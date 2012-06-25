package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;

import org.bukkit.block.BlockState;
import org.bukkit.event.*;
import org.bukkit.event.world.StructureGrowEvent;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;

public class StructureGrowLogging extends LoggingListener {
    public StructureGrowLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(final StructureGrowEvent event) {
        final WorldConfig wcfg = getWorldConfig(event.getWorld());
        if (wcfg == null) return;
        String playerName = "NaturalGrow";
        if (event.getPlayer() != null && wcfg.isLogging(Logging.BONEMEALSTRUCTUREGROW)) playerName = event.getPlayer().getName();
        for (final BlockState state : event.getBlocks())
            consumer.queueBlockReplace(playerName, state.getBlock().getState(), state);
    }
}
