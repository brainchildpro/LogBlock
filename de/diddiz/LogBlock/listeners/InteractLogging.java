package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;

public class InteractLogging extends LoggingListener {
    public InteractLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        final WorldConfig wcfg = getWorldConfig(p.getWorld());
        if (wcfg != null && (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            final int type = event.getClickedBlock().getTypeId();
            final String name = p.getName();
            final Location loc = event.getClickedBlock().getLocation();
            switch (type) {
            case 69:
            case 77:
                if (wcfg.isLogging(Logging.SWITCHINTERACT)) consumer.queueBlock(name, loc, type, type, (byte) 0);
                break;
            case 107:
                if (event.getAction() != Action.RIGHT_CLICK_BLOCK) break;
                break;
            case 64:
            case 96:
                if (wcfg.isLogging(Logging.DOORINTERACT)) consumer.queueBlock(name, loc, type, type, (byte) ((event.getClickedBlock().getData() & 4) / 4));
                break;
            case 92:
                if (wcfg.isLogging(Logging.CAKEEAT) && p.getFoodLevel() < 20) consumer.queueBlock(name, loc, 92, 92, (byte) 0);
                break;
            case 25:
                if (wcfg.isLogging(Logging.NOTEBLOCKINTERACT)) consumer.queueBlock(name, loc, 25, 25, (byte) 0);
                break;
            case 93:
            case 94:
                if (wcfg.isLogging(Logging.DIODEINTERACT) && event.getAction() == Action.RIGHT_CLICK_BLOCK) consumer.queueBlock(name, loc, type, type, (byte) 0);
                break;
            }
        }
    }
}
