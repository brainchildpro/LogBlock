package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;

import java.util.*;

import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockFromToEvent;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;

public class FluidFlowLogging extends LoggingListener {
    private static final Set<Integer> nonFluidProofBlocks = new HashSet<Integer>(Arrays.asList(27, 28, 31,
            32, 37, 38, 39, 40, 50, 51, 55, 59, 66, 69, 70, 75, 76, 78, 93, 94, 104, 105, 106));

    private static boolean isSurroundedByWater(final Block block) {
        for (final BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST,
                BlockFace.SOUTH }) {
            final int type = block.getRelative(face).getTypeId();
            if (type == 8 || type == 9) return true;
        }
        return false;
    }

    public FluidFlowLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(final BlockFromToEvent event) {
        final WorldConfig wcfg = getWorldConfig(event.getBlock().getWorld());
        if (wcfg != null) {
            final Block to = event.getToBlock();
            final int typeFrom = event.getBlock().getTypeId();
            final int typeTo = to.getTypeId();
            final boolean canFlow = typeTo == 0 || nonFluidProofBlocks.contains(typeTo);
            if (typeFrom == 10 || typeFrom == 11) {
                if (canFlow) {
                    if (isSurroundedByWater(to) && event.getBlock().getData() <= 2)
                        this.consumer.queueBlockReplace("LavaFlow", to.getState(), 4, (byte) 0);
                    else if (typeTo == 0) {
                        if (wcfg.isLogging(Logging.LAVAFLOW))
                            this.consumer.queueBlockPlace("LavaFlow", to.getLocation(), 10, (byte) (event
                                    .getBlock().getData() + 1));
                    } else this.consumer.queueBlockReplace("LavaFlow", to.getState(), 10, (byte) (event
                            .getBlock().getData() + 1));
                } else if (typeTo == 8 || typeTo == 9) if (event.getFace() == BlockFace.DOWN)
                    this.consumer.queueBlockReplace("LavaFlow", to.getState(), 1, (byte) 0);
                else this.consumer.queueBlockReplace("LavaFlow", to.getState(), 4, (byte) 0);
            } else if (typeFrom == 8 || typeFrom == 9) {
                if (typeTo == 0) {
                    if (wcfg.isLogging(Logging.WATERFLOW))
                        this.consumer.queueBlockPlace("WaterFlow", to.getLocation(), 8, (byte) (event
                                .getBlock().getData() + 1));
                } else if (nonFluidProofBlocks.contains(typeTo))
                    this.consumer.queueBlockReplace("WaterFlow", to.getState(), 8, (byte) (event.getBlock()
                            .getData() + 1));
                else if (typeTo == 10 || typeTo == 11)
                    if (to.getData() == 0)
                        this.consumer.queueBlockReplace("WaterFlow", to.getState(), 49, (byte) 0);
                    else if (event.getFace() == BlockFace.DOWN)
                        this.consumer.queueBlockReplace("LavaFlow", to.getState(), 1, (byte) 0);
                if (typeTo == 0 || nonFluidProofBlocks.contains(typeTo))
                    for (final BlockFace face : new BlockFace[] { BlockFace.DOWN, BlockFace.NORTH,
                            BlockFace.WEST, BlockFace.EAST, BlockFace.SOUTH }) {
                        final Block lower = to.getRelative(face);
                        if (lower.getTypeId() == 10 || lower.getTypeId() == 11)
                            this.consumer.queueBlockReplace("WaterFlow", lower.getState(),
                                    lower.getData() == 0 ? 49 : 4, (byte) 0);
                    }
            }
        }
    }
}
