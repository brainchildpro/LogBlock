package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.*;

import de.diddiz.LogBlock.*;

public class SnowFormLogging extends LoggingListener {
    public SnowFormLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent event) {
        if (isLogging(event.getBlock().getWorld(), Logging.SNOWFORM)) {
            final int type = event.getNewState().getTypeId();
            if (type == 78 || type == 79)
                this.consumer
                        .queueBlockReplace("SnowForm", event.getBlock().getState(), event.getNewState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        if (isLogging(event.getBlock().getWorld(), Logging.SNOWFORM))
            this.consumer.queueBlockBreak("LeavesDecay", event.getBlock().getState());
    }
}
