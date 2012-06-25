package de.diddiz.util;

import static de.diddiz.util.MaterialName.materialName;

import java.io.File;
import java.util.*;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public class BukkitUtils {
    private static final Set<Set<Integer>> blockEquivalents;

    static {
        blockEquivalents = new HashSet<Set<Integer>>(7);
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(2, 3, 60)));
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(8, 9, 79)));
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(10, 11)));
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(61, 62)));
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(73, 74)));
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(75, 76)));
        blockEquivalents.add(new HashSet<Integer>(Arrays.asList(93, 94)));
    }

    public static class ItemStackComparator implements Comparator<ItemStack> {
        @Override
        public int compare(final ItemStack a, final ItemStack b) {
            final int aType = a.getTypeId(), bType = b.getTypeId();
            if (aType < bType) return -1;
            if (aType > bType) return 1;
            final byte aData = rawData(a), bData = rawData(b);
            if (aData < bData) return -1;
            if (aData > bData) return 1;
            return 0;
        }
    }

    public static ItemStack[] compareInventories(final ItemStack[] items1, final ItemStack[] items2) {
        final ItemStackComparator comperator = new ItemStackComparator();
        final ArrayList<ItemStack> diff = new ArrayList<ItemStack>();
        final int l1 = items1.length, l2 = items2.length;
        int c1 = 0, c2 = 0;
        while (c1 < l1 || c2 < l2) {
            if (c1 >= l1) {
                diff.add(items2[c2]);
                c2++;
                continue;
            }
            if (c2 >= l2) {
                items1[c1].setAmount(items1[c1].getAmount() * -1);
                diff.add(items1[c1]);
                c1++;
                continue;
            }
            final int comp = comperator.compare(items1[c1], items2[c2]);
            if (comp < 0) {
                items1[c1].setAmount(items1[c1].getAmount() * -1);
                diff.add(items1[c1]);
                c1++;
            } else if (comp > 0) {
                diff.add(items2[c2]);
                c2++;
            } else {
                final int amount = items2[c2].getAmount() - items1[c1].getAmount();
                if (amount != 0) {
                    items1[c1].setAmount(amount);
                    diff.add(items1[c1]);
                }
                c1++;
                c2++;
            }
        }
        return diff.toArray(new ItemStack[diff.size()]);
    }

    public static ItemStack[] compressInventory(final ItemStack[] items) {
        final ArrayList<ItemStack> compressed = new ArrayList<ItemStack>();
        for (final ItemStack item : items)
            if (item != null) {
                final int type = item.getTypeId();
                final byte data = rawData(item);
                boolean found = false;
                for (final ItemStack item2 : compressed)
                    if (type == item2.getTypeId() && data == rawData(item2)) {
                        item2.setAmount(item2.getAmount() + item.getAmount());
                        found = true;
                        break;
                    }
                if (!found) compressed.add(new ItemStack(type, item.getAmount(), (short) 0, data));
            }
        Collections.sort(compressed, new ItemStackComparator());
        return compressed.toArray(new ItemStack[compressed.size()]);
    }

    public static String entityName(final Entity entity) {
        if (entity instanceof Player) return ((Player) entity).getName();
        if (entity instanceof TNTPrimed) return "TNT";
        return entity.getClass().getSimpleName().substring(5);
    }

    public static boolean equalTypes(final int type1, final int type2) {
        if (type1 == type2) return true;
        for (final Set<Integer> equivalent : blockEquivalents)
            if (equivalent.contains(type1) && equivalent.contains(type2)) return true;
        return false;
    }

    public static String friendlyWorldname(final String worldName) {
        return new File(worldName).getName();
    }

    public static Set<Set<Integer>> getBlockEquivalents() {
        return blockEquivalents;
    }

    public static void giveTool(final Player player, final int type) {
        final Inventory inv = player.getInventory();
        if (inv.contains(type)) player.sendMessage(ChatColor.RED + "You have already a " + materialName(type));
        else {
            final int free = inv.firstEmpty();
            if (free >= 0) {
                if (player.getItemInHand() != null && player.getItemInHand().getTypeId() != 0) inv.setItem(free, player.getItemInHand());
                player.setItemInHand(new ItemStack(type, 1));
                player.sendMessage(ChatColor.GREEN + "Here's your " + materialName(type));
            } else player.sendMessage(ChatColor.RED + "You have no empty slot in your inventory");
        }
    }

    public static int modifyContainer(final BlockState b, final ItemStack item) {
        if (b instanceof InventoryHolder) {
            final Inventory inv = ((InventoryHolder) b).getInventory();
            if (item.getAmount() < 0) {
                item.setAmount(-item.getAmount());
                final ItemStack tmp = inv.removeItem(item).get(0);
                return tmp != null ? tmp.getAmount() : 0;
            } else if (item.getAmount() > 0) {
                final ItemStack tmp = inv.addItem(item).get(0);
                return tmp != null ? tmp.getAmount() : 0;
            }
        }
        return 0;
    }

    public static byte rawData(final ItemStack item) {
        return item.getType() != null ? item.getData() != null ? item.getData().getData() : 0 : 0;
    }

    public static int saveSpawnHeight(final Location loc) {
        final World world = loc.getWorld();
        final Chunk chunk = world.getChunkAt(loc);
        if (!world.isChunkLoaded(chunk)) world.loadChunk(chunk);
        final int x = loc.getBlockX(), z = loc.getBlockZ();
        int y = loc.getBlockY();
        boolean lower = world.getBlockTypeIdAt(x, y, z) == 0, upper = world.getBlockTypeIdAt(x, y + 1, z) == 0;
        while ((!lower || !upper) && y != 127) {
            lower = upper;
            upper = world.getBlockTypeIdAt(x, ++y, z) == 0;
        }
        while (world.getBlockTypeIdAt(x, y - 1, z) == 0 && y != 0)
            y--;
        return y;
    }
}
