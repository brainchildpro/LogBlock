package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.config.Config.*;
import static de.diddiz.util.BukkitUtils.*;
import static org.bukkit.Bukkit.getLogger;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.*;
import java.util.logging.Level;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public class Consumer extends TimerTask {
    private class BlockRow extends BlockChange implements Row {
        public BlockRow(Location loc, String playerName, int replaced, int type, byte data, String signtext,
                ChestAccess ca) {
            super(System.currentTimeMillis() / 1000, loc, playerName, replaced, type, data, signtext, ca);
        }

        @Override
        public String[] getInserts() {
            final String table = getWorldConfig(this.loc.getWorld()).table;
            final String[] inserts = new String[this.ca != null || this.signtext != null ? 2 : 1];
            inserts[0] = "INSERT INTO `" + table
                    + "` (date, playerid, replaced, type, data, x, y, z) VALUES (FROM_UNIXTIME(" + this.date
                    + "), " + playerID(this.playerName) + ", " + this.replaced + ", " + this.type + ", "
                    + this.data + ", '" + this.loc.getBlockX() + "', " + this.loc.getBlockY() + ", '"
                    + this.loc.getBlockZ() + "');";
            if (this.signtext != null)
                inserts[1] = "INSERT INTO `" + table + "-sign` (id, signtext) values (LAST_INSERT_ID(), '"
                        + this.signtext + "');";
            else if (this.ca != null)
                inserts[1] = "INSERT INTO `" + table
                        + "-chest` (id, itemtype, itemamount, itemdata) values (LAST_INSERT_ID(), "
                        + this.ca.itemType + ", " + this.ca.itemAmount + ", " + this.ca.itemData + ");";
            return inserts;
        }

        @Override
        public String[] getPlayers() {
            return new String[] { this.playerName };
        }
    }

    private class ChatRow extends ChatMessage implements Row {
        ChatRow(String player, String message) {
            super(player, message);
        }

        @Override
        public String[] getInserts() {
            return new String[] { "INSERT INTO `lb-chat` (date, playerid, message) VALUES (FROM_UNIXTIME("
                    + this.date + "), " + playerID(this.playerName) + ", '" + this.message + "');" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { this.playerName };
        }
    }

    private class KillRow implements Row {
        final long date;
        final String killer, victim;
        final int weapon;
        final Location loc;

        KillRow(Location loc, String attacker, String defender, int weapon) {
            this.date = System.currentTimeMillis() / 1000;
            this.loc = loc;
            this.killer = attacker;
            this.victim = defender;
            this.weapon = weapon;
        }

        @Override
        public String[] getInserts() {
            return new String[] { "INSERT INTO `" + getWorldConfig(this.loc.getWorld()).table
                    + "-kills` (date, killer, victim, weapon, x, y, z) VALUES (FROM_UNIXTIME(" + this.date
                    + "), " + playerID(this.killer) + ", " + playerID(this.victim) + ", " + this.weapon
                    + ", " + this.loc.getBlockX() + ", " + this.loc.getBlockY() + ", "
                    + this.loc.getBlockZ() + ");" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { this.killer, this.victim };
        }
    }

    private class PlayerJoinRow implements Row {
        private final String playerName;
        private final long lastLogin;
        private final String ip;

        PlayerJoinRow(Player player) {
            this.playerName = player.getName();
            this.lastLogin = System.currentTimeMillis() / 1000;
            this.ip = player.getAddress().toString().replace("'", "\\'");
        }

        @Override
        public String[] getInserts() {
            return new String[] { "UPDATE `lb-players` SET lastlogin = FROM_UNIXTIME("
                    + this.lastLogin
                    + "), firstlogin = IF(firstlogin = 0, FROM_UNIXTIME("
                    + this.lastLogin
                    + "), firstlogin), ip = '"
                    + this.ip
                    + "' WHERE "
                    + (Consumer.this.playerIds.containsKey(this.playerName) ? "playerid = "
                            + Consumer.this.playerIds.get(this.playerName) : "playerName = '"
                            + this.playerName + "'") + ";" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { this.playerName };
        }
    }

    private class PlayerLeaveRow implements Row {
        private final String playerName;
        private final long leaveTime;

        PlayerLeaveRow(Player player) {
            this.playerName = player.getName();
            this.leaveTime = System.currentTimeMillis() / 1000;
        }

        @Override
        public String[] getInserts() {
            return new String[] { "UPDATE `lb-players` SET onlinetime = onlinetime + TIMESTAMPDIFF(SECOND, lastlogin, FROM_UNIXTIME('"
                    + this.leaveTime
                    + "')) WHERE lastlogin > 0 && "
                    + (Consumer.this.playerIds.containsKey(this.playerName) ? "playerid = "
                            + Consumer.this.playerIds.get(this.playerName) : "playerName = '"
                            + this.playerName + "'") + ";" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { this.playerName };
        }
    }

    private static interface Row {
        String[] getInserts();

        String[] getPlayers();
    }

    static boolean hide(Player player) {
        final String playerName = player.getName();
        if (hiddenPlayers.contains(playerName)) {
            hiddenPlayers.remove(playerName);
            return false;
        }
        hiddenPlayers.add(playerName);
        return true;
    }

    private final Queue<Row> queue = new LinkedBlockingQueue<Row>();

    private final Set<String> failedPlayers = new HashSet<String>();

    private final LogBlock logblock;

    final Map<String, Integer> playerIds = new HashMap<String, Integer>();

    private final Lock lock = new ReentrantLock();

    Consumer(LogBlock logblock) {
        this.logblock = logblock;
        try {
            Class.forName("PlayerLeaveRow");
        } catch (final ClassNotFoundException ex) {
        }
    }

    /**
     * Logs any block change. Don't try to combine broken and placed blocks.
     * Queue two block changes or use the queueBLockReplace methods.
     */
    public void queueBlock(String playerName, Location loc, int typeBefore, int typeAfter, byte data) {
        queueBlock(playerName, loc, typeBefore, typeAfter, data, null, null);
    }

    /**
     * Logs a block break. The type afterwards is assumed to be o (air).
     * 
     * @param before
     *            Blockstate of the block before actually being destroyed.
     */
    public void queueBlockBreak(String playerName, BlockState before) {
        queueBlockBreak(playerName,
                new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()),
                before.getTypeId(), before.getRawData());
    }

    /**
     * Logs a block break. The block type afterwards is assumed to be o (air).
     */
    public void queueBlockBreak(String playerName, Location loc, int typeBefore, byte dataBefore) {
        queueBlock(playerName, loc, typeBefore, 0, dataBefore);
    }

    /**
     * Logs a block place. The block type before is assumed to be o (air).
     * 
     * @param after
     *            Blockstate of the block after actually being placed.
     */
    public void queueBlockPlace(String playerName, BlockState after) {
        queueBlockPlace(playerName,
                new Location(after.getWorld(), after.getX(), after.getY(), after.getZ()), after.getTypeId(),
                after.getRawData());
    }

    /**
     * Logs a block place. The block type before is assumed to be o (air).
     */
    public void queueBlockPlace(String playerName, Location loc, int type, byte data) {
        queueBlock(playerName, loc, 0, type, data);
    }

    /**
     * @param before
     *            Blockstate of the block before actually being destroyed.
     * @param after
     *            Blockstate of the block after actually being placed.
     */
    public void queueBlockReplace(String playerName, BlockState before, BlockState after) {
        queueBlockReplace(playerName,
                new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()),
                before.getTypeId(), before.getRawData(), after.getTypeId(), after.getRawData());
    }

    /**
     * @param before
     *            Blockstate of the block before actually being destroyed.
     */
    public void queueBlockReplace(String playerName, BlockState before, int typeAfter, byte dataAfter) {
        queueBlockReplace(playerName,
                new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()),
                before.getTypeId(), before.getRawData(), typeAfter, dataAfter);
    }

    /**
     * @param after
     *            Blockstate of the block after actually being placed.
     */
    public void queueBlockReplace(String playerName, int typeBefore, byte dataBefore, BlockState after) {
        queueBlockReplace(playerName,
                new Location(after.getWorld(), after.getX(), after.getY(), after.getZ()), typeBefore,
                dataBefore, after.getTypeId(), after.getRawData());
    }

    public void queueBlockReplace(String playerName, Location loc, int typeBefore, byte dataBefore,
            int typeAfter, byte dataAfter) {
        if (dataBefore == 0)
            queueBlock(playerName, loc, typeBefore, typeAfter, dataAfter);
        else {
            queueBlockBreak(playerName, loc, typeBefore, dataBefore);
            queueBlockPlace(playerName, loc, typeAfter, dataAfter);
        }
    }

    public void queueChat(String player, String message) {
        this.queue.add(new ChatRow(player, message.replace("\\", "\\\\").replace("'", "\\'")));
    }

    /**
     * @param container
     *            The respective container. Must be an instance of Chest,
     *            Dispencer or Furnace.
     */
    public void queueChestAccess(String playerName, BlockState container, short itemType, short itemAmount,
            byte itemData) {
        if (!(container instanceof ContainerBlock)) return;
        queueChestAccess(playerName, new Location(container.getWorld(), container.getX(), container.getY(),
                container.getZ()), container.getTypeId(), itemType, itemAmount, itemData);
    }

    /**
     * @param type
     *            Type id of the container. Must be 63 or 68.
     */
    public void queueChestAccess(String playerName, Location loc, int type, short itemType,
            short itemAmount, byte itemData) {
        queueBlock(playerName, loc, type, type, (byte) 0, null, new ChestAccess(itemType, itemAmount,
                itemData));
    }

    /**
     * Logs a container block break. The block type before is assumed to be o
     * (air). All content is assumed to be taken.
     * 
     * @param container
     *            Must be instanceof ContainerBlock
     */
    public void queueContainerBreak(String playerName, BlockState container) {
        if (!(container instanceof ContainerBlock)) return;
        queueContainerBreak(playerName,
                new Location(container.getWorld(), container.getX(), container.getY(), container.getZ()),
                container.getTypeId(), container.getRawData(), ((ContainerBlock) container).getInventory());
    }

    /**
     * Logs a container block break. The block type before is assumed to be o
     * (air). All content is assumed to be taken.
     */
    public void queueContainerBreak(String playerName, Location loc, int type, byte data, Inventory inv) {
        final ItemStack[] items = compressInventory(inv.getContents());
        for (final ItemStack item : items)
            queueChestAccess(playerName, loc, type, (short) item.getTypeId(),
                    (short) (item.getAmount() * -1), rawData(item));
        queueBlockBreak(playerName, loc, type, data);
    }

    public void queueJoin(Player player) {
        this.queue.add(new PlayerJoinRow(player));
    }

    /**
     * @param killer
     *            Can' be null
     * @param victim
     *            Can' be null
     */
    public void queueKill(Entity killer, Entity victim) {
        if (killer == null || victim == null) return;
        int weapon = 0;
        if (killer instanceof Player && ((Player) killer).getItemInHand() != null)
            weapon = ((Player) killer).getItemInHand().getTypeId();
        queueKill(victim.getLocation(), entityName(killer), entityName(victim), weapon);
    }

    /**
     * @param location
     *            Location of the victim.
     * @param killerName
     *            Name of the killer. Can be null.
     * @param victimName
     *            Name of the victim. Can't be null.
     * @param weapon
     *            Item id of the weapon. 0 for no weapon.
     */
    public void queueKill(Location location, String killerName, String victimName, int weapon) {
        if (victimName == null || !isLogged(location.getWorld())) return;
        this.queue.add(new KillRow(location, killerName == null ? null : killerName.replaceAll(
                "[^a-zA-Z0-9_]", ""), victimName.replaceAll("[^a-zA-Z0-9_]", ""), weapon));
    }

    /**
     * @param world
     *            World the victim was inside.
     * @param killerName
     *            Name of the killer. Can be null.
     * @param victimName
     *            Name of the victim. Can't be null.
     * @param weapon
     *            Item id of the weapon. 0 for no weapon.
     * @deprecated Use {@link #queueKill(Location,String,String,int)} instead
     */
    @Deprecated
    public void queueKill(World world, String killerName, String victimName, int weapon) {
        queueKill(new Location(world, 0, 0, 0), killerName, victimName, weapon);
    }

    public void queueLeave(Player player) {
        this.queue.add(new PlayerLeaveRow(player));
    }

    /**
     * @param type
     *            Type of the sign. Must be 63 or 68.
     * @param lines
     *            The four lines on the sign.
     */
    public void queueSignBreak(String playerName, Location loc, int type, byte data, String[] lines) {
        if (type != 63 && type != 68 || lines == null || lines.length != 4) return;
        queueBlock(playerName, loc, type, 0, data, lines[0] + "\0" + lines[1] + "\0" + lines[2] + "\0"
                + lines[3], null);
    }

    public void queueSignBreak(String playerName, Sign sign) {
        queueSignBreak(playerName, new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ()),
                sign.getTypeId(), sign.getRawData(), sign.getLines());
    }

    /**
     * @param type
     *            Type of the sign. Must be 63 or 68.
     * @param lines
     *            The four lines on the sign.
     */
    public void queueSignPlace(String playerName, Location loc, int type, byte data, String[] lines) {
        if (type != 63 && type != 68 || lines == null || lines.length != 4) return;
        queueBlock(playerName, loc, 0, type, data, lines[0] + "\0" + lines[1] + "\0" + lines[2] + "\0"
                + lines[3], null);
    }

    public void queueSignPlace(String playerName, Sign sign) {
        queueSignPlace(playerName, new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ()),
                sign.getTypeId(), sign.getRawData(), sign.getLines());
    }

    @Override
    public void run() {
        if (this.queue.isEmpty() || !this.lock.tryLock()) return;
        final Connection conn = this.logblock.getConnection();
        Statement state = null;
        if (getQueueSize() > 1000)
            getLogger().info("[LogBlock Consumer] Queue overloaded. Size: " + getQueueSize());
        if(getQueueSize() > killConnectionAfter) {
            if(conn != null) try {
                conn.close();
                getLogger().severe("[LogBlock Consumer] Connection killed. Queue size: " + getQueueSize());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        try {
            if (conn == null) return;
            conn.setAutoCommit(false);
            state = conn.createStatement();
            final long start = System.currentTimeMillis();
            int count = 0;
            process: while (!this.queue.isEmpty()
                    && (System.currentTimeMillis() - start < timePerRun || count < forceToProcessAtLeast)) {
                final Row r = this.queue.poll();
                if (r == null) continue;
                for (final String player : r.getPlayers())
                    if (!this.playerIds.containsKey(player)) if (!addPlayer(state, player)) {
                        if (!this.failedPlayers.contains(player)) {
                            this.failedPlayers.add(player);
                            getLogger().warning("[LogBlock Consumer] Failed to add player " + player);
                        }
                        continue process;
                    }
                for (final String insert : r.getInserts())
                    try {
                        state.execute(insert);
                    } catch (final SQLException ex) {
                        getLogger().log(Level.SEVERE,
                                "[LogBlock Consumer] SQL exception on " + insert + ": ", ex);
                        break process;
                    }
                count++;
            }
            conn.commit();
        } catch (final SQLException ex) {
            getLogger().log(Level.SEVERE, "[LogBlock Consumer] SQL exception", ex);
        } finally {
            try {
                if (state != null) state.close();
                if (conn != null) conn.close();
            } catch (final SQLException ex) {
                getLogger().log(Level.SEVERE, "[LogBlock Consumer] SQL exception on close", ex);
            }
            this.lock.unlock();
        }
    }

    public void writeToFile() throws FileNotFoundException {
        final long time = System.currentTimeMillis();
        final Set<String> insertedPlayers = new HashSet<String>();
        int counter = 0;
        new File("plugins/LogBlock/import/").mkdirs();
        PrintWriter writer = new PrintWriter(new File("plugins/LogBlock/import/queue-" + time + "-0.sql"));
        while (!this.queue.isEmpty()) {
            final Row r = this.queue.poll();
            if (r == null) continue;
            for (final String player : r.getPlayers())
                if (!this.playerIds.containsKey(player) && !insertedPlayers.contains(player)) {
                    writer.println("INSERT IGNORE INTO `lb-players` (playername) VALUES ('" + player + "');");
                    insertedPlayers.add(player);
                }
            for (final String insert : r.getInserts())
                writer.println(insert);
            counter++;
            if (counter % 1000 == 0) {
                writer.close();
                writer = new PrintWriter(new File("plugins/LogBlock/import/queue-" + time + "-" + counter
                        / 1000 + ".sql"));
            }
        }
        writer.close();
    }

    private boolean addPlayer(Statement state, String playerName) throws SQLException {
        state.execute("INSERT IGNORE INTO `lb-players` (playername) VALUES ('" + playerName + "')");
        final ResultSet rs = state.executeQuery("SELECT playerid FROM `lb-players` WHERE playername = '"
                + playerName + "'");
        if (rs.next()) this.playerIds.put(playerName, rs.getInt(1));
        rs.close();
        return this.playerIds.containsKey(playerName);
    }

    String playerID(String playerName) {
        if (playerName == null) return "NULL";
        final Integer id = this.playerIds.get(playerName);
        if (id != null) return id.toString();
        return "(SELECT playerid FROM `lb-players` WHERE playername = '" + playerName + "')";
    }

    private void queueBlock(String playerName, Location loc, int typeBefore, int typeAfter, byte data,
            String signtext, ChestAccess ca) {
        if (playerName == null || loc == null || typeBefore < 0 || typeAfter < 0 || typeBefore > 255
                || typeAfter > 255 || hiddenPlayers.contains(playerName) || !isLogged(loc.getWorld())
                || typeBefore != typeAfter && hiddenBlocks.contains(typeBefore)
                && hiddenBlocks.contains(typeAfter)) return;
        this.queue.add(new BlockRow(loc, playerName.replaceAll("[^a-zA-Z0-9_]", ""), typeBefore, typeAfter,
                data, signtext != null ? signtext.replace("\\", "\\\\").replace("'", "\\'") : null, ca));
    }

    int getQueueSize() {
        return this.queue.size();
    }
}