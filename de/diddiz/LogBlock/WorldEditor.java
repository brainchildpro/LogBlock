package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.config.Config.dontRollback;
import static de.diddiz.LogBlock.config.Config.replaceAnyway;
import static de.diddiz.util.BukkitUtils.equalTypes;
import static de.diddiz.util.BukkitUtils.modifyContainer;
import static de.diddiz.util.MaterialName.materialName;
import static org.bukkit.Bukkit.getLogger;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.*;

public class WorldEditor implements Runnable {
    @SuppressWarnings("serial")
    public static class WorldEditorException extends Exception implements LookupCacheElement {
        private final Location loc;

        public WorldEditorException(int typeBefore, int typeAfter, Location loc) {
            this("Failed to replace " + materialName(typeBefore) + " with " + materialName(typeAfter), loc);
        }

        public WorldEditorException(String msg, Location loc) {
            super(msg + " at " + loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY()
                    + ":" + loc.getBlockZ());
            this.loc = loc;
        }

        @Override
        public Location getLocation() {
            return this.loc;
        }
    }

    private class Edit extends BlockChange {
        public Edit(long time, Location loc, String playerName, int replaced, int type, byte data,
                String signtext, ChestAccess ca) {
            super(time, loc, playerName, replaced, type, data, signtext, ca);
        }

        PerformResult perform() throws WorldEditorException {
            if (dontRollback.contains(this.replaced)) return PerformResult.BLACKLISTED;
            final Block block = this.loc.getBlock();
            if (this.replaced == 0 && block.getTypeId() == 0) return PerformResult.NO_ACTION;
            final BlockState state = block.getState();
            if (!WorldEditor.this.world.isChunkLoaded(block.getChunk()))
                WorldEditor.this.world.loadChunk(block.getChunk());
            if (this.type == this.replaced) {
                if (this.type == 0) {
                    if (!block.setTypeId(0))
                        throw new WorldEditorException(block.getTypeId(), 0, block.getLocation());
                } else if (this.ca != null
                        && (this.type == 23 || this.type == 54 || this.type == 61 || this.type == 62)) {
                    int leftover = 0;
                    try {
                        leftover = modifyContainer(state, new ItemStack(this.ca.itemType,
                                -this.ca.itemAmount, (short) 0, this.ca.itemData));
                        if (leftover > 0)
                            for (final BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH,
                                    BlockFace.EAST, BlockFace.WEST })
                                if (block.getRelative(face).getTypeId() == 54)
                                    leftover = modifyContainer(block.getRelative(face).getState(),
                                            new ItemStack(this.ca.itemType,
                                                    this.ca.itemAmount < 0 ? leftover : -leftover,
                                                    (short) 0, this.ca.itemData));
                    } catch (final Exception ex) {
                        throw new WorldEditorException(ex.getMessage(), block.getLocation());
                    }
                    if (!state.update())
                        throw new WorldEditorException("Failed to update inventory of "
                                + materialName(block.getTypeId()), block.getLocation());
                    if (leftover > 0 && this.ca.itemAmount < 0)
                        throw new WorldEditorException("Not enough space left in "
                                + materialName(block.getTypeId()), block.getLocation());
                } else return PerformResult.NO_ACTION;
                return PerformResult.SUCCESS;
            }
            if (!(equalTypes(block.getTypeId(), this.type) || replaceAnyway.contains(block.getTypeId())))
                return PerformResult.NO_ACTION;
            if (state instanceof ContainerBlock) {
                ((ContainerBlock) state).getInventory().clear();
                state.update();
            }
            if (block.getTypeId() == this.replaced) {
                if (block.getData() != (this.type == 0 ? this.data : (byte) 0))
                    block.setData(this.type == 0 ? this.data : (byte) 0, true);
                else return PerformResult.NO_ACTION;
            } else if (!block.setTypeIdAndData(this.replaced, this.type == 0 ? this.data : (byte) 0, true))
                throw new WorldEditorException(block.getTypeId(), this.replaced, block.getLocation());
            final int curtype = block.getTypeId();
            if (this.signtext != null && (curtype == 63 || curtype == 68)) {
                final Sign sign = (Sign) block.getState();
                final String[] lines = this.signtext.split("\0", 4);
                if (lines.length < 4) return PerformResult.NO_ACTION;
                for (int i = 0; i < 4; i++)
                    sign.setLine(i, lines[i]);
                if (!sign.update())
                    throw new WorldEditorException("Failed to update signtext of "
                            + materialName(block.getTypeId()), block.getLocation());
            } else if (curtype == 26) {
                final Bed bed = (Bed) block.getState().getData();
                final Block secBlock = bed.isHeadOfBed() ? block.getRelative(bed.getFacing()
                        .getOppositeFace()) : block.getRelative(bed.getFacing());
                if (secBlock.getTypeId() == 0
                        && !secBlock.setTypeIdAndData(26, (byte) (bed.getData() | 8), true))
                    throw new WorldEditorException(secBlock.getTypeId(), 26, secBlock.getLocation());
            } else if (curtype == 64 || curtype == 71) {
                final byte blockData = block.getData();
                final Block secBlock = (blockData & 8) == 8 ? block.getRelative(BlockFace.DOWN) : block
                        .getRelative(BlockFace.UP);
                if (secBlock.getTypeId() == 0
                        && !secBlock.setTypeIdAndData(curtype, (byte) (blockData | 8), true))
                    throw new WorldEditorException(secBlock.getTypeId(), curtype, secBlock.getLocation());
            } else if ((curtype == 29 || curtype == 33) && (block.getData() & 8) > 0) {
                final PistonBaseMaterial piston = (PistonBaseMaterial) block.getState().getData();
                final Block secBlock = block.getRelative(piston.getFacing());
                if (secBlock.getTypeId() == 0
                        && !secBlock.setTypeIdAndData(34, curtype == 29 ? (byte) (block.getData() | 8)
                                : (byte) (block.getData() & ~8), true))
                    throw new WorldEditorException(secBlock.getTypeId(), 34, secBlock.getLocation());
            } else if (curtype == 34) {
                final PistonExtensionMaterial piston = (PistonExtensionMaterial) block.getState().getData();
                final Block secBlock = block.getRelative(piston.getFacing().getOppositeFace());
                if (secBlock.getTypeId() == 0
                        && !secBlock.setTypeIdAndData(piston.isSticky() ? 29 : 33,
                                (byte) (block.getData() | 8), true))
                    throw new WorldEditorException(secBlock.getTypeId(), piston.isSticky() ? 29 : 33,
                            secBlock.getLocation());
            } else if (curtype == 18 && (block.getData() & 8) > 0)
                block.setData((byte) (block.getData() & 0xF7));
            return PerformResult.SUCCESS;
        }
    }

    private static enum PerformResult {
        SUCCESS, BLACKLISTED, NO_ACTION
    }

    private final LogBlock logblock;
    private final Queue<Edit> edits = new LinkedBlockingQueue<Edit>();
    final World world;
    private int taskID;

    private int successes = 0, blacklistCollisions = 0;

    private long elapsedTime = 0;

    public LookupCacheElement[] errors;

    public WorldEditor(LogBlock logblock, World world) {
        this.logblock = logblock;
        this.world = world;
    }

    public int getBlacklistCollisions() {
        return this.blacklistCollisions;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    public int getErrors() {
        return this.errors.length;
    }

    public int getSize() {
        return this.edits.size();
    }

    public int getSuccesses() {
        return this.successes;
    }

    public void queueEdit(int x, int y, int z, int replaced, int type, byte data, String signtext,
            short itemType, short itemAmount, byte itemData) {
        this.edits.add(new Edit(0, new Location(this.world, x, y, z), null, replaced, type, data, signtext,
                new ChestAccess(itemType, itemAmount, itemData)));
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public synchronized void run() {
        final List<WorldEditorException> errorList = new ArrayList<WorldEditorException>();
        int counter = 0;
        while (!this.edits.isEmpty() && counter < 100) {
            try {
                switch (this.edits.poll().perform()) {
                case SUCCESS:
                    this.successes++;
                    break;
                case BLACKLISTED:
                    this.blacklistCollisions++;
                    break;
                }
            } catch (final WorldEditorException ex) {
                errorList.add(ex);
            } catch (final Exception ex) {
                getLogger().log(Level.WARNING, "[LogBlock WorldEditor] Exeption: ", ex);
            }
            counter++;
        }
        if (this.edits.isEmpty()) {
            this.logblock.getServer().getScheduler().cancelTask(this.taskID);
            if (errorList.size() > 0)
                try {
                    final File file = new File("plugins/LogBlock/error/WorldEditor-"
                            + new SimpleDateFormat("yy-MM-dd-HH-mm-ss").format(System.currentTimeMillis())
                            + ".log");
                    file.getParentFile().mkdirs();
                    final PrintWriter writer = new PrintWriter(file);
                    for (final LookupCacheElement err : errorList)
                        writer.println(err.getMessage());
                    writer.close();
                } catch (final Exception ex) {
                }
            this.errors = errorList.toArray(new WorldEditorException[errorList.size()]);
            notify();
        }
    }

    synchronized public void start() throws Exception {
        final long start = System.currentTimeMillis();
        this.taskID = this.logblock.getServer().getScheduler()
                .scheduleSyncRepeatingTask(this.logblock, this, 0, 1);
        if (this.taskID == -1) throw new Exception("Failed to schedule task");
        try {
            this.wait();
        } catch (final InterruptedException ex) {
            throw new Exception("Interrupted");
        }
        this.elapsedTime = System.currentTimeMillis() - start;
    }
}
