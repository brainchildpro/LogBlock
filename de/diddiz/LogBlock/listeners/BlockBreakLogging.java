package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.isLogging;

import org.bukkit.*;
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
        if (wcfg == null || !wcfg.isLogging(Logging.BLOCKBREAK)) return;
        final String n = event.getPlayer().getName();
        final Block destroyedBlock = event.getBlock();
        BlockState before = destroyedBlock.getState();
        final int type = event.getBlock().getTypeId();

        if (wcfg.isLogging(Logging.SIGNTEXT) && (type == 63 || type == 68)) consumer.queueSignBreak(n, (Sign) before);
        else if (wcfg.isLogging(Logging.CHESTACCESS) && (type == 23 || type == 54 || type == 61)) consumer.queueContainerBreak(n, before);
        else if (type == 79) consumer.queueBlockReplace(n, before, 9, (byte) 0);
        else consumer.queueBlockBreak(n, before);

        // sand/gravel physics
        final World w = destroyedBlock.getWorld();
        for (int y1 = destroyedBlock.getY() + 1; y1 < w.getMaxHeight() - 1; y1++) {
            final Block up = w.getBlockAt(destroyedBlock.getX(), y1, destroyedBlock.getZ());
            int upID = up.getTypeId();
            if (upID != 12 && upID != 13) break;
            for (int y = y1 - 1; y > 0; y--) {
                final Block down = w.getBlockAt(destroyedBlock.getX(), y, destroyedBlock.getZ());
                final Block down1 = w.getBlockAt(down.getX(), down.getY() - 1, down.getZ());
                if (down.getLocation().equals(destroyedBlock.getLocation())) continue;
                final int id = down.getTypeId();
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
                if ((down.isLiquid() || id == 31 || id == 106) && !down1.isLiquid() && id1 != 106) consumer.queueBlockReplace(n, down.getState(), up.getState());

                if (wcfg.isLogging(Logging.BLOCKPLACE)) consumer.queueBlockPlace(n, w.getBlockAt(destroyedBlock.getX(), y + 1, destroyedBlock.getZ()).getLocation(), upID, up.getState().getRawData());
                consumer.queueBlockBreak(n, up.getState());
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketFill(final PlayerBucketFillEvent event) {
        final Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.BLOCKBREAK)) consumer.queueBlockBreak(p.getName(), event.getBlockClicked().getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getMaterial() != Material.FIRE || event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        final Player p = event.getPlayer();
        if (isLogging(p.getWorld(), Logging.BLOCKBREAK)) consumer.queueBlockBreak(p.getName(), event.getClickedBlock().getState());
    }
}
