package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.Session.getSession;
import static de.diddiz.LogBlock.Session.hasSession;
import static de.diddiz.LogBlock.config.Config.isLogged;
import static de.diddiz.LogBlock.config.Config.toolsByType;

import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import com.sk89q.worldedit.bukkit.selections.CuboidSelection;

import de.diddiz.LogBlock.*;

public class ToolListener implements Listener {
    private final CommandsHandler handler;
    private final LogBlock logblock;

    public ToolListener(final LogBlock logblock) {
        this.logblock = logblock;
        this.handler = logblock.getCommandsHandler();
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        if (hasSession(player)) {
            final Session session = getSession(player);
            for (final Entry<Tool, ToolData> entry : session.toolData.entrySet()) {
                final Tool tool = entry.getKey();
                final ToolData toolData = entry.getValue();
                if (toolData.enabled && !this.logblock.hasPermission(player, "logblock.tools." + tool.name)) {
                    toolData.enabled = false;
                    player.getInventory().removeItem(new ItemStack(tool.item, 1));
                    player.sendMessage(ChatColor.GREEN + "Tool disabled.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getMaterial() != null) {
            final Action action = event.getAction();
            final int type = event.getMaterial().getId();
            final Tool tool = toolsByType.get(type);
            final Player player = event.getPlayer();
            if (tool != null && (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
                    && isLogged(player.getWorld())
                    && this.logblock.hasPermission(player, "logblock.tools." + tool.name)) {
                final ToolBehavior behavior = action == Action.RIGHT_CLICK_BLOCK ? tool.rightClickBehavior
                        : tool.leftClickBehavior;
                final ToolData toolData = getSession(player).toolData.get(tool);
                if (behavior != ToolBehavior.NONE && toolData.enabled) {
                    final Block block = event.getClickedBlock();
                    final QueryParams params = toolData.params;
                    params.loc = null;
                    params.sel = null;
                    if (behavior == ToolBehavior.BLOCK)
                        params.setLocation(block.getRelative(event.getBlockFace()).getLocation());
                    else if (block.getTypeId() != 54 || tool.params.radius != 0)
                        params.setLocation(block.getLocation());
                    else {
                        for (final BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH,
                                BlockFace.EAST, BlockFace.WEST })
                            if (block.getRelative(face).getTypeId() == 54)
                                params.setSelection(new CuboidSelection(event.getPlayer().getWorld(), block
                                        .getLocation(), block.getRelative(face).getLocation()));
                        if (params.sel == null) params.setLocation(block.getLocation());
                    }
                    try {
                        if (toolData.mode == ToolMode.ROLLBACK)
                            this.handler.new CommandRollback(player, params, true);
                        else if (toolData.mode == ToolMode.REDO)
                            this.handler.new CommandRedo(player, params, true);
                        else if (toolData.mode == ToolMode.CLEARLOG)
                            this.handler.new CommandClearLog(player, params, true);
                        else if (toolData.mode == ToolMode.WRITELOGFILE)
                            this.handler.new CommandWriteLogFile(player, params, true);
                        else this.handler.new CommandLookup(player, params, true);
                    } catch (final Exception ex) {
                        player.sendMessage(ChatColor.RED + ex.getMessage());
                    }
                    event.setCancelled(true);
                }
            }
        }
    }
}
