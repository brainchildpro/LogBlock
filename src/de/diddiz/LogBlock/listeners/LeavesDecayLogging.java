package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.LeavesDecayEvent;

import de.diddiz.LogBlock.*;

public class LeavesDecayLogging extends LoggingListener {
    public LeavesDecayLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!event.isCancelled() && isLogging(event.getBlock().getWorld(), Logging.LEAVESDECAY))
            this.consumer.queueBlockBreak("LeavesDecay", event.getBlock().getState());
    }
}
