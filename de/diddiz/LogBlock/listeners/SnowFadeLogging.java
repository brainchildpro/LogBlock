package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.BlockFadeEvent;

import de.diddiz.LogBlock.*;

public class SnowFadeLogging extends LoggingListener {
    public SnowFadeLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFade(BlockFadeEvent event) {
        if (!event.isCancelled() && isLogging(event.getBlock().getWorld(), Logging.SNOWFADE)) {
            final int type = event.getBlock().getTypeId();
            if (type == 78 || type == 79)
                this.consumer
                        .queueBlockReplace("SnowFade", event.getBlock().getState(), event.getNewState());
        }
    }
}
