package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.Session.getSession;
import static de.diddiz.LogBlock.config.Config.*;
import static de.diddiz.util.BukkitUtils.friendlyWorldname;
import static de.diddiz.util.BukkitUtils.getBlockEquivalents;
import static de.diddiz.util.MaterialName.materialName;
import static de.diddiz.util.Utils.*;

import java.util.*;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.*;

public class QueryParams implements Cloneable {
    private static final Set<Integer> keywords = new HashSet<Integer>(Arrays.asList("player".hashCode(), "area".hashCode(), "selection".hashCode(), "sel".hashCode(), "block".hashCode(), "type".hashCode(), "sum".hashCode(),
            "destroyed".hashCode(), "created".hashCode(), "chestaccess".hashCode(), "all".hashCode(), "time".hashCode(), "since".hashCode(), "before".hashCode(), "limit".hashCode(), "world".hashCode(), "asc".hashCode(),
            "desc".hashCode(), "last".hashCode(), "coords".hashCode(), "silent".hashCode(), "chat".hashCode(), "search".hashCode(), "match".hashCode(), "loc".hashCode(), "location".hashCode()));

    public BlockChangeType bct = BlockChangeType.BOTH;

    public int limit = -1, before = 0, since = 0, radius = -1;

    public Location loc = null;

    public Order order = Order.DESC;

    public List<String> players = new ArrayList<String>();

    public boolean excludePlayersMode = false, prepareToolQuery = false, silent = false;
    public Selection sel = null;
    public SummarizationMode sum = SummarizationMode.NONE;
    public List<Integer> types = new ArrayList<Integer>();
    public World world = null;
    public String match = null;
    public boolean needCount = false, needId = false, needDate = false, needType = false, needData = false, needPlayer = false, needCoords = false, needSignText = false, needChestAccess = false, needMessage = false;
    private final LogBlock logblock;

    public QueryParams(final LogBlock logblock) {
        this.logblock = logblock;
    }

    public QueryParams(final LogBlock logblock, final CommandSender sender, final List<String> args) throws IllegalArgumentException {
        this.logblock = logblock;
        parseArgs(sender, args);
    }

    public static enum BlockChangeType {
        ALL, BOTH, CHESTACCESS, CREATED, DESTROYED, CHAT
    }

    public static enum Order {
        ASC, DESC
    }

    public static enum SummarizationMode {
        NONE, PLAYERS, TYPES
    }

    public static boolean isKeyWord(final String param) {
        return keywords.contains(param.toLowerCase().hashCode());
    }

    private static String[] getValues(final List<String> args, final int offset) {
        int i;
        for (i = offset; i < args.size(); i++)
            if (isKeyWord(args.get(i))) break;
        if (i == offset) return new String[0];
        final String[] values = new String[i - offset];
        for (int j = offset; j < i; j++)
            values[j - offset] = args.get(j);
        return values;
    }

    public String getLimit() {
        return this.limit > 0 ? "LIMIT " + this.limit : "";
    }

    public String getQuery() {
        if (this.bct == BlockChangeType.CHAT) {
            String select = "SELECT ";
            if (this.needCount) select += "COUNT(*) AS count";
            else {
                if (this.needId) select += "id, ";
                if (this.needDate) select += "date, ";
                if (this.needPlayer) select += "playername, ";
                if (this.needMessage) select += "message, ";
                select = select.substring(0, select.length() - 2);
            }
            String from = "FROM `lb-chat` ";

            if (this.needPlayer || this.players.size() > 0) from += "INNER JOIN `lb-players` USING (playerid) ";
            return select + " " + from + getWhere() + "ORDER BY date " + this.order + ", id " + this.order + " " + getLimit();
        }
        if (this.sum == SummarizationMode.NONE) {
            String select = "SELECT ";
            if (this.needCount) select += "COUNT(*) AS count";
            else {
                if (this.needId) select += "`" + getTable() + "`.id, ";
                if (this.needDate) select += "date, ";
                if (this.needType) select += "replaced, type, ";
                if (this.needData) select += "data, ";
                if (this.needPlayer) select += "playername, ";
                if (this.needCoords) select += "x, y, z, ";
                if (this.needSignText) select += "signtext, ";
                if (this.needChestAccess) select += "itemtype, itemamount, itemdata, ";
                select = select.substring(0, select.length() - 2);
            }
            String from = "FROM `" + getTable() + "` ";
            if (this.needPlayer || this.players.size() > 0) from += "INNER JOIN `lb-players` USING (playerid) ";
            if (this.needSignText) from += "LEFT JOIN `" + getTable() + "-sign` USING (id) ";
            if (this.needChestAccess) from += "LEFT JOIN `" + getTable() + "-chest` USING (id) ";
            return select + " " + from + getWhere() + "ORDER BY date " + this.order + ", id " + this.order + " " + getLimit();
        } else if (this.sum == SummarizationMode.TYPES) return "SELECT type, SUM(created) AS created, SUM(destroyed) AS destroyed FROM ((SELECT type, count(*) AS created, 0 AS destroyed FROM `" + getTable()
                + "` INNER JOIN `lb-players` USING (playerid) " + getWhere(BlockChangeType.CREATED) + "GROUP BY type) UNION (SELECT replaced AS type, 0 AS created, count(*) AS destroyed FROM `" + getTable()
                + "` INNER JOIN `lb-players` USING (playerid) " + getWhere(BlockChangeType.DESTROYED) + "GROUP BY replaced)) AS t GROUP BY type ORDER BY SUM(created) + SUM(destroyed) " + this.order + " " + getLimit();
        else return "SELECT playername, SUM(created) AS created, SUM(destroyed) AS destroyed FROM ((SELECT playerid, count(*) AS created, 0 AS destroyed FROM `" + getTable() + "` " + getWhere(BlockChangeType.CREATED)
                + "GROUP BY playerid) UNION (SELECT playerid, 0 AS created, count(*) AS destroyed FROM `" + getTable() + "` " + getWhere(BlockChangeType.DESTROYED)
                + "GROUP BY playerid)) AS t INNER JOIN `lb-players` USING (playerid) GROUP BY playerid ORDER BY SUM(created) + SUM(destroyed) " + this.order + " " + getLimit();
    }

    public String getTable() {
        return getWorldConfig(this.world).table;
    }

    public String getTitle() {
        final StringBuilder title = new StringBuilder();
        if (this.bct == BlockChangeType.CHESTACCESS) title.append("chest accesses ");
        else if (this.bct == BlockChangeType.CHAT) title.append("chat messages ");
        else {
            if (!this.types.isEmpty()) {
                final String[] blocknames = new String[this.types.size()];
                for (int i = 0; i < this.types.size(); i++)
                    blocknames[i] = materialName(this.types.get(i));
                title.append(listing(blocknames, ", ", " and ") + " ");
            } else title.append("block ");
            if (this.bct == BlockChangeType.CREATED) title.append("creations ");
            else if (this.bct == BlockChangeType.DESTROYED) title.append("destructions ");
            else title.append("changes ");
        }
        if (this.players.size() > 10) title.append((this.excludePlayersMode ? "without" : "from") + " many players ");
        else if (!this.players.isEmpty())
            title.append((this.excludePlayersMode ? "without" : "from") + " player" + (this.players.size() != 1 ? "s" : "") + " " + listing(this.players.toArray(new String[this.players.size()]), ", ", " and ") + " ");
        if (this.match != null && this.match.length() > 0) title.append("matching '" + this.match + "' ");
        if (this.before > 0 && this.since > 0) title.append("between " + this.since + " and " + this.before + " minutes ago ");
        else if (this.since > 0) title.append("in the last " + this.since + " minutes ");
        else if (this.before > 0) title.append("more than " + this.before * -1 + " minutes ago ");
        if (this.loc != null) {
            if (this.radius > 0) title.append("within " + this.radius + " blocks of " + (this.prepareToolQuery ? "clicked block" : "you") + " ");
            else if (this.radius == 0) title.append("at " + this.loc.getBlockX() + ":" + this.loc.getBlockY() + ":" + this.loc.getBlockZ() + " ");
        } else if (this.sel != null) title.append(this.prepareToolQuery ? "at double chest " : "inside selection ");
        else if (this.prepareToolQuery) if (this.radius > 0) title.append("within " + this.radius + " blocks of clicked block ");
        else if (this.radius == 0) title.append("at clicked block ");
        if (this.world != null && !(this.sel != null && this.prepareToolQuery)) title.append("in " + friendlyWorldname(this.world.getName()) + " ");
        if (this.sum != SummarizationMode.NONE) title.append("summed up by " + (this.sum == SummarizationMode.TYPES ? "blocks" : "players") + " ");
        title.deleteCharAt(title.length() - 1);
        title.setCharAt(0, String.valueOf(title.charAt(0)).toUpperCase().toCharArray()[0]);
        return title.toString();
    }

    public String getWhere() {
        return getWhere(this.bct);
    }

    public String getWhere(final BlockChangeType blockChangeType) {
        final StringBuilder where = new StringBuilder("WHERE ");
        if (blockChangeType == BlockChangeType.CHAT) {
            if (this.match != null && this.match.length() > 0) {
                final boolean unlike = this.match.startsWith("-");
                if (this.match.length() > 3 && !unlike || this.match.length() > 4) where.append("MATCH (message) AGAINST ('" + this.match + "' IN BOOLEAN MODE) AND ");
                else where.append("message " + (unlike ? "NOT " : "") + "LIKE '%" + (unlike ? this.match.substring(1) : this.match) + "%' AND ");
            }
        } else {
            switch (blockChangeType) {
            case ALL:
                if (!this.types.isEmpty()) {
                    where.append('(');
                    for (final int type : this.types)
                        where.append("type = " + type + " OR replaced = " + type + " OR ");
                    where.delete(where.length() - 4, where.length() - 1);
                    where.append(") AND ");
                }
                break;
            case BOTH:
                if (!this.types.isEmpty()) {
                    where.append('(');
                    for (final int type : this.types)
                        where.append("type = " + type + " OR replaced = " + type + " OR ");
                    where.delete(where.length() - 4, where.length());
                    where.append(") AND ");
                }
                where.append("type != replaced AND ");
                break;
            case CREATED:
                if (!this.types.isEmpty()) {
                    where.append('(');
                    for (final int type : this.types)
                        where.append("type = " + type + " OR ");
                    where.delete(where.length() - 4, where.length());
                    where.append(") AND ");
                } else where.append("type != 0 AND ");
                where.append("type != replaced AND ");
                break;
            case DESTROYED:
                if (!this.types.isEmpty()) {
                    where.append('(');
                    for (final int type : this.types)
                        where.append("replaced = " + type + " OR ");
                    where.delete(where.length() - 4, where.length());
                    where.append(") AND ");
                } else where.append("replaced != 0 AND ");
                where.append("type != replaced AND ");
                break;
            case CHESTACCESS:
                where.append("(type = 23 OR type = 54 OR type = 61 OR type = 62) AND type = replaced AND ");
                if (!this.types.isEmpty()) {
                    where.append('(');
                    for (final int type : this.types)
                        where.append("itemtype = " + type + " OR ");
                    where.delete(where.length() - 4, where.length());
                    where.append(") AND ");
                }
                break;
            case CHAT:
                break;
            }
            if (this.loc != null) {
                if (this.radius == 0) where.append("x = '" + this.loc.getBlockX() + "' AND y = '" + this.loc.getBlockY() + "' AND z = '" + this.loc.getBlockZ() + "' AND ");
                else if (this.radius > 0)
                    where.append("x > '" + (this.loc.getBlockX() - this.radius) + "' AND x < '" + (this.loc.getBlockX() + this.radius) + "' AND z > '" + (this.loc.getBlockZ() - this.radius) + "' AND z < '"
                            + (this.loc.getBlockZ() + this.radius) + "' AND ");
            } else if (this.sel != null)
                where.append("x >= '" + this.sel.getMinimumPoint().getBlockX() + "' AND x <= '" + this.sel.getMaximumPoint().getBlockX() + "' AND y >= '" + this.sel.getMinimumPoint().getBlockY() + "' AND y <= '"
                        + this.sel.getMaximumPoint().getBlockY() + "' AND z >= '" + this.sel.getMinimumPoint().getBlockZ() + "' AND z <= '" + this.sel.getMaximumPoint().getBlockZ() + "' AND ");
        }
        if (!this.players.isEmpty() && this.sum != SummarizationMode.PLAYERS) if (!this.excludePlayersMode) {
            where.append('(');
            for (final String playerName : this.players)
                where.append("playername = '" + playerName + "' OR ");
            where.delete(where.length() - 4, where.length());
            where.append(") AND ");
        } else for (final String playerName : this.players)
            where.append("playername != '" + playerName + "' AND ");
        if (this.since > 0) where.append("date > date_sub(now(), INTERVAL " + this.since + " MINUTE) AND ");
        if (this.before > 0) where.append("date < date_sub(now(), INTERVAL " + this.before + " MINUTE) AND ");
        if (where.length() > 6) where.delete(where.length() - 4, where.length());
        else where.delete(0, where.length());
        return where.toString();
    }

    public void merge(final QueryParams p) {
        this.players = p.players;
        this.excludePlayersMode = p.excludePlayersMode;
        this.types = p.types;
        this.loc = p.loc;
        this.radius = p.radius;
        this.sel = p.sel;
        if (p.since != 0 || this.since != defaultTime) this.since = p.since;
        this.before = p.before;
        this.sum = p.sum;
        this.bct = p.bct;
        this.limit = p.limit;
        this.world = p.world;
        this.order = p.order;
        this.match = p.match;
    }

    public void parseArgs(final CommandSender sender, final List<String> args) throws IllegalArgumentException {
        if (args == null || args.size() == 0) throw new IllegalArgumentException("No parameters specified.");
        final Player player = sender instanceof Player ? (Player) sender : null;
        final Session session = this.prepareToolQuery ? null : getSession(sender);
        if (player != null && this.world == null) this.world = player.getWorld();
        for (int i = 0; i < args.size(); i++) {
            final String param = args.get(i).toLowerCase();
            final String[] values = getValues(args, i + 1);
            if (param.equals("last")) {
                if (session.lastQuery == null) throw new IllegalArgumentException("This is your first command, you can't use last.");
                merge(session.lastQuery);
            } else if (param.equals("player")) {
                if (values.length < 1) throw new IllegalArgumentException("No or wrong count of arguments for '" + param + "'");
                for (final String playerName : values)
                    if (playerName.length() > 0) {
                        if (playerName.contains("!")) this.excludePlayersMode = true;
                        if (playerName.contains("\"")) this.players.add(playerName.replaceAll("[^a-zA-Z0-9_]", ""));
                        else {
                            final List<Player> matches = this.logblock.getServer().matchPlayer(playerName);
                            if (matches.size() > 1) throw new IllegalArgumentException("Ambiguous playername '" + param + "'");
                            this.players.add(matches.size() == 1 ? matches.get(0).getName() : playerName.replaceAll("[^a-zA-Z0-9_]", ""));
                        }
                    }
            } else if (param.equals("block") || param.equals("type")) {
                if (values.length < 1) throw new IllegalArgumentException("No or wrong count of arguments for '" + param + "'");
                for (final String blockName : values) {
                    final Material mat = Material.matchMaterial(blockName);
                    if (mat == null) throw new IllegalArgumentException("No material matching: '" + blockName + "'");
                    this.types.add(mat.getId());
                }
            } else if (param.equals("area")) {
                if (player == null && !this.prepareToolQuery) throw new IllegalArgumentException("You have to ba a player to use area");
                if (values.length == 0) {
                    this.radius = defaultDist;
                    if (!this.prepareToolQuery) this.loc = player.getLocation();
                } else {
                    if (!isInt(values[0])) throw new IllegalArgumentException("Not a number: '" + values[0] + "'");
                    this.radius = Integer.parseInt(values[0]);
                    if (!this.prepareToolQuery) this.loc = player.getLocation();
                }
            } else if (param.equals("selection") || param.equals("sel")) {
                if (player == null) throw new IllegalArgumentException("You have to ba a player to use selection");
                final Plugin we = player.getServer().getPluginManager().getPlugin("WorldEdit");
                if (we == null) throw new IllegalArgumentException("WorldEdit plugin not found");
                final Selection selection = ((WorldEditPlugin) we).getSelection(player);
                if (selection == null) throw new IllegalArgumentException("No selection defined");
                if (!(selection instanceof CuboidSelection)) throw new IllegalArgumentException("You have to define a cuboid selection");
                setSelection(selection);
            } else if (param.equals("time") || param.equals("since")) {
                this.since = values.length > 0 ? parseTimeSpec(values) : defaultTime;
                if (this.since == -1) throw new IllegalArgumentException("Failed to parse time spec for '" + param + "'");
            } else if (param.equals("before")) {
                this.before = values.length > 0 ? parseTimeSpec(values) : defaultTime;
                if (this.before == -1) throw new IllegalArgumentException("Faile to parse time spec for '" + param + "'");
            } else if (param.equals("sum")) {
                if (values.length != 1) throw new IllegalArgumentException("No or wrong count of arguments for '" + param + "'");
                if (values[0].startsWith("p")) this.sum = SummarizationMode.PLAYERS;
                else if (values[0].startsWith("b")) this.sum = SummarizationMode.TYPES;
                else if (values[0].startsWith("n")) this.sum = SummarizationMode.NONE;
                else throw new IllegalArgumentException("Wrong summarization mode");
            } else if (param.equals("created")) this.bct = BlockChangeType.CREATED;
            else if (param.equals("destroyed")) this.bct = BlockChangeType.DESTROYED;
            else if (param.equals("chestaccess")) this.bct = BlockChangeType.CHESTACCESS;
            else if (param.equals("chat")) this.bct = BlockChangeType.CHAT;
            else if (param.equals("all")) this.bct = BlockChangeType.ALL;
            else if (param.equals("limit")) {
                if (values.length != 1) throw new IllegalArgumentException("Wrong count of arguments for '" + param + "'");
                if (!isInt(values[0])) throw new IllegalArgumentException("Not a number: '" + values[0] + "'");
                this.limit = Integer.parseInt(values[0]);
            } else if (param.equals("world")) {
                if (values.length != 1) throw new IllegalArgumentException("Wrong count of arguments for '" + param + "'");
                final World w;
                if (values[0].equals("\"world\"")) w = Bukkit.getServer().getWorld("world");
                else w = Bukkit.getServer().getWorld(values[0].replace("\"", "").replace("*", ""));
                if (w == null) throw new IllegalArgumentException("There is no world called '" + values[0].replace("\"", "").replace("*", "") + "'");
                this.world = w;
            } else if (param.equals("asc")) this.order = Order.ASC;
            else if (param.equals("desc")) this.order = Order.DESC;
            else if (param.equals("coords")) this.needCoords = true;
            else if (param.equals("silent")) this.silent = true;
            else if (param.equals("search") || param.equals("match")) {
                if (values.length == 0) throw new IllegalArgumentException("No arguments for '" + param + "'");
                this.match = join(values, " ").replace("\\", "\\\\").replace("'", "\\'");
            } else if (param.equals("loc") || param.equals("location")) {
                final String[] vectors = values.length == 1 ? values[0].split(":") : values;
                if (vectors.length != 3) throw new IllegalArgumentException("Wrong count arguments for '" + param + "'");
                for (final String vec : vectors)
                    if (!isInt(vec)) throw new IllegalArgumentException("Not a number: '" + vec + "'");
                this.loc = new Location(null, Integer.valueOf(vectors[0]), Integer.valueOf(vectors[1]), Integer.valueOf(vectors[2]));
                this.radius = 0;
            } else throw new IllegalArgumentException("Not a valid argument: '" + param + "'");
            i += values.length;
        }
        if (this.types.size() > 0) for (final Set<Integer> equivalent : getBlockEquivalents()) {
            boolean found = false;
            for (final Integer type : this.types)
                if (equivalent.contains(type)) {
                    found = true;
                    break;
                }
            if (found) for (final Integer type : equivalent)
                if (!this.types.contains(type)) this.types.add(type);
        }
        if (!this.prepareToolQuery && this.bct != BlockChangeType.CHAT) {
            if (this.world == null) throw new IllegalArgumentException("No world specified");
            if (!isLogged(this.world)) throw new IllegalArgumentException("This world ('" + this.world.getName() + "') isn't logged");
        }
        if (session != null) session.lastQuery = clone();
    }

    public void setLocation(final Location loc) {
        this.loc = loc;
        this.world = loc.getWorld();
    }

    public void setPlayer(final String playerName) {
        this.players.clear();
        this.players.add(playerName);
    }

    public void setSelection(final Selection sel) {
        this.sel = sel;
        this.world = sel.getWorld();
    }

    @Override
    protected QueryParams clone() {
        try {
            final QueryParams params = (QueryParams) super.clone();
            params.players = new ArrayList<String>(this.players);
            params.types = new ArrayList<Integer>(this.types);
            return params;
        } catch (final CloneNotSupportedException ex) {}
        return null;
    }
}
