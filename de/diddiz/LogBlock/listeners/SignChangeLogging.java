package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;

import de.diddiz.LogBlock.*;

public class SignChangeLogging extends LoggingListener {
    public SignChangeLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(final SignChangeEvent event) {
        final Block b = event.getBlock();
        final Player p = event.getPlayer();
        if (isLogging(b.getWorld(), Logging.SIGNTEXT)) consumer.queueSignPlace(p.getName(), b.getLocation(), b.getTypeId(), b.getData(), event.getLines());
    }
}
