package de.diddiz.LogBlock.listeners;

import org.bukkit.event.*;
import org.bukkit.event.player.*;

import de.diddiz.LogBlock.LogBlock;

public class PlayerInfoLogging extends LoggingListener {
    public PlayerInfoLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        consumer.queueJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        consumer.queueLeave(event.getPlayer());
    }
}
