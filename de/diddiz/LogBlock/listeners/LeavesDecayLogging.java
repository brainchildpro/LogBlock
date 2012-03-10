package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.LeavesDecayEvent;

import de.diddiz.LogBlock.*;

public class LeavesDecayLogging extends LoggingListener {
    public LeavesDecayLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        if (isLogging(event.getBlock().getWorld(), Logging.LEAVESDECAY))
            this.consumer.queueBlockBreak("LeavesDecay", event.getBlock().getState());
    }
}
