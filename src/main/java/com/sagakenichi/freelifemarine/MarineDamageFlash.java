package com.sagakenichi.freelifemarine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recreates the vanilla red hurt flash for block-display based marine models.
 */
final class MarineDamageFlash {

    private static final long FLASH_TICKS = 6L;
    private static final double MATCH_DISTANCE_SQUARED = 0.55 * 0.55;
    private static final BlockData HURT_BLOCK = Bukkit.createBlockData(Material.RED_CONCRETE);

    private final JavaPlugin plugin;
    private final Map<UUID, FlashState> active = new HashMap<>();
    private long generation;

    MarineDamageFlash(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void flash(MarineMobService.MarineMob mob) {
        if (mob == null || mob.type().movementStyle() != MarineMobType.MovementStyle.AQUATIC) {
            return;
        }

        Location base = mob.location();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        long flashGeneration = ++generation;
        for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
            if (!display.isValid() || !belongsToModel(display, base, mob.type())) {
                continue;
            }

            UUID id = display.getUniqueId();
            FlashState state = active.get(id);
            if (state == null) {
                state = new FlashState(display.getBlock(), flashGeneration);
                active.put(id, state);
            } else {
                state.generation = flashGeneration;
            }
            display.setBlock(HURT_BLOCK);

            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> restore(display, flashGeneration), FLASH_TICKS);
        }
    }

    private void restore(BlockDisplay display, long expectedGeneration) {
        FlashState state = active.get(display.getUniqueId());
        if (state == null || state.generation != expectedGeneration) {
            return;
        }
        active.remove(display.getUniqueId());
        if (display.isValid()) {
            display.setBlock(state.original);
        }
    }

    private static boolean belongsToModel(BlockDisplay display, Location base, MarineMobType type) {
        Location actual = display.getLocation();
        for (MarineMobType.ModelPart part : type.parts()) {
            Location expected = relative(base, part.forward(), part.up(), part.right());
            if (actual.getWorld() == expected.getWorld()
                    && actual.distanceSquared(expected) <= MATCH_DISTANCE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static Location relative(Location base, double forward, double up, double right) {
        Vector unitForward = forwardFromYaw(base.getYaw());
        Vector forwardVector = unitForward.clone().multiply(forward);
        Vector rightVector = new Vector(unitForward.getZ(), 0.0, -unitForward.getX()).multiply(right);
        return base.clone().add(forwardVector).add(rightVector).add(0.0, up, 0.0);
    }

    private static Vector forwardFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static final class FlashState {
        private final BlockData original;
        private long generation;

        private FlashState(BlockData original, long generation) {
            this.original = original;
            this.generation = generation;
        }
    }
}
