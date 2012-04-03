package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;

public class BlockBreakLogging extends LoggingListener {
    public BlockBreakLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final WorldConfig wcfg = getWorldConfig(event.getBlock().getWorld());
        if (wcfg != null && wcfg.isLogging(Logging.BLOCKBREAK)) {
            final String n = event.getPlayer().getName();
            final Block block = event.getBlock();
            final BlockState bs = block.getState();
            final int type = event.getBlock().getTypeId();
            if (wcfg.isLogging(Logging.SIGNTEXT) && (type == 63 || type == 68))
                consumer.queueSignBreak(n, (Sign) bs);
            else if (wcfg.isLogging(Logging.CHESTACCESS) && (type == 23 || type == 54 || type == 61))
                consumer.queueContainerBreak(n, bs);
            else if (type == 79)
                consumer.queueBlockReplace(n, bs, 9, (byte) 0);
            else consumer.queueBlockBreak(n, bs);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketFill(final PlayerBucketFillEvent event) {
        final Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.BLOCKBREAK))
            consumer.queueBlockBreak(p.getName(), event.getBlockClicked().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getMaterial() != Material.FIRE) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        final Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.BLOCKBREAK))
            consumer.queueBlockBreak(p.getName(), event.getClickedBlock().getState());
    }
}
