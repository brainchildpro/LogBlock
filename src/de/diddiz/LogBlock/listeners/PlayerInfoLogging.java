package de.diddiz.LogBlock.listeners;

import org.bukkit.event.*;
import org.bukkit.event.player.*;

import de.diddiz.LogBlock.LogBlock;

public class PlayerInfoLogging extends LoggingListener {
    public PlayerInfoLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.consumer.queueJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.consumer.queueLeave(event.getPlayer());
    }
}
