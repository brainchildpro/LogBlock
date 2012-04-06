package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;

import de.diddiz.LogBlock.*;



public class SnowFormLogging extends LoggingListener {
    public SnowFormLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent event) {
        final Block b = event.getBlock();
        if (isLogging(b.getWorld(), Logging.SNOWFORM)) {
            final BlockState n = event.getNewState();
            final int type = n.getTypeId();
            if (type == 78 || type == 79) consumer.queueBlockReplace("SnowForm", b.getState(), n);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        if (isLogging(event.getBlock().getWorld(), Logging.SNOWFORM))
            this.consumer.queueBlockBreak("LeavesDecay", event.getBlock().getState());
    }
}
