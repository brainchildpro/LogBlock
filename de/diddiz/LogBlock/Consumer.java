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

import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public class Consumer extends TimerTask {
    private class BlockRow extends BlockChange implements Row {
        public BlockRow(final Location loc, final String playerName, final int replaced, final int type,
                final byte data, final String signtext, final ChestAccess ca) {
            super(System.currentTimeMillis() / 1000, loc, playerName, replaced, type, data, signtext, ca);
        }

        @Override
        public String[] getInserts() {
            final String table = getWorldConfig(loc.getWorld()).table;
            final String[] inserts = new String[ca != null || signtext != null ? 2 : 1];
            inserts[0] = "INSERT INTO `" + table
                    + "` (date, playerid, replaced, type, data, x, y, z) VALUES (FROM_UNIXTIME(" + date
                    + "), " + playerID(playerName) + ", " + replaced + ", " + type + ", " + data + ", '"
                    + loc.getBlockX() + "', " + loc.getBlockY() + ", '" + loc.getBlockZ() + "');";
            if (signtext != null)
                inserts[1] = "INSERT INTO `" + table + "-sign` (id, signtext) values (LAST_INSERT_ID(), '"
                        + signtext + "');";
            else if (ca != null)
                inserts[1] = "INSERT INTO `" + table
                        + "-chest` (id, itemtype, itemamount, itemdata) values (LAST_INSERT_ID(), "
                        + ca.itemType + ", " + ca.itemAmount + ", " + ca.itemData + ");";
            return inserts;
        }

        @Override
        public String[] getPlayers() {
            return new String[] { playerName };
        }
    }

    private class ChatRow extends ChatMessage implements Row {
        ChatRow(final String player, final String message) {
            super(player, message);
        }

        @Override
        public String[] getInserts() {
            return new String[] { "INSERT INTO `lb-chat` (date, playerid, message) VALUES (FROM_UNIXTIME("
                    + date + "), " + playerID(playerName) + ", '" + message + "');" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { playerName };
        }
    }

    private class KillRow implements Row {
        final long date;
        final String killer, victim;
        final int weapon;
        final Location loc;

        KillRow(final Location loc, final String attacker, final String defender, final int weapon) {
            date = System.currentTimeMillis() / 1000;
            this.loc = loc;
            killer = attacker;
            victim = defender;
            this.weapon = weapon;
        }

        @Override
        public String[] getInserts() {
            return new String[] { "INSERT INTO `" + getWorldConfig(loc.getWorld()).table
                    + "-kills` (date, killer, victim, weapon, x, y, z) VALUES (FROM_UNIXTIME(" + date
                    + "), " + playerID(killer) + ", " + playerID(victim) + ", " + weapon + ", "
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ");" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { killer, victim };
        }
    }

    private class PlayerJoinRow implements Row {
        private final String playerName;
        private final long lastLogin;
        private final String ip;

        PlayerJoinRow(final Player player) {
            playerName = player.getName();
            lastLogin = System.currentTimeMillis() / 1000;
            ip = player.getAddress().toString().replace("'", "\\'");
        }

        @Override
        public String[] getInserts() {
            return new String[] { "UPDATE `lb-players` SET lastlogin = FROM_UNIXTIME("
                    + lastLogin
                    + "), firstlogin = IF(firstlogin = 0, FROM_UNIXTIME("
                    + lastLogin
                    + "), firstlogin), ip = '"
                    + ip
                    + "' WHERE "
                    + (playerIds.containsKey(playerName) ? "playerid = " + playerIds.get(playerName)
                            : "playerName = '" + playerName + "'") + ";" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { playerName };
        }
    }

    private class PlayerLeaveRow implements Row {
        private final String playerName;
        private final long leaveTime;

        PlayerLeaveRow(final Player player) {
            playerName = player.getName();
            leaveTime = System.currentTimeMillis() / 1000;
        }

        @Override
        public String[] getInserts() {
            return new String[] { "UPDATE `lb-players` SET onlinetime = onlinetime + TIMESTAMPDIFF(SECOND, lastlogin, FROM_UNIXTIME('"
                    + leaveTime
                    + "')) WHERE lastlogin > 0 && "
                    + (playerIds.containsKey(playerName) ? "playerid = " + playerIds.get(playerName)
                            : "playerName = '" + playerName + "'") + ";" };
        }

        @Override
        public String[] getPlayers() {
            return new String[] { playerName };
        }
    }

    private static interface Row {
        String[] getInserts();

        String[] getPlayers();
    }

    static boolean hide(final Player player) {
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

    Consumer(final LogBlock logblock) {
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
    public void queueBlock(final String playerName, final Location loc, final int typeBefore,
            final int typeAfter, final byte data) {
        queueBlock(playerName, loc, typeBefore, typeAfter, data, null, null);
    }

    /**
     * Logs a block break. The type afterwards is assumed to be o (air).
     * 
     * @param before
     *            Blockstate of the block before actually being destroyed.
     */
    public void queueBlockBreak(final String playerName, final BlockState before) {
        queueBlockBreak(playerName,
                new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()),
                before.getTypeId(), before.getRawData());
    }

    /**
     * Logs a block break. The block type afterwards is assumed to be o (air).
     */
    public void queueBlockBreak(final String playerName, final Location loc, final int typeBefore,
            final byte dataBefore) {
        queueBlock(playerName, loc, typeBefore, 0, dataBefore);
    }

    /**
     * Logs a block place. The block type before is assumed to be o (air).
     * 
     * @param after
     *            Blockstate of the block after actually being placed.
     */
    public void queueBlockPlace(final String playerName, final BlockState after) {
        queueBlockPlace(playerName,
                new Location(after.getWorld(), after.getX(), after.getY(), after.getZ()), after.getTypeId(),
                after.getRawData());
    }

    /**
     * Logs a block place. The block type before is assumed to be o (air).
     */
    public void queueBlockPlace(final String playerName, final Location loc, final int type, final byte data) {
        queueBlock(playerName, loc, 0, type, data);
    }

    /**
     * @param before
     *            Blockstate of the block before actually being destroyed.
     * @param after
     *            Blockstate of the block after actually being placed.
     */
    public void queueBlockReplace(final String playerName, final BlockState before, final BlockState after) {
        queueBlockReplace(playerName,
                new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()),
                before.getTypeId(), before.getRawData(), after.getTypeId(), after.getRawData());
    }

    /**
     * @param before
     *            Blockstate of the block before actually being destroyed.
     */
    public void queueBlockReplace(final String playerName, final BlockState before, final int typeAfter,
            final byte dataAfter) {
        queueBlockReplace(playerName,
                new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()),
                before.getTypeId(), before.getRawData(), typeAfter, dataAfter);
    }

    /**
     * @param after
     *            Blockstate of the block after actually being placed.
     */
    public void queueBlockReplace(final String playerName, final int typeBefore, final byte dataBefore,
            final BlockState after) {
        queueBlockReplace(playerName,
                new Location(after.getWorld(), after.getX(), after.getY(), after.getZ()), typeBefore,
                dataBefore, after.getTypeId(), after.getRawData());
    }

    public void queueBlockReplace(final String playerName, final Location loc, final int typeBefore,
            final byte dataBefore, final int typeAfter, final byte dataAfter) {
        if (dataBefore == 0)
            queueBlock(playerName, loc, typeBefore, typeAfter, dataAfter);
        else {
            queueBlockBreak(playerName, loc, typeBefore, dataBefore);
            queueBlockPlace(playerName, loc, typeAfter, dataAfter);
        }
    }

    public void queueChat(final String player, final String message) {
        addToQueue(new ChatRow(player, message.replace("\\", "\\\\").replace("'", "\\'")));
    }

    /**
     * @param container
     *            The respective container. Must be an instance of Chest,
     *            Dispencer or Furnace.
     */
    public void queueChestAccess(final String playerName, final BlockState container, final short itemType,
            final short itemAmount, final byte itemData) {
        if (!(container instanceof InventoryHolder)) return;
        queueChestAccess(playerName, new Location(container.getWorld(), container.getX(), container.getY(),
                container.getZ()), container.getTypeId(), itemType, itemAmount, itemData);
    }

    /**
     * @param type
     *            Type id of the container. Must be 63 or 68.
     */
    public void queueChestAccess(final String playerName, final Location loc, final int type,
            final short itemType, final short itemAmount, final byte itemData) {
        queueBlock(playerName, loc, type, type, (byte) 0, null, new ChestAccess(itemType, itemAmount,
                itemData));
    }

    /**
     * Logs a container block break. The block type before is assumed to be o
     * (air). All content is assumed to be taken.
     * 
     * @param container
     *            Must be instanceof InventoryHolder
     */
    public void queueContainerBreak(final String playerName, final BlockState container) {
        if (!(container instanceof InventoryHolder)) return;
        queueContainerBreak(playerName,
                new Location(container.getWorld(), container.getX(), container.getY(), container.getZ()),
                container.getTypeId(), container.getRawData(), ((InventoryHolder) container).getInventory());
    }

    /**
     * Logs a container block break. The block type before is assumed to be o
     * (air). All content is assumed to be taken.
     */
    public void queueContainerBreak(final String playerName, final Location loc, final int type,
            final byte data, final Inventory inv) {
        final ItemStack[] items = compressInventory(inv.getContents());
        for (final ItemStack item : items)
            queueChestAccess(playerName, loc, type, (short) item.getTypeId(),
                    (short) (item.getAmount() * -1), rawData(item));
        queueBlockBreak(playerName, loc, type, data);
    }

    public void queueJoin(final Player player) {
        addToQueue(new PlayerJoinRow(player));
    }

    /**
     * @param killer
     *            Can' be null
     * @param victim
     *            Can' be null
     */
    public void queueKill(final Entity killer, final Entity victim) {
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
    public void queueKill(final Location location, final String killerName, final String victimName,
            final int weapon) {
        if (victimName == null || !isLogged(location.getWorld())) return;
        addToQueue(new KillRow(location, killerName == null ? null : killerName.replaceAll("[^a-zA-Z0-9_]",
                ""), victimName.replaceAll("[^a-zA-Z0-9_]", ""), weapon));
    }

    public void queueLeave(final Player player) {
        addToQueue(new PlayerLeaveRow(player));
    }

    /**
     * @param type
     *            Type of the sign. Must be 63 or 68.
     * @param lines
     *            The four lines on the sign.
     */
    public void queueSignBreak(final String playerName, final Location loc, final int type, final byte data,
            final String[] lines) {
        if (type != 63 && type != 68 || lines == null || lines.length != 4) return;
        queueBlock(playerName, loc, type, 0, data, lines[0] + "\0" + lines[1] + "\0" + lines[2] + "\0"
                + lines[3], null);
    }

    public void queueSignBreak(final String playerName, final Sign sign) {
        queueSignBreak(playerName, new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ()),
                sign.getTypeId(), sign.getRawData(), sign.getLines());
    }

    /**
     * @param type
     *            Type of the sign. Must be 63 or 68.
     * @param lines
     *            The four lines on the sign.
     */
    public void queueSignPlace(final String playerName, final Location loc, final int type, final byte data,
            final String[] lines) {
        if (type != 63 && type != 68 || lines == null || lines.length != 4) return;
        queueBlock(playerName, loc, 0, type, data, lines[0] + "\0" + lines[1] + "\0" + lines[2] + "\0"
                + lines[3], null);
    }

    public void queueSignPlace(final String playerName, final Sign sign) {
        queueSignPlace(playerName, new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ()),
                sign.getTypeId(), sign.getRawData(), sign.getLines());
    }

    @Override
    public void run() {
        if (queue.isEmpty() || !lock.tryLock()) return;
        final Connection conn = logblock.getConnection();
        Statement state = null;
        if (getQueueSize() > 1000)
            getLogger().info("[LogBlock Consumer] Queue overloaded. Size: " + getQueueSize());
        if (getQueueSize() >= dropQueueAfter) dropQueue();
        try {
            if (conn == null) return;
            conn.setAutoCommit(false);
            state = conn.createStatement();
            final long start = System.currentTimeMillis();
            int count = 0;
            process: while (!queue.isEmpty()
                    && (System.currentTimeMillis() - start < timePerRun || count < forceToProcessAtLeast)) {
                final Row r = queue.poll();
                if (r == null) continue;
                for (final String player : r.getPlayers())
                    if (!playerIds.containsKey(player)) if (!addPlayer(state, player)) {
                        if (!failedPlayers.contains(player)) {
                            failedPlayers.add(player);
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
            lock.unlock();
        }
    }

    public void writeToFile() throws FileNotFoundException {
        final long time = System.currentTimeMillis();
        final Set<String> insertedPlayers = new HashSet<String>();
        int counter = 0;
        new File("plugins/LogBlock/import/").mkdirs();
        PrintWriter writer = new PrintWriter(new File("plugins/LogBlock/import/queue-" + time + "-0.sql"));
        while (!queue.isEmpty()) {
            final Row r = queue.poll();
            if (r == null) continue;
            for (final String player : r.getPlayers())
                if (!playerIds.containsKey(player) && !insertedPlayers.contains(player)) {
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

    int getQueueSize() {
        return queue.size();
    }

    String playerID(final String playerName) {
        if (playerName == null) return "NULL";
        final Integer id = playerIds.get(playerName);
        if (id != null) return id.toString();
        return "(SELECT playerid FROM `lb-players` WHERE playername = '" + playerName + "')";
    }

    private boolean addPlayer(final Statement state, final String playerName) throws SQLException {
        state.execute("INSERT IGNORE INTO `lb-players` (playername) VALUES ('" + playerName + "')");
        final ResultSet rs = state.executeQuery("SELECT playerid FROM `lb-players` WHERE playername = '"
                + playerName + "'");
        if (rs.next()) playerIds.put(playerName, rs.getInt(1));
        rs.close();
        return playerIds.containsKey(playerName);
    }

    private void addToQueue(Row b) {
        queue.add(b);
        if (queue.size() >= dropQueueAfter) dropQueue();
    }

    private void dropQueue() {
        if (getQueueSize() > 0) {
            getLogger().info("[LogBlock] Dumping queue to files. Queue size " + getQueueSize());
            try {
                writeToFile();
                getLogger().info("Successfully dumped queue (queue size: " + getQueueSize() + ") \\o/");
            } catch (Exception ex) {
                getLogger().warning("Failed to write. Given up.");
            }
        }
    }

    private void queueBlock(final String playerName, final Location loc, final int typeBefore,
            final int typeAfter, final byte data, final String signtext, final ChestAccess ca) {
        if (playerName == null || loc == null || typeBefore < 0 || typeAfter < 0 || typeBefore > 255
                || typeAfter > 255 || hiddenPlayers.contains(playerName) || !isLogged(loc.getWorld())
                || typeBefore != typeAfter && hiddenBlocks.contains(typeBefore)
                && hiddenBlocks.contains(typeAfter)) return;
        addToQueue(new BlockRow(loc, playerName.replaceAll("[^a-zA-Z0-9_]", ""), typeBefore, typeAfter,
                data, signtext != null ? signtext.replace("\\", "\\\\").replace("'", "\\'") : null, ca));
    }
}
