package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.entity.Enderman;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import de.diddiz.LogBlock.*;

public class EndermenLogging extends LoggingListener {
    public EndermenLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Enderman
                && isLogging(event.getBlock().getWorld(), Logging.ENDERMEN))
            this.consumer.queueBlockReplace("Enderman", event.getBlock().getState(), event.getTo().getId(),
                    (byte) 0); // Figure out how to get the data of the placed
                               // block;
    }
}
