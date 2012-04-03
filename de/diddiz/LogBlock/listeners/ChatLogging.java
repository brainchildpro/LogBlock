package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.entity.Player;
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
        Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.CHAT)) this.consumer.queueChat(p.getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.CHAT)) this.consumer.queueChat(p.getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(final ServerCommandEvent event) {
        this.consumer.queueChat("Console", "/" + event.getCommand());
    }
}
