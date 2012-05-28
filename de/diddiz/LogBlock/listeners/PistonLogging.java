package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.*;
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
            for (Block pushed : event.getBlocks()) {
                if (pushed.getPistonMoveReaction() == PistonMoveReaction.BLOCK) continue;
                final Location from = pushed.getLocation();
                final Location to = new Location(from.getWorld(), from.getX() + direction.getModX(),
                        from.getY() + direction.getModY(), from.getZ() + direction.getModZ());
                final BlockState fromBlock = from.getBlock().getState();
                final BlockState toBlock = to.getBlock().getState();
                consumer.queueBlockBreak("Piston", fromBlock);
                if (pushed.getPistonMoveReaction() == PistonMoveReaction.MOVE)
                    consumer.queueBlockReplace("Piston", toBlock, fromBlock);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;
        final Block b = event.getBlock();
        if (b.getPistonMoveReaction() != PistonMoveReaction.MOVE) return;
        final World w = b.getWorld();
        if (isLogging(w, Logging.PISTONRETRACT)) {
            final BlockFace direction = event.getDirection();
            final Location from = event.getRetractLocation();
            final Location to = new Location(from.getWorld(), from.getX() - direction.getModX(), from.getY() - direction.getModY(), from.getZ() - direction.getModZ());
            final BlockState fromBlock = from.getBlock().getState();
            final BlockState toBlock = to.getBlock().getState();
            consumer.queueBlockBreak("Piston", fromBlock);
            consumer.queueBlockReplace("Piston", toBlock, fromBlock);
        }
    }

}
