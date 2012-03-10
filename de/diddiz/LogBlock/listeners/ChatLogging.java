package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;

import de.diddiz.LogBlock.*;

public class ChatLogging extends LoggingListener {
    public ChatLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(final PlayerChatEvent event) {
        if (isLogging(event.getPlayer().getWorld(), Logging.CHAT))
            this.consumer.queueChat(event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (isLogging(event.getPlayer().getWorld(), Logging.CHAT))
            this.consumer.queueChat(event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(final ServerCommandEvent event) {
        this.consumer.queueChat("Console", "/" + event.getCommand());
    }
}
