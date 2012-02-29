package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.Session.getSession;
import static de.diddiz.LogBlock.config.Config.defaultDist;
import static de.diddiz.LogBlock.config.Config.defaultTime;
import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.isLogged;
import static de.diddiz.util.BukkitUtils.friendlyWorldname;
import static de.diddiz.util.BukkitUtils.getBlockEquivalents;
import static de.diddiz.util.MaterialName.materialName;
import static de.diddiz.util.Utils.isInt;
import static de.diddiz.util.Utils.join;
import static de.diddiz.util.Utils.listing;
import static de.diddiz.util.Utils.parseTimeSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;

public class QueryParams implements Cloneable
{
	private static final Set<Integer> keywords = new HashSet<Integer>(Arrays.asList("player".hashCode(), "area".hashCode(), "selection".hashCode(), "sel".hashCode(), "block".hashCode(), "type".hashCode(), "sum".hashCode(), "destroyed".hashCode(), "created".hashCode(), "chestaccess".hashCode(), "all".hashCode(), "time".hashCode(), "since".hashCode(), "before".hashCode(), "limit".hashCode(), "world".hashCode(), "asc".hashCode(), "desc".hashCode(), "last".hashCode(), "coords".hashCode(), "silent".hashCode(), "chat".hashCode(), "search".hashCode(), "match".hashCode(), "loc".hashCode(), "location".hashCode()));
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

	public QueryParams(LogBlock logblock) {
		this.logblock = logblock;
	}

	public QueryParams(LogBlock logblock, CommandSender sender, List<String> args) throws IllegalArgumentException {
		this.logblock = logblock;
		parseArgs(sender, args);
	}

	public static boolean isKeyWord(String param) {
		return keywords.contains(param.toLowerCase().hashCode());
	}

	public String getLimit() {
		return limit > 0 ? "LIMIT " + limit : "";
	}

	public String getQuery() {
		if (bct == BlockChangeType.CHAT) {
			String select = "SELECT ";
			if (needCount)
				select += "COUNT(*) AS count";
			else {
				if (needId)
					select += "id, ";
				if (needDate)
					select += "date, ";
				if (needPlayer)
					select += "playername, ";
				if (needMessage)
					select += "message, ";
				select = select.substring(0, select.length() - 2);
			}
			String from = "FROM `lb-chat` ";

			if (needPlayer || players.size() > 0)
				from += "INNER JOIN `lb-players` USING (playerid) ";
			return select + " " + from + getWhere() + "ORDER BY date " + order + ", id " + order + " " + getLimit();
		}
		if (sum == SummarizationMode.NONE) {
			String select = "SELECT ";
			if (needCount)
				select += "COUNT(*) AS count";
			else {
				if (needId)
					select += "`" + getTable() + "`.id, ";
				if (needDate)
					select += "date, ";
				if (needType)
					select += "replaced, type, ";
				if (needData)
					select += "data, ";
				if (needPlayer)
					select += "playername, ";
				if (needCoords)
					select += "x, y, z, ";
				if (needSignText)
					select += "signtext, ";
				if (needChestAccess)
					select += "itemtype, itemamount, itemdata, ";
				select = select.substring(0, select.length() - 2);
			}
			String from = "FROM `" + getTable() + "` ";
			if (needPlayer || players.size() > 0)
				from += "INNER JOIN `lb-players` USING (playerid) ";
			if (needSignText)
				from += "LEFT JOIN `" + getTable() + "-sign` USING (id) ";
			if (needChestAccess)
				from += "LEFT JOIN `" + getTable() + "-chest` USING (id) ";
			return select + " " + from + getWhere() + "ORDER BY date " + order + ", id " + order + " " + getLimit();
		} else if (sum == SummarizationMode.TYPES)
			return "SELECT type, SUM(created) AS created, SUM(destroyed) AS destroyed FROM ((SELECT type, count(*) AS created, 0 AS destroyed FROM `" + getTable() + "` INNER JOIN `lb-players` USING (playerid) " + getWhere(BlockChangeType.CREATED) + "GROUP BY type) UNION (SELECT replaced AS type, 0 AS created, count(*) AS destroyed FROM `" + getTable() + "` INNER JOIN `lb-players` USING (playerid) " + getWhere(BlockChangeType.DESTROYED) + "GROUP BY replaced)) AS t GROUP BY type ORDER BY SUM(created) + SUM(destroyed) " + order + " " + getLimit();
		else
			return "SELECT playername, SUM(created) AS created, SUM(destroyed) AS destroyed FROM ((SELECT playerid, count(*) AS created, 0 AS destroyed FROM `" + getTable() + "` " + getWhere(BlockChangeType.CREATED) + "GROUP BY playerid) UNION (SELECT playerid, 0 AS created, count(*) AS destroyed FROM `" + getTable() + "` " + getWhere(BlockChangeType.DESTROYED) + "GROUP BY playerid)) AS t INNER JOIN `lb-players` USING (playerid) GROUP BY playerid ORDER BY SUM(created) + SUM(destroyed) " + order + " " + getLimit();
	}

	public String getTable() {
		return getWorldConfig(world).table;
	}

	public String getTitle() {
		final StringBuilder title = new StringBuilder();
		if (bct == BlockChangeType.CHESTACCESS)
			title.append("chest accesses ");
		else if (bct == BlockChangeType.CHAT)
			title.append("chat messages ");
		else {
			if (!types.isEmpty()) {
				final String[] blocknames = new String[types.size()];
				for (int i = 0; i < types.size(); i++)
					blocknames[i] = materialName(types.get(i));
				title.append(listing(blocknames, ", ", " and ") + " ");
			} else
				title.append("block ");
			if (bct == BlockChangeType.CREATED)
				title.append("creations ");
			else if (bct == BlockChangeType.DESTROYED)
				title.append("destructions ");
			else
				title.append("changes ");
		}
		if (players.size() > 10)
			title.append((excludePlayersMode ? "without" : "from") + " many players ");
		else if (!players.isEmpty())
			title.append((excludePlayersMode ? "without" : "from") + " player" + (players.size() != 1 ? "s" : "") + " " + listing(players.toArray(new String[players.size()]), ", ", " and ") + " ");
		if (match != null && match.length() > 0)
			title.append("matching '" + match + "' ");
		if (before > 0 && since > 0)
			title.append("between " + since + " and " + before + " minutes ago ");
		else if (since > 0)
			title.append("in the last " + since + " minutes ");
		else if (before > 0)
			title.append("more than " + before * -1 + " minutes ago ");
		if (loc != null) {
			if (radius > 0)
				title.append("within " + radius + " blocks of " + (prepareToolQuery ? "clicked block" : "you") + " ");
			else if (radius == 0)
				title.append("at " + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ() + " ");
		} else if (sel != null)
			title.append(prepareToolQuery ? "at double chest " : "inside selection ");
		else if (prepareToolQuery)
			if (radius > 0)
				title.append("within " + radius + " blocks of clicked block ");
			else if (radius == 0)
				title.append("at clicked block ");
		if (world != null && !(sel != null && prepareToolQuery))
			title.append("in " + friendlyWorldname(world.getName()) + " ");
		if (sum != SummarizationMode.NONE)
			title.append("summed up by " + (sum == SummarizationMode.TYPES ? "blocks" : "players") + " ");
		title.deleteCharAt(title.length() - 1);
		title.setCharAt(0, String.valueOf(title.charAt(0)).toUpperCase().toCharArray()[0]);
		return title.toString();
	}

	public String getWhere() {
		return getWhere(bct);
	}

	public String getWhere(BlockChangeType blockChangeType) {
		final StringBuilder where = new StringBuilder("WHERE ");
		if (blockChangeType == BlockChangeType.CHAT) {
			if (match != null && match.length() > 0) {
				final boolean unlike = match.startsWith("-");
				if (match.length() > 3 && !unlike || match.length() > 4)
					where.append("MATCH (message) AGAINST ('" + match + "' IN BOOLEAN MODE) AND ");
				else
					where.append("message " + (unlike ? "NOT " : "") + "LIKE '%" + (unlike ? match.substring(1) : match) + "%' AND ");
			}
		} else {
			switch (blockChangeType) {
				case ALL:
					if (!types.isEmpty()) {
						where.append('(');
						for (final int type : types)
							where.append("type = " + type + " OR replaced = " + type + " OR ");
						where.delete(where.length() - 4, where.length() - 1);
						where.append(") AND ");
					}
					break;
				case BOTH:
					if (!types.isEmpty()) {
						where.append('(');
						for (final int type : types)
							where.append("type = " + type + " OR replaced = " + type + " OR ");
						where.delete(where.length() - 4, where.length());
						where.append(") AND ");
					}
					where.append("type != replaced AND ");
					break;
				case CREATED:
					if (!types.isEmpty()) {
						where.append('(');
						for (final int type : types)
							where.append("type = " + type + " OR ");
						where.delete(where.length() - 4, where.length());
						where.append(") AND ");
					} else
						where.append("type != 0 AND ");
					where.append("type != replaced AND ");
					break;
				case DESTROYED:
					if (!types.isEmpty()) {
						where.append('(');
						for (final int type : types)
							where.append("replaced = " + type + " OR ");
						where.delete(where.length() - 4, where.length());
						where.append(") AND ");
					} else
						where.append("replaced != 0 AND ");
					where.append("type != replaced AND ");
					break;
				case CHESTACCESS:
					where.append("(type = 23 OR type = 54 OR type = 61 OR type = 62) AND type = replaced AND ");
					if (!types.isEmpty()) {
						where.append('(');
						for (final int type : types)
							where.append("itemtype = " + type + " OR ");
						where.delete(where.length() - 4, where.length());
						where.append(") AND ");
					}
					break;
			}
			if (loc != null) {
				if (radius == 0)
					where.append("x = '" + loc.getBlockX() + "' AND y = '" + loc.getBlockY() + "' AND z = '" + loc.getBlockZ() + "' AND ");
				else if (radius > 0)
					where.append("x > '" + (loc.getBlockX() - radius) + "' AND x < '" + (loc.getBlockX() + radius) + "' AND z > '" + (loc.getBlockZ() - radius) + "' AND z < '" + (loc.getBlockZ() + radius) + "' AND ");
			} else if (sel != null)
				where.append("x >= '" + sel.getMinimumPoint().getBlockX() + "' AND x <= '" + sel.getMaximumPoint().getBlockX() + "' AND y >= '" + sel.getMinimumPoint().getBlockY() + "' AND y <= '" + sel.getMaximumPoint().getBlockY() + "' AND z >= '" + sel.getMinimumPoint().getBlockZ() + "' AND z <= '" + sel.getMaximumPoint().getBlockZ() + "' AND ");
		}
		if (!players.isEmpty() && sum != SummarizationMode.PLAYERS)
			if (!excludePlayersMode) {
				where.append('(');
				for (final String playerName : players)
					where.append("playername = '" + playerName + "' OR ");
				where.delete(where.length() - 4, where.length());
				where.append(") AND ");
			} else
				for (final String playerName : players)
					where.append("playername != '" + playerName + "' AND ");
		if (since > 0)
			where.append("date > date_sub(now(), INTERVAL " + since + " MINUTE) AND ");
		if (before > 0)
			where.append("date < date_sub(now(), INTERVAL " + before + " MINUTE) AND ");
		if (where.length() > 6)
			where.delete(where.length() - 4, where.length());
		else
			where.delete(0, where.length());
		return where.toString();
	}

	public void parseArgs(CommandSender sender, List<String> args) throws IllegalArgumentException {
		if (args == null || args.size() == 0)
			throw new IllegalArgumentException("No parameters specified.");
		final Player player = sender instanceof Player ? (Player)sender : null;
		final Session session = prepareToolQuery ? null : getSession(sender);
		if (player != null && world == null)
			world = player.getWorld();
		for (int i = 0; i < args.size(); i++) {
			final String param = args.get(i).toLowerCase();
			final String[] values = getValues(args, i + 1);
			if (param.equals("last")) {
				if (session.lastQuery == null)
					throw new IllegalArgumentException("This is your first command, you can't use last.");
				merge(session.lastQuery);
			} else if (param.equals("player")) {
				if (values.length < 1)
					throw new IllegalArgumentException("No or wrong count of arguments for '" + param + "'");
				for (final String playerName : values)
					if (playerName.length() > 0) {
						if (playerName.contains("!"))
							excludePlayersMode = true;
						if (playerName.contains("\""))
							players.add(playerName.replaceAll("[^a-zA-Z0-9_]", ""));
						else {
							final List<Player> matches = logblock.getServer().matchPlayer(playerName);
							if (matches.size() > 1)
								throw new IllegalArgumentException("Ambiguous playername '" + param + "'");
							players.add(matches.size() == 1 ? matches.get(0).getName() : playerName.replaceAll("[^a-zA-Z0-9_]", ""));
						}
					}
			} else if (param.equals("block") || param.equals("type")) {
				if (values.length < 1)
					throw new IllegalArgumentException("No or wrong count of arguments for '" + param + "'");
				for (final String blockName : values) {
					final Material mat = Material.matchMaterial(blockName);
					if (mat == null)
						throw new IllegalArgumentException("No material matching: '" + blockName + "'");
					types.add(mat.getId());
				}
			} else if (param.equals("area")) {
				if (player == null && !prepareToolQuery)
					throw new IllegalArgumentException("You have to ba a player to use area");
				if (values.length == 0) {
					radius = defaultDist;
					if (!prepareToolQuery)
						loc = player.getLocation();
				} else {
					if (!isInt(values[0]))
						throw new IllegalArgumentException("Not a number: '" + values[0] + "'");
					radius = Integer.parseInt(values[0]);
					if (!prepareToolQuery)
						loc = player.getLocation();
				}
			} else if (param.equals("selection") || param.equals("sel")) {
				if (player == null)
					throw new IllegalArgumentException("You have to ba a player to use selection");
				final Plugin we = player.getServer().getPluginManager().getPlugin("WorldEdit");
				if (we == null)
					throw new IllegalArgumentException("WorldEdit plugin not found");
				final Selection selection = ((WorldEditPlugin)we).getSelection(player);
				if (selection == null)
					throw new IllegalArgumentException("No selection defined");
				if (!(selection instanceof CuboidSelection))
					throw new IllegalArgumentException("You have to define a cuboid selection");
				setSelection(selection);
			} else if (param.equals("time") || param.equals("since")) {
				since = values.length > 0 ? parseTimeSpec(values) : defaultTime;
				if (since == -1)
					throw new IllegalArgumentException("Failed to parse time spec for '" + param + "'");
			} else if (param.equals("before")) {
				before = values.length > 0 ? parseTimeSpec(values) : defaultTime;
				if (before == -1)
					throw new IllegalArgumentException("Faile to parse time spec for '" + param + "'");
			} else if (param.equals("sum")) {
				if (values.length != 1)
					throw new IllegalArgumentException("No or wrong count of arguments for '" + param + "'");
				if (values[0].startsWith("p"))
					sum = SummarizationMode.PLAYERS;
				else if (values[0].startsWith("b"))
					sum = SummarizationMode.TYPES;
				else if (values[0].startsWith("n"))
					sum = SummarizationMode.NONE;
				else
					throw new IllegalArgumentException("Wrong summarization mode");
			} else if (param.equals("created"))
				bct = BlockChangeType.CREATED;
			else if (param.equals("destroyed"))
				bct = BlockChangeType.DESTROYED;
			else if (param.equals("chestaccess"))
				bct = BlockChangeType.CHESTACCESS;
			else if (param.equals("chat"))
				bct = BlockChangeType.CHAT;
			else if (param.equals("all"))
				bct = BlockChangeType.ALL;
			else if (param.equals("limit")) {
				if (values.length != 1)
					throw new IllegalArgumentException("Wrong count of arguments for '" + param + "'");
				if (!isInt(values[0]))
					throw new IllegalArgumentException("Not a number: '" + values[0] + "'");
				limit = Integer.parseInt(values[0]);
			} else if (param.equals("world")) {
				if (values.length != 1)
					throw new IllegalArgumentException("Wrong count of arguments for '" + param + "'");
				final World w = sender.getServer().getWorld(values[0].replace("\"", ""));
				if (w == null)
					throw new IllegalArgumentException("There is no world called '" + values[0] + "'");
				world = w;
			} else if (param.equals("asc"))
				order = Order.ASC;
			else if (param.equals("desc"))
				order = Order.DESC;
			else if (param.equals("coords"))
				needCoords = true;
			else if (param.equals("silent"))
				silent = true;
			else if (param.equals("search") || param.equals("match")) {
				if (values.length == 0)
					throw new IllegalArgumentException("No arguments for '" + param + "'");
				match = join(values, " ").replace("\\", "\\\\").replace("'", "\\'");
			} else if (param.equals("loc") || param.equals("location")) {
				final String[] vectors = values.length == 1 ? values[0].split(":") : values;
				if (vectors.length != 3)
					throw new IllegalArgumentException("Wrong count arguments for '" + param + "'");
				for (final String vec : vectors)
					if (!isInt(vec))
						throw new IllegalArgumentException("Not a number: '" + vec + "'");
				loc = new Location(null, Integer.valueOf(vectors[0]), Integer.valueOf(vectors[1]), Integer.valueOf(vectors[2]));
				radius = 0;
			} else
				throw new IllegalArgumentException("Not a valid argument: '" + param + "'");
			i += values.length;
		}
		if (types.size() > 0)
			for (final Set<Integer> equivalent : getBlockEquivalents()) {
				boolean found = false;
				for (final Integer type : types)
					if (equivalent.contains(type)) {
						found = true;
						break;
					}
				if (found)
					for (final Integer type : equivalent)
						if (!types.contains(type))
							types.add(type);
			}
		if (!prepareToolQuery && bct != BlockChangeType.CHAT) {
			if (world == null)
				throw new IllegalArgumentException("No world specified");
			if (!isLogged(world))
				throw new IllegalArgumentException("This world ('" + world.getName() + "') isn't logged");
		}
		if (session != null)
			session.lastQuery = clone();
	}

	public void setLocation(Location loc) {
		this.loc = loc;
		world = loc.getWorld();
	}

	public void setSelection(Selection sel) {
		this.sel = sel;
		world = sel.getWorld();
	}

	public void setPlayer(String playerName) {
		players.clear();
		players.add(playerName);
	}

	@Override
	protected QueryParams clone() {
		try {
			final QueryParams params = (QueryParams)super.clone();
			params.players = new ArrayList<String>(players);
			params.types = new ArrayList<Integer>(types);
			return params;
		} catch (final CloneNotSupportedException ex) {}
		return null;
	}

	private static String[] getValues(List<String> args, int offset) {
		int i;
		for (i = offset; i < args.size(); i++)
			if (isKeyWord(args.get(i)))
				break;
		if (i == offset)
			return new String[0];
		final String[] values = new String[i - offset];
		for (int j = offset; j < i; j++)
			values[j - offset] = args.get(j);
		return values;
	}

	public void merge(QueryParams p) {
		players = p.players;
		excludePlayersMode = p.excludePlayersMode;
		types = p.types;
		loc = p.loc;
		radius = p.radius;
		sel = p.sel;
		if (p.since != 0 || since != defaultTime)
			since = p.since;
		before = p.before;
		sum = p.sum;
		bct = p.bct;
		limit = p.limit;
		world = p.world;
		order = p.order;
		match = p.match;
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
}
