package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.block.Block;
import org.bukkit.entity.Enderman;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import de.diddiz.LogBlock.*;



public class EndermenLogging extends LoggingListener {
    public EndermenLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        final Block b = event.getBlock();
        if (event.getEntity() instanceof Enderman && isLogging(b.getWorld(), Logging.ENDERMEN))
            consumer.queueBlockReplace("Enderman", b.getState(), event.getTo().getId(), (byte) 0);
    }
}
