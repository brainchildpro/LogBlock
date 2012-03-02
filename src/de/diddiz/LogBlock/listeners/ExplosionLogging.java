package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.getWorldConfig;
import static de.diddiz.LogBlock.config.Config.logCreeperExplosionsAsPlayerWhoTriggeredThese;

import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityExplodeEvent;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.WorldConfig;

public class ExplosionLogging extends LoggingListener {
    public ExplosionLogging(LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        final WorldConfig wcfg = getWorldConfig(event.getLocation().getWorld());
        if (!event.isCancelled() && wcfg != null) {
            final String name;
            if (event.getEntity() == null) {
                if (!wcfg.isLogging(Logging.MISCEXPLOSION)) return;
                name = "Explosion";
            } else if (event.getEntity() instanceof TNTPrimed) {
                if (!wcfg.isLogging(Logging.TNTEXPLOSION)) return;
                name = "TNT";
            } else if (event.getEntity() instanceof Creeper) {
                if (!wcfg.isLogging(Logging.CREEPEREXPLOSION)) return;
                if (logCreeperExplosionsAsPlayerWhoTriggeredThese) {
                    final Entity target = ((Creeper) event.getEntity()).getTarget();
                    name = target instanceof Player ? ((Player) target).getName() : "Creeper";
                } else name = "Creeper";
            } else if (event.getEntity() instanceof Fireball) {
                if (!wcfg.isLogging(Logging.GHASTFIREBALLEXPLOSION)) return;
                name = "Ghast";
            } else if (event.getEntity() instanceof EnderDragon) {
                if (!wcfg.isLogging(Logging.ENDERDRAGON)) return;
                name = "EnderDragon";
            } else {
                if (!wcfg.isLogging(Logging.MISCEXPLOSION)) return;
                name = "Explosion";
            }
            for (final Block block : event.blockList()) {
                final int type = block.getTypeId();
                if (wcfg.isLogging(Logging.SIGNTEXT) & (type == 63 || type == 68))
                    this.consumer.queueSignBreak(name, (Sign) block.getState());
                else if (wcfg.isLogging(Logging.CHESTACCESS) && (type == 23 || type == 54 || type == 61))
                    this.consumer.queueContainerBreak(name, block.getState());
                else this.consumer.queueBlockBreak(name, block.getState());
            }
        }
    }
}
