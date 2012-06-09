package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import java.util.List;

import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;

import de.diddiz.LogBlock.*;

public class PistonLogging extends LoggingListener {

    public PistonLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        final Block b = event.getBlock();
        final BlockFace direction = event.getDirection();
        if (isLogging(b.getWorld(), Logging.PISTONEXTEND)) {
            List<Block> blocks = event.getBlocks();
            for (Block pushed : blocks) {
                if (pushed.getTypeId() == 0 || pushed.getPistonMoveReaction() == PistonMoveReaction.BLOCK) continue;
                final BlockState fromBlock = pushed.getState();
                final BlockState toBlock = pushed.getRelative(direction).getState();

                consumer.queueBlockBreak("Piston", fromBlock);
                if (pushed.getPistonMoveReaction() == PistonMoveReaction.MOVE)
                    consumer.queueBlockReplace("Piston", toBlock, fromBlock);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;
        final Block b = event.getRetractLocation().getBlock();
        if (b.getTypeId() == 0) return;
        if (b.getPistonMoveReaction() != PistonMoveReaction.MOVE) return;
        final World w = b.getWorld();
        if (isLogging(w, Logging.PISTONRETRACT)) {
            final BlockFace direction = event.getDirection();
            final BlockState fromBlock = b.getState();
            final BlockState toBlock = b.getRelative(direction.getOppositeFace()).getState();
            consumer.queueBlockBreak("Piston", fromBlock);
            consumer.queueBlockPlace("Piston", toBlock.getLocation(), fromBlock.getTypeId(),
                    fromBlock.getRawData());
        }
    }

}
