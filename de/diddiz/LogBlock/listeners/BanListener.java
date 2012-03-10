package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.banPermission;
import static de.diddiz.LogBlock.config.Config.isLogged;
import static org.bukkit.Bukkit.getScheduler;

import org.bukkit.World;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import de.diddiz.LogBlock.*;

public class BanListener implements Listener {
    final CommandsHandler handler;
    final LogBlock logblock;

    public BanListener(final LogBlock logblock) {
        this.logblock = logblock;
        this.handler = logblock.getCommandsHandler();
    }

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final String[] split = event.getMessage().split(" ");
        if (split.length > 1 && split[0].equalsIgnoreCase("/ban")
                && this.logblock.hasPermission(event.getPlayer(), banPermission)) {
            final QueryParams p = new QueryParams(this.logblock);
            p.setPlayer(split[1].equalsIgnoreCase("g") ? split[2] : split[1]);
            p.since = 0;
            p.silent = false;
            getScheduler().scheduleAsyncDelayedTask(this.logblock, new Runnable() {
                @Override
                public void run() {
                    for (final World world : BanListener.this.logblock.getServer().getWorlds())
                        if (isLogged(world)) {
                            p.world = world;
                            try {
                                BanListener.this.handler.new CommandRollback(event.getPlayer(), p, false);
                            } catch (final Exception ex) {
                            }
                        }
                }
            });
        }
    }
}
