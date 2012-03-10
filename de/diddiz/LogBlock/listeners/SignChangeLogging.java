package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;

import de.diddiz.LogBlock.*;

public class SignChangeLogging extends LoggingListener {
    public SignChangeLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(final SignChangeEvent event) {
        if (isLogging(event.getBlock().getWorld(), Logging.SIGNTEXT))
            this.consumer.queueSignPlace(event.getPlayer().getName(), event.getBlock().getLocation(), event
                    .getBlock().getTypeId(), event.getBlock().getData(), event.getLines());
    }
}
