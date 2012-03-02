package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.BlockBurnEvent;

import de.diddiz.LogBlock.*;

public class BlockBurnLogging extends LoggingListener {
    public BlockBurnLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!event.isCancelled() && isLogging(event.getBlock().getWorld(), Logging.FIRE))
            this.consumer.queueBlockBreak("Fire", event.getBlock().getState());
    }
}
