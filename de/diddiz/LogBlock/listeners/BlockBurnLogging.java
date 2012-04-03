package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBurnEvent;

import de.diddiz.LogBlock.*;

public class BlockBurnLogging extends LoggingListener {
    public BlockBurnLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(final BlockBurnEvent event) {
        Block b = event.getBlock();
        if (isLogging(b.getWorld(), Logging.FIRE)) this.consumer.queueBlockBreak("Fire", b.getState());
    }
}
