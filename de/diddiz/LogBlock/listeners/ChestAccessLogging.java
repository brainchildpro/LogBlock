package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;
import static de.diddiz.util.BukkitUtils.*;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

import de.diddiz.LogBlock.*;

public class ChestAccessLogging extends LoggingListener {
    private static class ContainerState {
        public final ItemStack[] items;
        public final Location loc;

        ContainerState(final Location loc, final ItemStack[] items) {
            this.items = items;
            this.loc = loc;
        }
    }

    private final Map<Player, ContainerState> containers = new HashMap<Player, ContainerState>();

    public ChestAccessLogging(final LogBlock lb) {
        super(lb);
    }

    public void checkInventoryClose(final Player player) {
        final ContainerState cont = this.containers.get(player);
        if (cont == null) return;
        final ItemStack[] before = cont.items;
        final BlockState state = cont.loc.getBlock().getState();
        if (!(state instanceof InventoryHolder)) return;
        final ItemStack[] after = compressInventory(((InventoryHolder) state).getInventory().getContents());
        final ItemStack[] diff = compareInventories(before, after);
        for (final ItemStack item : diff)
            consumer.queueChestAccess(player.getName(), cont.loc, state.getTypeId(),
                    (short) item.getTypeId(), (short) item.getAmount(), rawData(item));
        containers.remove(player);
    }

    public void checkInventoryOpen(final Player player, final Block block) {
        final BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            ContainerState c = new ContainerState(block.getLocation(),
                    compressInventory(((InventoryHolder) state).getInventory().getContents()));
            if (containers.containsValue(c)) return;
            containers.put(player, c);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(final PlayerChatEvent event) {
        checkInventoryClose(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        checkInventoryClose(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        checkInventoryClose(p);
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isLogging(p.getWorld(), Logging.CHESTACCESS)) {
            final Block block = event.getClickedBlock();
            final int type = block.getTypeId();
            if (type == 23 || type == 54 || type == 61 || type == 62 || type == 117)
                checkInventoryOpen(p, block);
            // dispenser || chest || furnace || furnace || brewing stand
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        checkInventoryClose(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        checkInventoryClose(event.getPlayer());
    }
}
