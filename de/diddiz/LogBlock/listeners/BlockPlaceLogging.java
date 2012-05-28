package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
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
            final int beforeType = b.getTypeId();
            BlockState before = event.getBlockReplacedState();
            final BlockState after = event.getBlockPlaced().getState();
            final boolean placed = before.getTypeId() == 0;
            final ItemStack inHand = event.getItemInHand();
            final int handTypeId = inHand.getTypeId();
            if (beforeType == 0 && inHand != null) {
                if (handTypeId == 51) return;
                after.setTypeId(inHand.getTypeId());
                after.setData(new MaterialData(inHand.getTypeId()));
            }
            if (wcfg.isLogging(Logging.SIGNTEXT) && (beforeType == 63 || beforeType == 68)) return;
            final String name = event.getPlayer().getName();
            if (placed && (handTypeId == 12 || handTypeId == 13)) { // sand/gravel physics
                final World w = b.getWorld();
                for (int i = before.getY(); i > 0; i--) {
                    final Block down = w.getBlockAt(b.getX(), i, b.getZ());
                    if (down.getTypeId() == 0) continue;
                    before = down.getState();
                    break;
                }
            }
            if (placed) consumer.queueBlockPlace(name, after);
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
