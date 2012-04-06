package de.diddiz.LogBlock.listeners;

import static de.diddiz.LogBlock.config.Config.isLogging;
import static de.diddiz.LogBlock.config.Config.logKillsLevel;

import java.util.*;

import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

import de.diddiz.LogBlock.*;
import de.diddiz.LogBlock.config.Config.LogKillsLevel;



public class KillLogging extends LoggingListener {
    private final Map<Integer, Integer> lastAttackedEntity = new HashMap<Integer, Integer>();
    private final Map<Integer, Long> lastAttackTime = new HashMap<Integer, Long>();

    public KillLogging(final LogBlock lb) {
        super(lb);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (isLogging(event.getEntity().getWorld(), Logging.KILL)
                && event instanceof EntityDamageByEntityEvent && event.getEntity() instanceof LivingEntity) {
            final LivingEntity victim = (LivingEntity) event.getEntity();
            final Entity killer = ((EntityDamageByEntityEvent) event).getDamager();
            if (victim.getHealth() - event.getDamage() > 0 || victim.getHealth() <= 0) return;
            if (logKillsLevel == LogKillsLevel.PLAYERS
                    && !(victim instanceof Player && killer instanceof Player))
                return;
            else if (logKillsLevel == LogKillsLevel.MONSTERS
                    && !((victim instanceof Player || victim instanceof Monster) && killer instanceof Player || killer instanceof Monster))
                return;
            if (this.lastAttackedEntity.containsKey(killer.getEntityId())
                    && this.lastAttackedEntity.get(killer.getEntityId()) == victim.getEntityId()
                    && System.currentTimeMillis() - this.lastAttackTime.get(killer.getEntityId()) < 5000)
                return;
            consumer.queueKill(killer, victim);
            lastAttackedEntity.put(killer.getEntityId(), victim.getEntityId());
            lastAttackTime.put(killer.getEntityId(), System.currentTimeMillis());
        }
    }
}
