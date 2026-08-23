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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts a fast, upward rider-controlled approach to the surface into a real breach.
 * Autonomous orcas are explicitly prevented from producing an upward breach impulse.
 */
final class RiddenOrcaBreachController {

    private static final double DIRECTION_EPSILON = 1.0E-6;
    private static final double MIN_BREACH_HORIZONTAL_BLOCKS_PER_TICK = 0.30; // 6 blocks/s
    private static final double MIN_UPWARD_LOOK = 0.28;
    private static final int BREACH_COOLDOWN_TICKS = 18;
    private static final double MIN_VERTICAL_LAUNCH = 0.58;
    private static final double MAX_VERTICAL_LAUNCH = 1.45;
    private static final double MAX_HORIZONTAL_LAUNCH = 1.85;
    private static final double AUTONOMOUS_BREACH_VERTICAL_THRESHOLD = 0.18;

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private BukkitTask task;
    private long serverTick;

    RiddenOrcaBreachController(JavaPlugin plugin, MarineMobService mobs) {
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
        previousLocations.clear();
        cooldownUntil.clear();
    }

    private void tick() {
        serverTick++;
        Set<UUID> seen = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            for (Horse horse : world.getEntitiesByClass(Horse.class)) {
                MarineMobService.MarineMob mob = mobs.find(horse);
                if (mob == null || !mob.id().equals(horse.getUniqueId())
                        || mob.type() != MarineMobType.ORCA || mob.showControlled()
                        || mob.commandControlled()) {
                    continue;
                }

                UUID id = horse.getUniqueId();
                seen.add(id);
                Location current = horse.getLocation();
                Location previous = previousLocations.put(id, current.clone());
                Player pilot = firstPlayerPassenger(horse);

                if (pilot == null) {
                    suppressAutonomousBreach(horse, current);
                    continue;
                }
                if (!isWaterContact(current)) {
                    continue;
                }
                if (serverTick < cooldownUntil.getOrDefault(id, 0L)) {
                    continue;
                }
                if (previous == null || previous.getWorld() != current.getWorld()) {
                    continue;
                }

                double dx = current.getX() - previous.getX();
                double dz = current.getZ() - previous.getZ();
                double horizontalTravel = Math.hypot(dx, dz);
                if (horizontalTravel < MIN_BREACH_HORIZONTAL_BLOCKS_PER_TICK) {
                    continue;
                }

                Vector look = pilot.getEyeLocation().getDirection();
                if (look.lengthSquared() < DIRECTION_EPSILON) {
                    continue;
                }
                look.normalize();
                if (look.getY() < MIN_UPWARD_LOOK || !isNearSurface(current)) {
                    continue;
                }

                Vector horizontal = look.clone().setY(0.0);
                if (horizontal.lengthSquared() < DIRECTION_EPSILON) {
                    continue;
                }
                horizontal.normalize();

                Vector measured = new Vector(dx, 0.0, dz).normalize();
                if (measured.dot(horizontal) < 0.55) {
                    continue;
                }

                Location launchStart = firstAirAbove(current);
                if (launchStart == null || solidCollision(launchStart)
                        || solidCollision(launchStart.clone().add(0.0, 1.0, 0.0))) {
                    continue;
                }

                double configuredPerTick = mob.riddenSpeedBlocksPerTick();
                double horizontalLaunch = Math.min(MAX_HORIZONTAL_LAUNCH,
                        Math.max(horizontalTravel, configuredPerTick * 0.72));
                double verticalLaunch = clamp(
                        Math.max(MIN_VERTICAL_LAUNCH, configuredPerTick * look.getY() * 1.12),
                        MIN_VERTICAL_LAUNCH,
                        MAX_VERTICAL_LAUNCH);

                horse.teleport(launchStart);
                horse.setGravity(true);
                horse.setFallDistance(0.0F);
                horse.setVelocity(horizontal.multiply(horizontalLaunch).setY(verticalLaunch));
                horse.setRotation(pilot.getLocation().getYaw(),
                        (float) clamp(pilot.getLocation().getPitch(), -65.0, -8.0));

                previousLocations.put(id, launchStart.clone());
                cooldownUntil.put(id, serverTick + BREACH_COOLDOWN_TICKS);
            }
        }

        previousLocations.keySet().retainAll(seen);
        cooldownUntil.keySet().retainAll(seen);
    }

    private static void suppressAutonomousBreach(Horse horse, Location location) {
        if (!isWaterContact(location) || !isNearSurface(location)) {
            return;
        }
        Vector velocity = horse.getVelocity();
        if (velocity.getY() <= AUTONOMOUS_BREACH_VERTICAL_THRESHOLD) {
            return;
        }
        horse.setVelocity(new Vector(velocity.getX(), 0.035, velocity.getZ()));
    }

    private static Player firstPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean isNearSurface(Location location) {
        if (!isWaterAt(location)) {
            return false;
        }
        return !isWaterAt(location.clone().add(0.0, 1.15, 0.0))
                || !isWaterAt(location.clone().add(0.0, 1.85, 0.0));
    }

    private static Location firstAirAbove(Location location) {
        for (int step = 0; step <= 10; step++) {
            double dy = step * 0.20;
            Location probe = location.clone().add(0.0, dy, 0.0);
            if (!isWaterAt(probe) && !isWaterAt(probe.clone().add(0.0, 0.25, 0.0))) {
                return probe.add(0.0, 0.05, 0.0);
            }
        }
        return null;
    }

    private static boolean isWaterContact(Location location) {
        return isWaterAt(location)
                || isWaterAt(location.clone().add(0.0, 0.35, 0.0))
                || isWaterAt(location.clone().add(0.0, -0.45, 0.0));
    }

    private static boolean isWaterAt(Location location) {
        Block block = location.getBlock();
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
            return true;
        }
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private static boolean solidCollision(Location location) {
        Block block = location.getBlock();
        return block.getType().isSolid() && !block.isPassable();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
