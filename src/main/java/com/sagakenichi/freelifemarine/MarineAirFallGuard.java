package com.sagakenichi.freelifemarine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Safety pass for aquatic carrier entities that have stopped moving while unsupported.
 *
 * <p>The normal breach code controls its own arc. This guard intervenes only after an
 * orca or shark remains at effectively the same Y position for several consecutive ticks
 * while genuinely in open air. Water directly under the carrier counts as surface
 * support, so an animal floating at the water line is never pushed downward by this
 * recovery path.</p>
 */
final class MarineAirFallGuard {

    private static final double STAGNANT_Y_EPSILON = 0.003;
    private static final double STAGNANT_VERTICAL_SPEED = 0.04;
    private static final int STAGNANT_TICKS_BEFORE_RECOVERY = 3;
    private static final double SURFACE_STABILIZE_MAX_VERTICAL_SPEED = 0.08;
    private static final double SURFACE_ANCHOR_OFFSET = 0.88;

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private final Map<UUID, AirSample> samples = new HashMap<>();
    private BukkitTask task;

    MarineAirFallGuard(JavaPlugin plugin, MarineMobService mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        samples.clear();
    }

    private void tick() {
        Set<UUID> seen = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Horse.class, Slime.class)) {
                MarineMobService.MarineMob mob = mobs.find(entity);
                if (mob == null || !mob.id().equals(entity.getUniqueId())
                        || mob.type().movementStyle() != MarineMobType.MovementStyle.AQUATIC) {
                    continue;
                }

                UUID id = entity.getUniqueId();
                seen.add(id);
                Location location = entity.getLocation();

                if (stabilizeAtWaterSurface(entity, location)) {
                    samples.remove(id);
                    continue;
                }

                if (isWaterOrSurfaceContact(location) || entity.isOnGround()) {
                    samples.remove(id);
                    continue;
                }

                AirSample sample = samples.computeIfAbsent(id, ignored -> new AirSample(location.getY()));
                double deltaY = location.getY() - sample.previousY;
                double verticalSpeed = entity.getVelocity().getY();

                if (Math.abs(deltaY) <= STAGNANT_Y_EPSILON
                        && Math.abs(verticalSpeed) <= STAGNANT_VERTICAL_SPEED) {
                    sample.stagnantTicks++;
                } else {
                    sample.stagnantTicks = 0;
                }
                sample.previousY = location.getY();

                if (sample.stagnantTicks < STAGNANT_TICKS_BEFORE_RECOVERY) {
                    continue;
                }

                entity.setGravity(true);
                Vector velocity = entity.getVelocity();
                // First falling tick matches FallingBlockEntity: (0 - 0.04) * 0.98.
                double fallingBlockStep = MarineAirKinematics.nextVerticalVelocity(0.0);
                velocity.setY(Math.min(velocity.getY(), fallingBlockStep));
                entity.setVelocity(velocity);
                sample.stagnantTicks = 0;
            }
        }

        samples.keySet().retainAll(seen);
    }

    private static boolean stabilizeAtWaterSurface(Entity entity, Location location) {
        if (isWaterAt(location) || Math.abs(entity.getVelocity().getY()) > SURFACE_STABILIZE_MAX_VERTICAL_SPEED) {
            return false;
        }

        Block surfaceWater = findSurfaceWaterBelow(location);
        if (surfaceWater == null) {
            return false;
        }

        Location stable = location.clone();
        stable.setY(surfaceWater.getY() + SURFACE_ANCHOR_OFFSET);
        entity.teleport(stable);
        entity.setGravity(true);
        Vector velocity = entity.getVelocity();
        velocity.setY(0.0);
        entity.setVelocity(velocity);
        entity.setFallDistance(0.0F);
        return true;
    }

    private static Block findSurfaceWaterBelow(Location location) {
        double[] probes = {0.20, 0.40, 0.60, 0.80, 1.00, 1.20};
        for (double down : probes) {
            Block block = location.clone().add(0.0, -down, 0.0).getBlock();
            if (!isWater(block)) {
                continue;
            }
            Block above = block.getRelative(0, 1, 0);
            if (!isWater(above)) {
                return block;
            }
        }
        return null;
    }

    private static boolean isWaterOrSurfaceContact(Location location) {
        return isWaterAt(location)
                || isWaterAt(location.clone().add(0.0, 0.35, 0.0))
                || isWaterAt(location.clone().add(0.0, -0.45, 0.0))
                || isWaterAt(location.clone().add(0.0, -0.85, 0.0))
                || isWaterAt(location.clone().add(0.0, -1.15, 0.0));
    }

    private static boolean isWaterAt(Location location) {
        return isWater(location.getBlock());
    }

    private static boolean isWater(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
            return true;
        }
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static final class AirSample {
        private double previousY;
        private int stagnantTicks;

        private AirSample(double previousY) {
            this.previousY = previousY;
        }
    }
}
