package de.diddiz.LogBlock.listeners;

import static de.diddiz.util.BukkitUtils.rawData;

import java.util.*;

import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event.Result;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

import de.diddiz.LogBlock.LogBlock;

public class ChestAccessLogging extends LoggingListener {

    private LogBlock lb;

    private final Map<String, Boolean> heldItem = new HashMap<String, Boolean>();
    private final Map<String, Integer> clickedItem = new HashMap<String, Integer>();

    public ChestAccessLogging(final LogBlock lb) {
        super(lb);
        this.lb = lb;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getResult() == Result.DENY) return;
        final HumanEntity en = e.getWhoClicked();
        if (!(en instanceof Player)) return;
        Player p = (Player) en;
        final int slotClicked = e.getRawSlot();
        InventoryType type = e.getInventory().getType();
        ItemStack item = e.getCursor();
        if (type == InventoryType.PLAYER || type == InventoryType.CREATIVE
                || type == InventoryType.WORKBENCH || type == InventoryType.ENCHANTING
                || type == InventoryType.CRAFTING) return;
        final InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof Entity) return; // storage minecarts
        Block b = null;
        boolean unknown = false;
        final int containerSlots;
        if (type == InventoryType.CHEST) {
            if (h instanceof Chest) {
                b = ((Chest) h).getBlock();
                containerSlots = 26;
            } else {
                b = ((Chest) ((DoubleChest) h).getLeftSide()).getBlock();
                containerSlots = 53;
            }
        } else if (type == InventoryType.BREWING) {
            b = ((BrewingStand) h).getBlock();
            containerSlots = 3;
            if (item != null && item.getType() != Material.POTION) return;
        } else if (type == InventoryType.DISPENSER) {
            b = ((Dispenser) h).getBlock();
            containerSlots = 8;
        } else if (type == InventoryType.FURNACE) {
            b = ((Furnace) h).getBlock();
            if (slotClicked == 2) return; // cannot place in that slot
            containerSlots = 2;
        } else {
            unknown = true;
            return;
        }

        if (b == null || unknown) {
            lb.getLogger()
                    .warning(
                            "New container type detected; please update LogBlock to have support for logging this container.");
            return;
        }
        final BlockState state = b.getState();
        if (e.isShiftClick() && !e.isRightClick()) {
            item = e.getCurrentItem();
            int amount = item.getAmount();
            if (slotClicked > containerSlots || slotClicked == -999)
            // inside the container's window
            consumer.queueChestAccess(p.getName(), b.getLocation(), state.getTypeId(),
                    (short) item.getTypeId(), (short) amount, rawData(item));
            else
            // outside the container's window
            consumer.queueChestAccess(p.getName(), b.getLocation(), state.getTypeId(),
                    (short) item.getTypeId(), (short) (amount * -1), rawData(item));
            return;
        }
        if (item == null || item.getTypeId() == 0) {
            heldItem.put(p.getName(), !(slotClicked > containerSlots)); // true = clicked inside container
            clickedItem.put(p.getName(), new Integer(slotClicked)); // they picked it up
            return;
        }
        Integer clickeditem = clickedItem.get(p.getName());
        if (clickeditem != null) // should always be true but stuff can happen
            if (clickeditem > containerSlots && slotClicked > containerSlots || clickeditem < containerSlots
                    && slotClicked < containerSlots) return; // clicking in same inventory

        Boolean cocw = heldItem.get(p.getName());

        boolean nodata = cocw == null;
        boolean clickedOutsideChestWindow;

        if (!nodata) {
            clickedOutsideChestWindow = cocw;
            heldItem.remove(p.getName());
        } else clickedOutsideChestWindow = false;

        if (slotClicked == -999 && !clickedOutsideChestWindow) return;
        int amount = item.getAmount();
        if (e.isRightClick()) amount = 1;
        if (slotClicked > containerSlots || slotClicked == -999)
        // outside the container's window
        consumer.queueChestAccess(p.getName(), b.getLocation(), state.getTypeId(), (short) item.getTypeId(),
                (short) (amount * -1), rawData(item));
        else // inside the container's window
        consumer.queueChestAccess(p.getName(), b.getLocation(), state.getTypeId(), (short) item.getTypeId(),
                (short) amount, rawData(item));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        String p = e.getPlayer().getName();
        heldItem.remove(p);
        clickedItem.remove(p);
    }
}
