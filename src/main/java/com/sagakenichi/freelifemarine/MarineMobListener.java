package com.sagakenichi.freelifemarine;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class MarineMobListener implements Listener {

    private final MarineMobService mobs;
    private final MarineDamageFlash damageFlash;

    public MarineMobListener(MarineMobService mobs, MarineDamageFlash damageFlash) {
        this.mobs = mobs;
        this.damageFlash = damageFlash;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (mobs.feed(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (!mobs.mount(event.getPlayer(), event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        MarineMobService.MarineMob mob = mobs.find(event.getEntity());
        if (mob == null) {
            return;
        }

        event.setCancelled(true);
        if (mobs.damage(event.getEntity(), event.getFinalDamage()) && mob.health() > 0.0) {
            damageFlash.flash(mob);
        }
    }
}
