package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.material.MaterialData;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;



public class BlockPlaceLogging extends LoggingListener {
    public BlockPlaceLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final Block b = event.getBlock();
        final WorldConfig wcfg = getWorldConfig(b.getWorld());
        if (wcfg != null && wcfg.isLogging(Logging.BLOCKPLACE)) {
            final int type = b.getTypeId();
            final BlockState before = event.getBlockReplacedState();
            final BlockState after = event.getBlockPlaced().getState();
            if (type == 0 && event.getItemInHand() != null) {
                if (event.getItemInHand().getTypeId() == 51) return;
                after.setTypeId(event.getItemInHand().getTypeId());
                after.setData(new MaterialData(event.getItemInHand().getTypeId()));
            }
            if (wcfg.isLogging(Logging.SIGNTEXT) && (type == 63 || type == 68)) return;
            final String name = event.getPlayer().getName();
            if (before.getTypeId() == 0)
                consumer.queueBlockPlace(name, after);
            else consumer.queueBlockReplace(name, before, after);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event) {
        final Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.BLOCKPLACE))
            consumer.queueBlockPlace(p.getName(), event.getBlockClicked().getRelative(event.getBlockFace())
                    .getLocation(), event.getBucket() == Material.WATER_BUCKET ? 9 : 11, (byte) 0);
    }
}
