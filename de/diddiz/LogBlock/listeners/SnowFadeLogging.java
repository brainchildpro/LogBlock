package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockFadeEvent;

import de.diddiz.LogBlock.*;



public class SnowFadeLogging extends LoggingListener {
    public SnowFadeLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(final BlockFadeEvent event) {
        final Block b = event.getBlock();
        if (isLogging(b.getWorld(), Logging.SNOWFADE)) {
            final int type = b.getTypeId();
            if (type == 78 || type == 79)
                consumer.queueBlockReplace("SnowFade", b.getState(), event.getNewState());
        }
    }
}
