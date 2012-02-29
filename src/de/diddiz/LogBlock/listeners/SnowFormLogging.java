package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.*;

import de.diddiz.LogBlock.*;

public class SnowFormLogging extends LoggingListener {
    public SnowFormLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockForm(BlockFormEvent event) {
        if (!event.isCancelled() && isLogging(event.getBlock().getWorld(), Logging.SNOWFORM)) {
            final int type = event.getNewState().getTypeId();
            if (type == 78 || type == 79)
                this.consumer
                        .queueBlockReplace("SnowForm", event.getBlock().getState(), event.getNewState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!event.isCancelled() && isLogging(event.getBlock().getWorld(), Logging.SNOWFORM))
            this.consumer.queueBlockBreak("LeavesDecay", event.getBlock().getState());
    }
}
