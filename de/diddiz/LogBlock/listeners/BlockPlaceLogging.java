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
            final int placedType = b.getTypeId();
            BlockState before_dyn = event.getBlockReplacedState();
            final BlockState before = event.getBlockReplacedState();
            final BlockState after = event.getBlockPlaced().getState();
            final boolean placed = before.getTypeId() == 0;
            final ItemStack inHand = event.getItemInHand();
            final int type = inHand.getTypeId();
            final String name = event.getPlayer().getName();
            if (placedType == 0 && inHand != null) {
                if (type == 51) return;
                after.setTypeId(inHand.getTypeId());
                after.setData(new MaterialData(inHand.getTypeId()));
            }
            // Delay queueing of stairs and blocks by 1 tick to allow the raw data to update
            if (type == 53 || type == 67 || type == 108 || type == 109 || type == 114 || type == 128 || type == 134 || type == 135 || type == 136 || type == 26) {
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(LogBlock.getInstance(), new Runnable() {
                    @Override
                    public void run() {
                        if (before.getTypeId() == 0) consumer.queueBlockPlace(name, after);
                        else consumer.queueBlockReplace(name, before, after);
                    }
                }, 1L);
                return;
            }
            if (wcfg.isLogging(Logging.SIGNTEXT) && (placedType == 63 || placedType == 68)) return;
            if (type == 12 || type == 13) { // sand/gravel physics
                final World w = b.getWorld();
                for (int y = before.getY() - 1; y > 0; y--) {
                    final Block down = w.getBlockAt(b.getX(), y, b.getZ());
                    final Block down1 = w.getBlockAt(down.getX(), down.getY() - 1, down.getZ());
                    int id = down.getTypeId();
                    final int id1 = down1.getTypeId();
                    // did the sand fall on a block that destroys it? and did the sand fall more than one block, and is there a block below it?
                    // I don't like having to check all of these ids, but there's no other way that I know of
                    if ((id == 75 || id == 76 || id == 50 || id == 59 || id == 70 || id == 72 || id == 63 || id == 69 || id == 68 || id == 27 || id == 66 || id == 44 || id == 107 || id >= 37 && id <= 40 || id == 77 || id == 55
                            || id == 6 || id == 83 || id == 90 || id == 111 || id >= 92 && id <= 94 || id == 96 || id == 104 || id == 105 || id == 115)
                            && before.getY() - y > 1) {
                        if (id1 != 0
                                && !(id1 == 75 || id1 == 76 || id1 == 50 || id1 == 59 || id1 == 70 || id1 == 72 || id1 == 63 || id1 == 69 || id1 == 68 || id1 == 27 || id1 == 66 || id1 == 44 || id1 == 107 || id1 >= 37 && id1 <= 40
                                        || id1 == 77 || id1 == 55 || id1 == 6 || id1 == 83 || id1 == 90 || id1 == 111 || id1 >= 92 && id1 <= 94 || id1 == 96 || id1 == 104 || id1 == 105 || id1 == 115)) return;
                        continue; // sand falls through torches
                    }
                    if (id == 0) continue;
                    if ((down.isLiquid() || id == 31 || id == 106) && !down1.isLiquid() && id1 != 106) consumer.queueBlockReplace(name, down.getState(), after);
                    before_dyn = w.getBlockAt(b.getX(), y + 1, b.getZ()).getState();
                    break;
                }
            }
            if (placed) consumer.queueBlockPlace(name, before_dyn.getLocation(), after.getTypeId(), after.getRawData());
            else consumer.queueBlockReplace(name, before_dyn, after);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event) {
        final Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.BLOCKPLACE))
            consumer.queueBlockPlace(p.getName(), event.getBlockClicked().getRelative(event.getBlockFace()).getLocation(), event.getBucket() == Material.WATER_BUCKET ? 9 : 11, (byte) 0);
    }
}
