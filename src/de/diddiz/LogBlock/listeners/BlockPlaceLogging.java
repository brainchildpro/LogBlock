package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.material.MaterialData;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;

public class BlockPlaceLogging extends LoggingListener {
    public BlockPlaceLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        final WorldConfig wcfg = getWorldConfig(event.getBlock().getWorld());
        if (!event.isCancelled() && wcfg != null && wcfg.isLogging(Logging.BLOCKPLACE)) {
            final int type = event.getBlock().getTypeId();
            final BlockState before = event.getBlockReplacedState();
            final BlockState after = event.getBlockPlaced().getState();
            if (type == 0 && event.getItemInHand() != null) {
                if (event.getItemInHand().getTypeId() == 51) return;
                after.setTypeId(event.getItemInHand().getTypeId());
                after.setData(new MaterialData(event.getItemInHand().getTypeId()));
            }
            if (wcfg.isLogging(Logging.SIGNTEXT) && (type == 63 || type == 68)) return;
            if (before.getTypeId() == 0)
                this.consumer.queueBlockPlace(event.getPlayer().getName(), after);
            else this.consumer.queueBlockReplace(event.getPlayer().getName(), before, after);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!event.isCancelled() && isLogging(event.getPlayer().getWorld(), Logging.BLOCKPLACE))
            this.consumer.queueBlockPlace(event.getPlayer().getName(),
                    event.getBlockClicked().getRelative(event.getBlockFace()).getLocation(),
                    event.getBucket() == Material.WATER_BUCKET ? 9 : 11, (byte) 0);
    }
}
