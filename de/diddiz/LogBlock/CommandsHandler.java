package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.Session.getSession;
import static de.diddiz.LogBlock.config.Config.*;
import static de.diddiz.util.BukkitUtils.giveTool;
import static de.diddiz.util.BukkitUtils.saveSpawnHeight;
import static de.diddiz.util.Utils.isInt;
import static de.diddiz.util.Utils.listing;
import static org.bukkit.Bukkit.getLogger;
import static org.bukkit.Bukkit.getServer;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;

import de.diddiz.LogBlock.QueryParams.BlockChangeType;
import de.diddiz.LogBlock.QueryParams.Order;
import de.diddiz.LogBlock.QueryParams.SummarizationMode;
import de.diddiz.LogBlock.config.*;

public class CommandsHandler implements CommandExecutor {
    public abstract class AbstractCommand implements Runnable, Closeable {
        protected CommandSender sender;
        protected QueryParams params;
        protected Connection conn = null;
        protected Statement state = null;
        protected ResultSet rs = null;

        protected AbstractCommand(CommandSender sender, QueryParams params, boolean async) throws Exception {
            this.sender = sender;
            this.params = params;
            if (async) {
                if (CommandsHandler.this.scheduler.scheduleAsyncDelayedTask(logblock, this) == -1)
                    throw new Exception("Failed to schedule the command");
            } else run();
        }

        @Override
        public final void close() {
            try {
                if (this.conn != null) this.conn.close();
                if (this.state != null) this.state.close();
                if (this.rs != null) this.rs.close();
            } catch (final SQLException ex) {
                getLogger().log(Level.SEVERE, "[LogBlock CommandsHandler] SQL exception on close", ex);
            }
        }
    }

    public class CommandClearLog extends AbstractCommand {
        public CommandClearLog(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            try {
                this.conn = CommandsHandler.this.logblock.getConnection();
                this.state = this.conn.createStatement();
                if (this.conn == null) {
                    this.sender.sendMessage(ChatColor.RED + "MySQL connection lost");
                    return;
                }
                if (!checkRestrictions(this.sender, this.params)) return;
                final File dumpFolder = new File(CommandsHandler.this.logblock.getDataFolder(), "dump");
                if (!dumpFolder.exists()) dumpFolder.mkdirs();
                final String time = new SimpleDateFormat("yyMMddHHmmss").format(System.currentTimeMillis());
                int deleted;
                final String table = this.params.getTable();
                final String join = this.params.players.size() > 0 ? "INNER JOIN `lb-players` USING (playerid) "
                        : "";
                this.rs = this.state.executeQuery("SELECT count(*) FROM `" + table + "` " + join
                        + this.params.getWhere());
                this.rs.next();
                if ((deleted = this.rs.getInt(1)) > 0) {
                    if (!this.params.silent && askClearLogs && this.sender instanceof Player) {
                        this.sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + this.params.getTitle()
                                + ":");
                        this.sender.sendMessage(ChatColor.GREEN.toString() + deleted + " blocks found.");
                        if (!logblock.ask((Player) this.sender,
                                "Are you sure you want to continue?", "yes", "no").equals("yes")) {
                            this.sender.sendMessage(ChatColor.RED + "ClearLog aborted");
                            return;
                        }
                    }
                    if (dumpDeletedLog)
                        try {
                            this.state
                                    .execute("SELECT * FROM `"
                                            + table
                                            + "` "
                                            + join
                                            + this.params.getWhere()
                                            + "INTO OUTFILE '"
                                            + new File(dumpFolder, time + " " + table + " "
                                                    + this.params.getTitle().replace(":", ".") + ".csv")
                                                    .getAbsolutePath().replace("\\", "\\\\")
                                            + "' FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"'  LINES TERMINATED BY '\n'");
                        } catch (final SQLException ex) {
                            this.sender
                                    .sendMessage(ChatColor.RED
                                            + "Error while dumping log. Make sure your MySQL user has access to the LogBlock folder, or disable clearlog.dumpDeletedLog");
                            getLogger().log(Level.SEVERE,
                                    "[LogBlock ClearLog] Exception while dumping log: ", ex);
                            return;
                        }
                    this.state.execute("DELETE `" + table + "` FROM `" + table + "` " + join
                            + this.params.getWhere());
                    this.sender.sendMessage(ChatColor.GREEN + "Cleared out table " + table + ". Deleted "
                            + deleted + " entries.");
                }
                this.rs = this.state.executeQuery("SELECT COUNT(*) FROM `" + table + "-sign` LEFT JOIN `"
                        + table + "` USING (id) WHERE `" + table + "`.id IS NULL");
                this.rs.next();
                if ((deleted = this.rs.getInt(1)) > 0) {
                    if (dumpDeletedLog)
                        this.state
                                .execute("SELECT id, signtext FROM `"
                                        + table
                                        + "-sign` LEFT JOIN `"
                                        + table
                                        + "` USING (id) WHERE `"
                                        + table
                                        + "`.id IS NULL INTO OUTFILE '"
                                        + new File(dumpFolder, time + " " + table + "-sign "
                                                + this.params.getTitle() + ".csv").getAbsolutePath()
                                                .replace("\\", "\\\\")
                                        + "' FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"'  LINES TERMINATED BY '\n'");
                    this.state.execute("DELETE `" + table + "-sign` FROM `" + table + "-sign` LEFT JOIN `"
                            + table + "` USING (id) WHERE `" + table + "`.id IS NULL;");
                    this.sender.sendMessage(ChatColor.GREEN + "Cleared out table " + table
                            + "-sign. Deleted " + deleted + " entries.");
                }
                this.rs = this.state.executeQuery("SELECT COUNT(*) FROM `" + table + "-chest` LEFT JOIN `"
                        + table + "` USING (id) WHERE `" + table + "`.id IS NULL");
                this.rs.next();
                if ((deleted = this.rs.getInt(1)) > 0) {
                    if (dumpDeletedLog)
                        this.state
                                .execute("SELECT id, itemtype, itemamount, itemdata FROM `"
                                        + table
                                        + "-chest` LEFT JOIN `"
                                        + table
                                        + "` USING (id) WHERE `"
                                        + table
                                        + "`.id IS NULL INTO OUTFILE '"
                                        + new File(dumpFolder, time + " " + table + "-chest "
                                                + this.params.getTitle() + ".csv").getAbsolutePath()
                                                .replace("\\", "\\\\")
                                        + "' FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"'  LINES TERMINATED BY '\n'");
                    this.state.execute("DELETE `" + table + "-chest` FROM `" + table + "-chest` LEFT JOIN `"
                            + table + "` USING (id) WHERE `" + table + "`.id IS NULL;");
                    this.sender.sendMessage(ChatColor.GREEN + "Cleared out table " + table
                            + "-chest. Deleted " + deleted + " entries.");
                }
            } catch (final Exception ex) {
                this.sender.sendMessage(ChatColor.RED + "Exception, check error log");
                getLogger().log(Level.SEVERE, "[LogBlock ClearLog] Exception: ", ex);
            } finally {
                close();
            }
        }
    }

    public class CommandLookup extends AbstractCommand {
        public CommandLookup(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            try {
                if (this.params.bct == BlockChangeType.CHAT) {
                    this.params.needDate = true;
                    this.params.needPlayer = true;
                    this.params.needMessage = true;
                } else {
                    this.params.needDate = true;
                    this.params.needType = true;
                    this.params.needData = true;
                    this.params.needPlayer = true;
                    if (this.params.types.size() == 0 || this.params.types.contains(63)
                            || this.params.types.contains(68)) this.params.needSignText = true;
                    if (this.params.bct == BlockChangeType.CHESTACCESS || this.params.types.size() == 0
                            || this.params.types.contains(23) || this.params.types.contains(54)
                            || this.params.types.contains(61) || this.params.types.contains(62))
                        this.params.needChestAccess = true;
                }
                this.conn = CommandsHandler.this.logblock.getConnection();
                if (this.conn == null) {
                    this.sender.sendMessage(ChatColor.RED + "MySQL connection lost");
                    return;
                }
                this.state = this.conn.createStatement();
                this.rs = this.state.executeQuery(this.params.getQuery());
                this.sender.sendMessage(ChatColor.DARK_AQUA + this.params.getTitle() + ":");
                if (this.rs.next()) {
                    this.rs.beforeFirst();
                    final List<LookupCacheElement> blockchanges = new ArrayList<LookupCacheElement>();
                    final LookupCacheElementFactory factory = new LookupCacheElementFactory(this.params,
                            this.sender instanceof Player ? 2 / 3f : 1);
                    while (this.rs.next())
                        blockchanges.add(factory.getLookupCacheElement(this.rs));
                    getSession(this.sender).lookupCache = blockchanges
                            .toArray(new LookupCacheElement[blockchanges.size()]);
                    if (blockchanges.size() > linesPerPage)
                        this.sender.sendMessage(ChatColor.DARK_AQUA.toString()
                                + blockchanges.size()
                                + " changes found."
                                + (blockchanges.size() == linesLimit ? " Use 'limit -1' to see all changes."
                                        : ""));
                    if (this.params.sum != SummarizationMode.NONE)
                        this.sender.sendMessage(ChatColor.GOLD + "Created - Destroyed - "
                                + (this.params.sum == SummarizationMode.TYPES ? "Block" : "Player"));
                    showPage(this.sender, 1);
                } else {
                    this.sender.sendMessage(ChatColor.DARK_AQUA + "No results found.");
                    getSession(this.sender).lookupCache = null;
                }
            } catch (final Exception ex) {
                this.sender.sendMessage(ChatColor.RED + "Exception, check error log");
                getLogger().log(Level.SEVERE, "[LogBlock Lookup] " + this.params.getQuery() + ": ", ex);
            } finally {
                close();
            }
        }
    }

    public class CommandRedo extends AbstractCommand {
        public CommandRedo(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            try {
                this.params.needCoords = true;
                this.params.needType = true;
                this.params.needData = true;
                this.params.needSignText = true;
                this.params.needChestAccess = true;
                this.params.order = Order.ASC;
                this.params.sum = SummarizationMode.NONE;
                this.conn = CommandsHandler.this.logblock.getConnection();
                if (this.conn == null) {
                    this.sender.sendMessage(ChatColor.RED + "MySQL connection lost");
                    return;
                }
                this.state = this.conn.createStatement();
                if (!checkRestrictions(this.sender, this.params)) return;
                this.rs = this.state.executeQuery(this.params.getQuery());
                if (!this.params.silent)
                    this.sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + this.params.getTitle()
                            + ":");
                final WorldEditor editor = new WorldEditor(CommandsHandler.this.logblock, this.params.world);
                while (this.rs.next())
                    editor.queueEdit(this.rs.getInt("x"), this.rs.getInt("y"), this.rs.getInt("z"),
                            this.rs.getInt("type"), this.rs.getInt("replaced"), this.rs.getByte("data"),
                            this.rs.getString("signtext"), this.rs.getShort("itemtype"),
                            (short) -this.rs.getShort("itemamount"), this.rs.getByte("itemdata"));
                final int changes = editor.getSize();
                if (!this.params.silent)
                    this.sender.sendMessage(ChatColor.GREEN.toString() + changes + " blocks found.");
                if (changes == 0) {
                    if (!this.params.silent) this.sender.sendMessage(ChatColor.RED + "Redo aborted");
                    return;
                }
                if (!this.params.silent
                        && askRedos
                        && this.sender instanceof Player
                        && !logblock.ask((Player) this.sender,
                                "Are you sure you want to continue?", "yes", "no").equals("yes")) {
                    this.sender.sendMessage(ChatColor.RED + "Redo aborted");
                    return;
                }
                editor.start();
                this.sender.sendMessage(ChatColor.GREEN
                        + "Redo finished successfully ("
                        + editor.getElapsedTime()
                        + " ms, "
                        + editor.getSuccesses()
                        + "/"
                        + changes
                        + " blocks"
                        + (editor.getErrors() > 0 ? ", " + ChatColor.RED + editor.getErrors() + " errors"
                                + ChatColor.GREEN : "")
                        + (editor.getBlacklistCollisions() > 0 ? ", " + editor.getBlacklistCollisions()
                                + " blacklist collisions" : "") + ")");
            } catch (final Exception ex) {
                this.sender.sendMessage(ChatColor.RED + "Exception, check error log");
                getLogger().log(Level.SEVERE, "[LogBlock Redo] " + this.params.getQuery() + ": ", ex);
            } finally {
                close();
            }
        }
    }

    public class CommandRollback extends AbstractCommand {
        public CommandRollback(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            try {
                this.params.needCoords = true;
                this.params.needType = true;
                this.params.needData = true;
                this.params.needSignText = true;
                this.params.needChestAccess = true;
                this.params.order = Order.DESC;
                this.params.sum = SummarizationMode.NONE;
                this.conn = CommandsHandler.this.logblock.getConnection();
                if (this.conn == null) {
                    this.sender.sendMessage(ChatColor.RED + "MySQL connection lost");
                    return;
                }
                this.state = this.conn.createStatement();
                if (!checkRestrictions(this.sender, this.params)) return;
                if (CommandsHandler.this.logblock.getConsumer().getQueueSize() > 0)
                    new CommandSaveQueue(this.sender, null, false);
                if (!this.params.silent)
                    this.sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + this.params.getTitle()
                            + ":");
                this.rs = this.state.executeQuery(this.params.getQuery());
                final WorldEditor editor = new WorldEditor(CommandsHandler.this.logblock, this.params.world);
                while (this.rs.next())
                    editor.queueEdit(this.rs.getInt("x"), this.rs.getInt("y"), this.rs.getInt("z"),
                            this.rs.getInt("replaced"), this.rs.getInt("type"), this.rs.getByte("data"),
                            this.rs.getString("signtext"), this.rs.getShort("itemtype"),
                            this.rs.getShort("itemamount"), this.rs.getByte("itemdata"));
                final int changes = editor.getSize();
                if (!this.params.silent)
                    this.sender.sendMessage(ChatColor.GREEN.toString() + changes + " blocks found.");
                if (changes == 0) {
                    if (!this.params.silent) this.sender.sendMessage(ChatColor.RED + "Rollback aborted");
                    return;
                }
                if (!this.params.silent
                        && askRollbacks
                        && this.sender instanceof Player
                        && !logblock.ask((Player) this.sender, "Are you sure you want to continue?", "yes",
                                "no").equals("yes")) {
                    this.sender.sendMessage(ChatColor.RED + "Rollback aborted");
                    return;
                }
                editor.start();
                getSession(this.sender).lookupCache = editor.errors;
                this.sender.sendMessage(ChatColor.GREEN
                        + "Rollback finished successfully ("
                        + editor.getElapsedTime()
                        + " ms, "
                        + editor.getSuccesses()
                        + "/"
                        + changes
                        + " blocks"
                        + (editor.getErrors() > 0 ? ", " + ChatColor.RED + editor.getErrors() + " errors"
                                + ChatColor.GREEN : "")
                        + (editor.getBlacklistCollisions() > 0 ? ", " + editor.getBlacklistCollisions()
                                + " blacklist collisions" : "") + ")");
                if (!this.params.silent && askClearLogAfterRollback
                        && CommandsHandler.this.logblock.hasPermission(this.sender, "logblock.clearlog") && this.sender instanceof Player) {
                    Thread.sleep(1000);
                    if (logblock.ask((Player) this.sender,
                            "Do you want to delete the rollbacked log?", "yes", "no").equals("yes")) {
                        this.params.silent = true;
                        new CommandClearLog(this.sender, this.params, false);
                    } else this.sender.sendMessage(ChatColor.LIGHT_PURPLE + "Clearlog cancelled");
                }
            } catch (final Exception ex) {
                this.sender.sendMessage(ChatColor.RED + "Exception, check error log");
                getLogger().log(Level.SEVERE, "[LogBlock Rollback] " + this.params.getQuery() + ": ", ex);
            } finally {
                close();
            }
        }
    }

    public class CommandSaveQueue extends AbstractCommand {
        public CommandSaveQueue(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            final Consumer consumer = CommandsHandler.this.logblock.getConsumer();
            if (consumer.getQueueSize() > 0) {
                this.sender.sendMessage(ChatColor.DARK_AQUA + "Current queue size: "
                        + consumer.getQueueSize());
                int lastSize = -1, fails = 0;
                while (consumer.getQueueSize() > 0) {
                    fails = lastSize == consumer.getQueueSize() ? fails + 1 : 0;
                    if (fails > 10) {
                        this.sender.sendMessage(ChatColor.RED + "Unable to save queue");
                        return;
                    }
                    lastSize = consumer.getQueueSize();
                    consumer.run();
                }
                this.sender.sendMessage(ChatColor.GREEN + "Queue saved successfully");
            }
        }
    }

    public class CommandKillConnection extends AbstractCommand {
        public CommandKillConnection(CommandSender s, QueryParams params, boolean async) throws Exception {
            super(s, params, async);
        }

        @Override
        public void run() {
            final Connection conn = CommandsHandler.this.logblock.getConnection();
            if (conn == null) {
                this.sender.sendMessage(ChatColor.RED + "Connection is null!");
                return;
            }
            // conn.close();
            close();
            this.sender.sendMessage(ChatColor.GREEN + "Connection killed successfully.");
        }
    }

    public class CommandTeleport extends AbstractCommand {
        public CommandTeleport(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            try {
                this.params.needCoords = true;
                this.params.limit = 1;
                this.params.sum = SummarizationMode.NONE;
                this.conn = CommandsHandler.this.logblock.getConnection();
                if (this.conn == null) {
                    this.sender.sendMessage(ChatColor.RED + "MySQL connection lost");
                    return;
                }
                this.state = this.conn.createStatement();
                this.rs = this.state.executeQuery(this.params.getQuery());
                if (this.rs.next()) {
                    final Player player = (Player) this.sender;
                    final int y = this.rs.getInt(2);
                    final Location loc = new Location(this.params.world, this.rs.getInt(1) + 0.5, y,
                            this.rs.getInt(3) + 0.5, player.getLocation().getYaw(), 90);
                    final int y2 = saveSpawnHeight(loc);
                    loc.setY(y2);
                    player.teleport(loc);
                    this.sender.sendMessage(ChatColor.GREEN + "You were teleported " + Math.abs(y2 - y)
                            + " blocks " + (y2 - y > 0 ? "above" : "below"));
                } else this.sender.sendMessage(ChatColor.RED + "No block change found to teleport to");
            } catch (final Exception ex) {
                this.sender.sendMessage(ChatColor.RED + "Exception, check error log");
                getLogger().log(Level.SEVERE, "[LogBlock Teleport] " + this.params.getQuery() + ": ", ex);
            } finally {
                close();
            }
        }
    }

    public class CommandWriteLogFile extends AbstractCommand {
        public CommandWriteLogFile(CommandSender sender, QueryParams params, boolean async) throws Exception {
            super(sender, params, async);
        }

        @Override
        public void run() {
            File file = null;
            try {
                if (this.params.bct == BlockChangeType.CHAT) {
                    this.params.needDate = true;
                    this.params.needPlayer = true;
                    this.params.needMessage = true;
                } else {
                    this.params.needDate = true;
                    this.params.needType = true;
                    this.params.needData = true;
                    this.params.needPlayer = true;
                    if (this.params.types.size() == 0 || this.params.types.contains(63)
                            || this.params.types.contains(68)) this.params.needSignText = true;
                    if (this.params.types.size() == 0 || this.params.types.contains(23)
                            || this.params.types.contains(54) || this.params.types.contains(61)
                            || this.params.types.contains(62)) this.params.needChestAccess = true;
                }
                this.conn = CommandsHandler.this.logblock.getConnection();
                if (this.conn == null) {
                    this.sender.sendMessage(ChatColor.RED + "MySQL connection lost");
                    return;
                }
                this.state = this.conn.createStatement();
                file = new File("plugins/LogBlock/log/" + this.params.getTitle().replace(":", ".") + ".log");
                this.sender.sendMessage(ChatColor.GREEN + "Creating " + file.getName());
                this.rs = this.state.executeQuery(this.params.getQuery());
                file.getParentFile().mkdirs();
                file.createNewFile();
                final FileWriter writer = new FileWriter(file);
                final String newline = System.getProperty("line.separator");
                file.getParentFile().mkdirs();
                int counter = 0;
                if (this.params.sum != SummarizationMode.NONE)
                    writer.write("Created - Destroyed - "
                            + (this.params.sum == SummarizationMode.TYPES ? "Block" : "Player") + newline);
                final LookupCacheElementFactory factory = new LookupCacheElementFactory(this.params,
                        this.sender instanceof Player ? 2 / 3f : 1);
                while (this.rs.next()) {
                    writer.write(factory.getLookupCacheElement(this.rs).getMessage() + newline);
                    counter++;
                }
                writer.close();
                this.sender.sendMessage(ChatColor.GREEN + "Wrote " + counter + " lines.");
            } catch (final Exception ex) {
                this.sender.sendMessage(ChatColor.RED + "Exception, check error log");
                getLogger().log(
                        Level.SEVERE,
                        "[LogBlock WriteLogFile] " + this.params.getQuery() + " (file was "
                                + file.getAbsolutePath() + "): ", ex);
            } finally {
                close();
            }
        }
    }

    private static List<String> argsToList(String[] arr, int offset) {
        final List<String> list = new ArrayList<String>(Arrays.asList(arr));
        for (int i = 0; i < offset; i++)
            list.remove(0);
        return list;
    }

    static void showPage(CommandSender sender, int page) {
        final Session session = getSession(sender);
        if (session.lookupCache != null && session.lookupCache.length > 0) {
            final int startpos = (page - 1) * linesPerPage;
            if (page > 0 && startpos <= session.lookupCache.length - 1) {
                final int stoppos = startpos + linesPerPage >= session.lookupCache.length ? session.lookupCache.length - 1
                        : startpos + linesPerPage - 1;
                final int numberOfPages = (int) Math
                        .ceil(session.lookupCache.length / (double) linesPerPage);
                if (numberOfPages != 1)
                    sender.sendMessage(ChatColor.DARK_AQUA + "Page " + page + "/" + numberOfPages);
                for (int i = startpos; i <= stoppos; i++)
                    sender.sendMessage(ChatColor.GOLD
                            + (session.lookupCache[i].getLocation() != null ? "(" + (i + 1) + ") " : "")
                            + session.lookupCache[i].getMessage());
                session.page = page;
            } else sender.sendMessage(ChatColor.RED + "There isn't a page '" + page + "'");
        } else sender.sendMessage(ChatColor.RED + "No blocks in lookup cache");
    }

    final LogBlock logblock;

    final BukkitScheduler scheduler;

    CommandsHandler(LogBlock logblock) {
        this.logblock = logblock;
        this.scheduler = logblock.getServer().getScheduler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command c, String commandLabel, String[] args) {
        try {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "LogBlock v"
                        + this.logblock.getDescription().getVersion() + " by DiddiZ");
                if (checkVersion)
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + this.logblock.getUpdater().checkVersion());
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Type /lb help for help");
            } else {
                final String cmd = args[0].toLowerCase();
                if (cmd.equals("help")) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "LogBlock Help:");
                    sender.sendMessage(ChatColor.GOLD + "For the commands list type '/lb commands'");
                    sender.sendMessage(ChatColor.GOLD + "For the parameters list type '/lb params'");
                    sender.sendMessage(ChatColor.GOLD
                            + "For the list of permissions you got type '/lb permissions'");
                } else if (cmd.equals("commands")) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "LogBlock Commands:");
                    sender.sendMessage(ChatColor.GOLD + "/lb tool -- Gives you the lb tool");
                    sender.sendMessage(ChatColor.GOLD + "/lb tool [on|off] -- Enables/Disables tool");
                    sender.sendMessage(ChatColor.GOLD + "/lb tool [params] -- Sets the tool lookup query");
                    sender.sendMessage(ChatColor.GOLD
                            + "/lb tool default -- Sets the tool lookup query to default");
                    sender.sendMessage(ChatColor.GOLD + "/lb toolblock -- Analog to tool");
                    sender.sendMessage(ChatColor.GOLD + "/lb hide -- Hides you from log");
                    sender.sendMessage(ChatColor.GOLD + "/lb rollback [params] -- Rollback");
                    sender.sendMessage(ChatColor.GOLD + "/lb redo [params] -- Redo");
                    sender.sendMessage(ChatColor.GOLD
                            + "/lb tp [params] -- Teleports you to the location of griefing");
                    sender.sendMessage(ChatColor.GOLD + "/lb writelogfile [params] -- Writes a log file");
                    sender.sendMessage(ChatColor.GOLD + "/lb lookup [params] -- Lookup");
                    sender.sendMessage(ChatColor.GOLD + "/lb prev|next -- Browse lookup result pages");
                    sender.sendMessage(ChatColor.GOLD + "/lb page -- Shows a specific lookup result page");
                    sender.sendMessage(ChatColor.GOLD + "/lb me -- Displays your stats");
                    sender.sendMessage(ChatColor.GOLD
                            + "Look at github.com/DiddiZ/LogBlock/wiki/Commands for the full commands reference");
                } else if (cmd.equals("params")) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "LogBlock Query Parameters:");
                    sender.sendMessage(ChatColor.GOLD
                            + "Use doublequotes to escape a keyword: world \"world\"");
                    sender.sendMessage(ChatColor.GOLD + "player [name1] <name2> <name3> -- List of players");
                    sender.sendMessage(ChatColor.GOLD
                            + "block [type1] <type2> <type3> -- List of block types");
                    sender.sendMessage(ChatColor.GOLD
                            + "created, destroyed -- Show only created/destroyed blocks");
                    sender.sendMessage(ChatColor.GOLD + "chestaccess -- Show only chest accesses");
                    sender.sendMessage(ChatColor.GOLD + "area <radius> -- Area around you");
                    sender.sendMessage(ChatColor.GOLD
                            + "selection, sel -- Inside current WorldEdit selection");
                    sender.sendMessage(ChatColor.GOLD + "world [worldname] -- Changes the world");
                    sender.sendMessage(ChatColor.GOLD + "time [number] [minutes|hours|days] -- Limits time");
                    sender.sendMessage(ChatColor.GOLD
                            + "since <dd.MM.yyyy> <HH:mm:ss> -- Limits time to a fixed point");
                    sender.sendMessage(ChatColor.GOLD
                            + "before <dd.MM.yyyy> <HH:mm:ss> -- Affects only blocks before a fixed time");
                    sender.sendMessage(ChatColor.GOLD
                            + "limit <row count> -- Limits the result to count of rows");
                    sender.sendMessage(ChatColor.GOLD + "sum [none|blocks|players] -- Sums the result");
                    sender.sendMessage(ChatColor.GOLD
                            + "asc, desc -- Changes the order of the displayed log");
                    sender.sendMessage(ChatColor.GOLD + "coords -- Shows coordinates for each block");
                    sender.sendMessage(ChatColor.GOLD + "silent -- Displays lesser messages");
                } else if (cmd.equals("permissions")) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "You've got the following permissions:");
                    for (final String permission : new String[] { "me", "lookup", "tp", "rollback",
                            "clearlog", "hide", "ignoreRestrictions", "spawnTools" })
                        if (this.logblock.hasPermission(sender, "logblock." + permission))
                            sender.sendMessage(ChatColor.GOLD + "logblock." + permission);
                    for (final Tool tool : toolsByType.values())
                        if (this.logblock.hasPermission(sender, "logblock.tools." + tool.name))
                            sender.sendMessage(ChatColor.GOLD + "logblock.tools." + tool.name);
                } else if (cmd.equals("logging")) {
                    if (this.logblock.hasPermission(sender, "logblock.lookup")) {
                        World world = null;
                        if (args.length > 1)
                            world = getServer().getWorld(args[1]);
                        else if (sender instanceof Player) world = ((Player) sender).getWorld();
                        if (world != null) {
                            final WorldConfig wcfg = getWorldConfig(world.getName());
                            if (wcfg != null) {
                                sender.sendMessage(ChatColor.DARK_AQUA + "Currently logging in "
                                        + world.getName() + ":");
                                final List<String> logging = new ArrayList<String>();
                                for (final Logging l : Logging.values())
                                    if (wcfg.isLogging(l)) logging.add(l.toString());
                                sender.sendMessage(ChatColor.GOLD + listing(logging, ", ", " and "));
                            } else {
                                sender.sendMessage(ChatColor.RED + "World not logged: '" + world.getName()
                                        + "'");
                                sender.sendMessage(ChatColor.LIGHT_PURPLE
                                        + "Make the world name is listed at loggedWorlds in config. World names are case sensitive and must contains the path (if any), exactly like in the message above.");
                            }
                        } else sender.sendMessage(ChatColor.RED + "No world specified");
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                } else if (toolsByName.get(cmd) != null) {
                    final Tool tool = toolsByName.get(cmd);
                    if (this.logblock.hasPermission(sender, "logblock.tools." + tool.name)) {
                        if (sender instanceof Player) {
                            final Player player = (Player) sender;
                            final Session session = Session.getSession(player.getName());
                            final ToolData toolData = session.toolData.get(tool);
                            if (args.length == 1) {
                                if (this.logblock.hasPermission(player, "logblock.spawnTools")) {
                                    giveTool(player, tool.item);
                                    session.toolData.get(tool).enabled = true;
                                } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                            } else if (args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("on")) {
                                toolData.enabled = true;
                                player.sendMessage(ChatColor.GREEN + "Tool enabled.");
                            } else if (args[1].equalsIgnoreCase("disable")
                                    || args[1].equalsIgnoreCase("off")) {
                                toolData.enabled = false;
                                player.getInventory().removeItem(new ItemStack(tool.item, 1));
                                player.sendMessage(ChatColor.GREEN + "Tool disabled.");
                            } else if (args[1].equalsIgnoreCase("mode")) {
                                if (args.length == 3) {
                                    final ToolMode mode;
                                    try {
                                        mode = ToolMode.valueOf(args[2].toUpperCase());
                                    } catch (final IllegalArgumentException ex) {
                                        sender.sendMessage(ChatColor.RED + "Can't find mode " + args[2]);
                                        return true;
                                    }
                                    if (this.logblock.hasPermission(player, mode.getPermission())) {
                                        toolData.mode = mode;
                                        sender.sendMessage(ChatColor.GREEN + "Tool mode set to " + args[2]);
                                    } else sender.sendMessage(ChatColor.RED
                                            + "You aren't allowed to use mode " + args[2]);
                                } else player.sendMessage(ChatColor.RED + "No mode specified");
                            } else if (args[1].equalsIgnoreCase("default")) {
                                toolData.params = tool.params.clone();
                                toolData.mode = tool.mode;
                                sender.sendMessage(ChatColor.GREEN + "Tool set to default.");
                            } else if (this.logblock.hasPermission(player, "logblock.lookup"))
                                try {
                                    final QueryParams params = tool.params.clone();
                                    params.parseArgs(sender, argsToList(args, 1));
                                    toolData.params = params;
                                    sender.sendMessage(ChatColor.GREEN + "Set tool query to: "
                                            + params.getTitle());
                                } catch (final Exception ex) {
                                    sender.sendMessage(ChatColor.RED + ex.getMessage());
                                }
                            else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                        } else sender.sendMessage(ChatColor.RED + "You have to be a player.");
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                } else if (cmd.equals("hide")) {
                    if (sender instanceof Player) {
                        if (this.logblock.hasPermission(sender, "logblock.hide")) {
                            if (Consumer.hide((Player) sender))
                                sender.sendMessage(ChatColor.GREEN
                                        + "You are now hidden and aren't logged. Type '/lb hide' again to unhide");
                            else sender.sendMessage(ChatColor.GREEN + "You aren't hidden anylonger.");
                        } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                    } else sender.sendMessage(ChatColor.RED + "You have to be a player.");
                } else if (cmd.equals("page")) {
                    if (args.length == 2 && isInt(args[1]))
                        showPage(sender, Integer.valueOf(args[1]));
                    else sender.sendMessage(ChatColor.RED + "You have to specify a page");
                } else if (cmd.equals("next") || cmd.equals("+"))
                    showPage(sender, getSession(sender).page + 1);
                else if (cmd.equals("prev") || cmd.equals("-"))
                    showPage(sender, getSession(sender).page - 1);
                else if (cmd.equals("savequeue") || cmd.equals("sq")) {
                    if (this.logblock.hasPermission(sender, "logblock.rollback"))
                        new CommandSaveQueue(sender, null, true);
                    else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                } else if (cmd.equals("queuesize") || cmd.equals("qs")) {
                    if (this.logblock.hasPermission(sender, "logblock.rollback"))
                        sender.sendMessage("Current queue size: "
                                + this.logblock.getConsumer().getQueueSize());
                    else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                } else if (cmd.equals("killconnection")) {
                    if (this.logblock.hasPermission(sender, "logblock.killconnection"))
                        new CommandKillConnection(sender, null, true);
                } else if (cmd.equals("rollback") || cmd.equals("undo") || cmd.equals("rb")) {
                    if (this.logblock.hasPermission(sender, "logblock.rollback")) {
                        final QueryParams params = new QueryParams(this.logblock);
                        params.since = defaultTime;
                        params.bct = BlockChangeType.ALL;
                        params.parseArgs(sender, argsToList(args, 1));
                        new CommandRollback(sender, params, true);
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                } else if (cmd.equals("redo")) {
                    if (this.logblock.hasPermission(sender, "logblock.rollback")) {
                        final QueryParams params = new QueryParams(this.logblock);
                        params.since = defaultTime;
                        params.bct = BlockChangeType.ALL;
                        params.parseArgs(sender, argsToList(args, 1));
                        new CommandRedo(sender, params, true);
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                } else if (cmd.equals("me")) {
                    if (sender instanceof Player) {
                        if (this.logblock.hasPermission(sender, "logblock.me")) {
                            final Player player = (Player) sender;
                            if (Config.isLogged(player.getWorld())) {
                                final QueryParams params = new QueryParams(this.logblock);
                                params.setPlayer(player.getName());
                                params.world = player.getWorld();
                                player.sendMessage("Total block changes: " + this.logblock.getCount(params));
                                params.sum = SummarizationMode.TYPES;
                                new CommandLookup(sender, params, true);
                            } else sender.sendMessage(ChatColor.RED + "This world isn't logged");
                        } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this.");
                    } else sender.sendMessage(ChatColor.RED + "You have to be a player.");
                } else if (cmd.equals("writelogfile")) {
                    if (this.logblock.hasPermission(sender, "logblock.rollback")) {
                        final QueryParams params = new QueryParams(this.logblock);
                        params.limit = -1;
                        params.bct = BlockChangeType.ALL;
                        params.parseArgs(sender, argsToList(args, 1));
                        new CommandWriteLogFile(sender, params, true);
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this");
                } else if (cmd.equals("clearlog")) {
                    if (this.logblock.hasPermission(sender, "logblock.clearlog")) {
                        final QueryParams params = new QueryParams(this.logblock, sender,
                                argsToList(args, 1));
                        params.bct = BlockChangeType.ALL;
                        params.limit = -1;
                        new CommandClearLog(sender, params, true);
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this");
                } else if (cmd.equals("tp")) {
                    if (sender instanceof Player) {
                        if (this.logblock.hasPermission(sender, "logblock.tp"))
                            if (args.length == 2 || isInt(args[1])) {
                                final int pos = Integer.parseInt(args[1]) - 1;
                                final Player player = (Player) sender;
                                final Session session = getSession(player);
                                if (session.lookupCache != null)
                                    if (pos >= 0 && pos < session.lookupCache.length) {
                                        final Location loc = session.lookupCache[pos].getLocation();
                                        if (loc != null) {
                                            player.teleport(new Location(loc.getWorld(), loc.getX() + 0.5,
                                                    saveSpawnHeight(loc), loc.getZ() + 0.5, player
                                                            .getLocation().getYaw(), 90));
                                            player.sendMessage(ChatColor.LIGHT_PURPLE + "Teleported to "
                                                    + loc.getBlockX() + ":" + loc.getBlockY() + ":"
                                                    + loc.getBlockZ());
                                        } else sender
                                                .sendMessage(ChatColor.RED
                                                        + "There is no location associated with that. Did you forget coords parameter?");
                                    } else sender.sendMessage(ChatColor.RED + "'" + args[1]
                                            + " is out of range");
                                else sender.sendMessage(ChatColor.RED + "You havn't done a lookup yet");
                            } else new CommandTeleport(sender, new QueryParams(this.logblock, sender,
                                    argsToList(args, 1)), true);
                        else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this");
                    } else sender.sendMessage(ChatColor.RED + "You have to be a player.");
                } else if (cmd.equals("lookup") || QueryParams.isKeyWord(args[0])) {
                    if (this.logblock.hasPermission(sender, "logblock.lookup")) {
                        final List<String> argsList = new ArrayList<String>(Arrays.asList(args));
                        if (cmd.equals("lookup")) argsList.remove(0);
                        new CommandLookup(sender, new QueryParams(this.logblock, sender, argsList), true);
                    } else sender.sendMessage(ChatColor.RED + "You aren't allowed to do this");
                } else sender.sendMessage(ChatColor.RED + "Unknown command '" + args[0] + "'");
            }
        } catch (final IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
        } catch (final Exception ex) {
            sender.sendMessage(ChatColor.RED + "Error, check server.log");
            getLogger().log(Level.WARNING, "[LogBlock] Exception in commands handler: ", ex);
        }
        return true;
    }

    boolean checkRestrictions(CommandSender sender, QueryParams params) {
        if (sender.isOp() || this.logblock.hasPermission(sender, "logblock.ignoreRestrictions"))
            return true;
        if (rollbackMaxTime > 0 && (params.before > 0 || params.since > rollbackMaxTime)) {
            sender.sendMessage(ChatColor.RED + "You are not allowed to rollback more than "
                    + rollbackMaxTime + " minutes");
            return false;
        }
        if (rollbackMaxArea > 0
                && (params.sel == null && params.loc == null || params.radius > rollbackMaxArea || params.sel != null
                        && (params.sel.getLength() > rollbackMaxArea || params.sel.getWidth() > rollbackMaxArea))) {
            sender.sendMessage(ChatColor.RED + "You are not allowed to rollback an area larger than "
                    + rollbackMaxArea + " blocks");
            return false;
        }
        return true;
    }
}
