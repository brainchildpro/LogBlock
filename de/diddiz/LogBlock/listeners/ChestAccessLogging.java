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

        ContainerState(Location loc, ItemStack[] items) {
            this.items = items;
            this.loc = loc;
        }
    }

    private final Map<Player, ContainerState> containers = new HashMap<Player, ContainerState>();

    public ChestAccessLogging(LogBlock lb) {
        super(lb);
    }

    public void checkInventoryClose(Player player) {
        final ContainerState cont = this.containers.get(player);
        if (cont != null) {
            final ItemStack[] before = cont.items;
            final BlockState state = cont.loc.getBlock().getState();
            if (!(state instanceof InventoryHolder)) return;
            final ItemStack[] after = compressInventory(((InventoryHolder) state).getInventory()
                    .getContents());
            final ItemStack[] diff = compareInventories(before, after);
            for (final ItemStack item : diff)
                this.consumer.queueChestAccess(player.getName(), cont.loc, state.getTypeId(),
                        (short) item.getTypeId(), (short) item.getAmount(), rawData(item));
            this.containers.remove(player);
        }
    }

    public void checkInventoryOpen(Player player, Block block) {
        final BlockState state = block.getState();
        if (!(state instanceof InventoryHolder)) return;
        this.containers.put(player, new ContainerState(block.getLocation(),
                compressInventory(((InventoryHolder) state).getInventory().getContents())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(PlayerChatEvent event) {
        checkInventoryClose(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        checkInventoryClose(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        checkInventoryClose(player);
        if (!event.isCancelled() && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && isLogging(player.getWorld(), Logging.CHESTACCESS)) {
            final Block block = event.getClickedBlock();
            final int type = block.getTypeId();
            if (type == 23 || type == 54 || type == 61 || type == 62) checkInventoryOpen(player, block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        checkInventoryClose(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        checkInventoryClose(event.getPlayer());
    }
}
