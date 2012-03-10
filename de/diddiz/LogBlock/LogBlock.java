package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.config.Config.*;
import static de.diddiz.util.Utils.download;
import static org.bukkit.Bukkit.getPluginManager;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

import de.diddiz.LogBlock.config.Config;
import de.diddiz.LogBlock.listeners.*;
import de.diddiz.util.MySQLConnectionPool;

public class LogBlock extends JavaPlugin {
    private static LogBlock logblock = null;

    public static LogBlock getInstance() {
        return logblock;
    }

    private MySQLConnectionPool pool;
    private Consumer consumer = null;
    private CommandsHandler commandsHandler;
    private Updater updater = null;
    private Timer timer = null;
    private PermissionHandler permissions = null;

    private final Vector<Question> questions = new Vector<Question>();

    private boolean errorAtLoading = false, noDb = false, connected = true;

    public String ask(final Player respondent, final String questionMessage, final String... answers) {
        final Question question = new Question(respondent, questionMessage, answers);
        questions.add(question);
        return question.ask();
    }

    /**
     * @param params
     *            QueryParams that contains the needed columns (all other will
     *            be filled with default values) and the params. World is
     *            required.
     */
    public List<BlockChange> getBlockChanges(final QueryParams params) throws SQLException {
        final Connection conn = getConnection();
        Statement state = null;
        if (conn == null) throw new SQLException("No connection");
        try {
            state = conn.createStatement();
            final ResultSet rs = state.executeQuery(params.getQuery());
            final List<BlockChange> blockchanges = new ArrayList<BlockChange>();
            while (rs.next())
                blockchanges.add(new BlockChange(rs, params));
            return blockchanges;
        } finally {
            if (state != null) state.close();
            conn.close();
        }
    }

    public CommandsHandler getCommandsHandler() {
        return commandsHandler;
    }

    public Connection getConnection() {
        try {
            final Connection conn = pool.getConnection();
            if (!connected) {
                getLogger().info("[LogBlock] MySQL connection rebuild");
                connected = true;
            }
            return conn;
        } catch (final Exception ex) {
            if (connected) {
                getLogger().log(Level.SEVERE, "[LogBlock] Error while fetching connection: ", ex);
                connected = false;
            } else getLogger().severe("[LogBlock] MySQL connection lost");
            return null;
        }
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public int getCount(final QueryParams params) throws SQLException {
        final Connection conn = getConnection();
        Statement state = null;
        if (conn == null) throw new SQLException("No connection");
        try {
            state = conn.createStatement();
            final QueryParams p = params.clone();
            p.needCount = true;
            final ResultSet rs = state.executeQuery(p.getQuery());
            if (!rs.next()) return 0;
            return rs.getInt(1);
        } finally {
            if (state != null) state.close();
            conn.close();
        }
    }

    public boolean hasPermission(final CommandSender sender, final String permission) {
        if (permissions != null && sender instanceof Player)
            return permissions.has((Player) sender, permission);
        return sender.hasPermission(permission);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String commandLabel,
            final String[] args) {
        if (noDb)
            sender.sendMessage(ChatColor.RED
                    + "No database connected. Check your MySQL user/pw and database for typos. Start/restart your MySQL server.");
        return true;
    }

    @Override
    public void onDisable() {
        if (timer != null) timer.cancel();
        getServer().getScheduler().cancelTasks(this);
        if (consumer != null) {
            if (logPlayerInfo && getServer().getOnlinePlayers() != null)
                for (final Player player : getServer().getOnlinePlayers())
                    consumer.queueLeave(player);
            if (consumer.getQueueSize() > 0) {
                getLogger().info("[LogBlock] Waiting for consumer ...");
                int tries = 10;
                while (consumer.getQueueSize() > 0) {
                    getLogger().info("[LogBlock] Remaining queue size: " + consumer.getQueueSize());
                    if (tries > 0)
                        getLogger().info("[LogBlock] Remaining tries: " + tries);
                    else {
                        getLogger().info(
                                "Unable to save queue to database. Trying to write to a local file.");
                        try {
                            consumer.writeToFile();
                            getLogger().info("Successfully dumped queue. Disabling..");
                            break;
                        } catch (final FileNotFoundException ex) {
                            getLogger().info("Failed to write. Given up.");
                            break;
                        }
                    }
                    consumer.run();
                    tries--;
                }
            }
        }
        if (pool != null) pool.close();
        getLogger().info("LogBlock disabled.");
    }

    @Override
    public void onEnable() {
        final PluginManager pm = getPluginManager();
        if (errorAtLoading) {
            pm.disablePlugin(this);
            return;
        }
        if (noDb) return;
        if (pm.getPlugin("WorldEdit") == null && !new File("lib/WorldEdit.jar").exists()
                && !new File("WorldEdit.jar").exists())
            try {
                download(getLogger(), new URL("http://diddiz.insane-architects.net/download/WorldEdit.jar"),
                        new File("lib/WorldEdit.jar"));
                getLogger().info("[LogBlock] You've to restart/reload your server now.");
                pm.disablePlugin(this);
                return;
            } catch (final Exception ex) {
                getLogger()
                        .warning(
                                "[LogBlock] Failed to download WorldEdit. You may have to download it manually. You don't have to install it, just place the jar in the lib folder.");
            }
        commandsHandler = new CommandsHandler(this);
        getCommand("lb").setExecutor(commandsHandler);
        if (pm.getPlugin("Permissions") != null) {
            permissions = ((Permissions) pm.getPlugin("Permissions")).getHandler();
            getLogger().info("[LogBlock] Permissions plugin found.");
        } else getLogger().info("[LogBlock] Permissions plugin not found. Using Bukkit Permissions.");
        if (enableAutoClearLog && autoClearLogDelay > 0)
            getServer().getScheduler().scheduleAsyncRepeatingTask(this, new AutoClearLog(this), 6000,
                    autoClearLogDelay * 60 * 20);
        getServer().getScheduler().scheduleAsyncDelayedTask(this, new DumpedLogImporter(this));
        registerEvents();
        if (useBukkitScheduler) {
            if (getServer().getScheduler().scheduleAsyncRepeatingTask(this, consumer, delayBetweenRuns * 20,
                    delayBetweenRuns * 20) > 0)
                getLogger().info("[LogBlock] Scheduled consumer with bukkit scheduler.");
            else {
                getLogger()
                        .warning(
                                "[LogBlock] Failed to schedule consumer with bukkit scheduler. Now trying schedule with timer.");
                timer = new Timer();
                timer.scheduleAtFixedRate(consumer, delayBetweenRuns * 1000, delayBetweenRuns * 1000);
            }
        } else {
            timer = new Timer();
            timer.scheduleAtFixedRate(consumer, delayBetweenRuns * 1000, delayBetweenRuns * 1000);
            getLogger().info("[LogBlock] Scheduled consumer with timer.");
        }
        for (final Tool tool : toolsByType.values())
            if (pm.getPermission("logblock.tools." + tool.name) == null) {
                final Permission perm = new Permission("logblock.tools." + tool.name, tool.permissionDefault);
                pm.addPermission(perm);
            }
        // perm.addParent("logblock.*", true);
        getServer().getPluginManager().registerEvents(new LogBlockQuestionerPlayerListener(questions), this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new QuestionsReaper(questions), 15000,
                15000);
        getLogger().info("LogBlock v" + getDescription().getVersion() + " by DiddiZ enabled.");
    }

    @Override
    public void onLoad() {
        logblock = this;
        try {
            updater = new Updater(this);
            Config.load(this);
            getLogger().info("[LogBlock] Connecting to " + user + "@" + url + "...");
            pool = new MySQLConnectionPool(url, user, password);
            final Connection conn = getConnection();
            if (conn == null) {
                noDb = true;
                return;
            }
            conn.close();
            if (updater.update()) load(this);
            updater.checkTables();
        } catch (final NullPointerException ex) {
            getLogger().log(Level.SEVERE, "[LogBlock] Error while loading: ", ex);
        } catch (final Exception ex) {
            getLogger().severe("[LogBlock] Error while loading: " + ex.getMessage());
            errorAtLoading = true;
            return;
        }
        consumer = new Consumer(this);
    }

    public void reload() {
        // TODO
    }

    Updater getUpdater() {
        return updater;
    }

    private void registerEvents() {
        final PluginManager pm = getPluginManager();
        pm.registerEvents(new ToolListener(this), this);
        if (askRollbackAfterBan) pm.registerEvents(new BanListener(this), this);
        if (isLogging(Logging.BLOCKPLACE)) pm.registerEvents(new BlockPlaceLogging(this), this);
        if (isLogging(Logging.BLOCKPLACE) || isLogging(Logging.LAVAFLOW) || isLogging(Logging.WATERFLOW))
            pm.registerEvents(new FluidFlowLogging(this), this);
        if (isLogging(Logging.BLOCKBREAK)) pm.registerEvents(new BlockBreakLogging(this), this);
        if (isLogging(Logging.SIGNTEXT)) pm.registerEvents(new SignChangeLogging(this), this);
        if (isLogging(Logging.FIRE)) pm.registerEvents(new BlockBurnLogging(this), this);
        if (isLogging(Logging.SNOWFORM)) pm.registerEvents(new SnowFormLogging(this), this);
        if (isLogging(Logging.SNOWFADE)) pm.registerEvents(new SnowFadeLogging(this), this);
        if (isLogging(Logging.CREEPEREXPLOSION) || isLogging(Logging.TNTEXPLOSION)
                || isLogging(Logging.GHASTFIREBALLEXPLOSION) || isLogging(Logging.ENDERDRAGON)
                || isLogging(Logging.MISCEXPLOSION)) pm.registerEvents(new ExplosionLogging(this), this);
        if (isLogging(Logging.LEAVESDECAY)) pm.registerEvents(new LeavesDecayLogging(this), this);
        if (isLogging(Logging.CHESTACCESS)) {
            pm.registerEvents(new ChestAccessLogging(this), this);
            getLogger().info("[LogBlock] Using own chest access API");
        }
        if (isLogging(Logging.SWITCHINTERACT) || isLogging(Logging.DOORINTERACT)
                || isLogging(Logging.CAKEEAT) || isLogging(Logging.DIODEINTERACT)
                || isLogging(Logging.NOTEBLOCKINTERACT)) pm.registerEvents(new InteractLogging(this), this);
        if (isLogging(Logging.KILL)) pm.registerEvents(new KillLogging(this), this);
        if (isLogging(Logging.CHAT)) pm.registerEvents(new ChatLogging(this), this);
        if (isLogging(Logging.ENDERMEN)) pm.registerEvents(new EndermenLogging(this), this);
        if (isLogging(Logging.NATURALSTRUCTUREGROW) || isLogging(Logging.BONEMEALSTRUCTUREGROW))
            pm.registerEvents(new StructureGrowLogging(this), this);
        if (logPlayerInfo) pm.registerEvents(new PlayerInfoLogging(this), this);
    }
}
