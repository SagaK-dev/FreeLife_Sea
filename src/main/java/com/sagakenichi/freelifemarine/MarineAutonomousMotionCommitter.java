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
import org.bukkit.entity.Slime;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Commits autonomous aquatic motion by moving the carrier on the server rather than
 * depending on Horse/Slime water physics. The earlier behavior controller still chooses
 * direction and pace; this final pass guarantees that those choices produce coordinate
 * progress even when the native carrier is wedged or heavily damped in water.
 */
final class MarineAutonomousMotionCommitter {

    private static final double DIRECTION_EPSILON = 1.0E-6;
    private static final double SWEEP_STEP = 0.16;
    private static final double STRONG_VERTICAL_MANEUVER = 0.085;
    private static final double MIN_COMMITTED_FRACTION = 0.72;
    private static final double[] RECOVERY_TURNS = {
            28.0, -28.0, 55.0, -55.0, 82.0, -82.0, 120.0, -120.0, 180.0
    };

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private BukkitTask task;

    MarineAutonomousMotionCommitter(JavaPlugin plugin, MarineMobService mobs) {
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
    }

    private void tick() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Horse.class, Slime.class)) {
                MarineMobService.MarineMob mob = mobs.find(entity);
                if (mob == null || !mob.id().equals(entity.getUniqueId())) {
                    continue;
                }
                if (mob.type().movementStyle() != MarineMobType.MovementStyle.AQUATIC
                        || mob.showControlled() || mob.commandControlled()
                        || hasPlayerPassenger(entity)) {
                    continue;
                }

                Location start = entity.getLocation();
                if (!isWaterContact(start)) {
                    continue;
                }

                Vector requested = entity.getVelocity();
                if (Math.abs(requested.getY()) > STRONG_VERTICAL_MANEUVER) {
                    continue;
                }

                Vector displacement = requested.clone();
                double horizontal = Math.hypot(displacement.getX(), displacement.getZ());
                double minimum = MarineNaturalMotionProfile.minCruiseBlocksPerTick(mob.type());
                double maximum = MarineNaturalMotionProfile.maxCruiseBlocksPerTick(mob.type());

                if (horizontal < minimum * MIN_COMMITTED_FRACTION) {
                    Vector forward = forwardFromYaw(start.getYaw()).multiply(minimum);
                    displacement.setX(forward.getX());
                    displacement.setZ(forward.getZ());
                    horizontal = minimum;
                } else if (horizontal > maximum) {
                    double factor = maximum / horizontal;
                    displacement.setX(displacement.getX() * factor);
                    displacement.setZ(displacement.getZ() * factor);
                }
                displacement.setY(clamp(displacement.getY(), -0.060, 0.060));

                MoveResult result = sweep(start, displacement, mob.type());
                if (!result.moved()) {
                    result = recoveryMove(start, displacement, mob.type(), minimum);
                }

                if (result.moved()) {
                    entity.teleport(result.location());
                    entity.setFallDistance(0.0F);
                }

                // Prevent native carrier physics from applying the same displacement again.
                // The next behavior tick will publish a fresh requested swim vector.
                entity.setVelocity(new Vector());
            }
        }
    }

    private static MoveResult recoveryMove(Location start, Vector requested,
                                           MarineMobType type, double minimumSpeed) {
        Vector base = requested.clone().setY(0.0);
        if (base.lengthSquared() < DIRECTION_EPSILON) {
            base = forwardFromYaw(start.getYaw());
        } else {
            base.normalize();
        }

        MoveResult best = new MoveResult(start, false);
        double bestDistanceSquared = 0.0;
        for (double degrees : RECOVERY_TURNS) {
            Vector direction = rotateY(base, Math.toRadians(degrees)).normalize();
            Vector candidate = direction.multiply(minimumSpeed).setY(0.0);
            MoveResult result = sweep(start, candidate, type);
            if (!result.moved()) {
                continue;
            }
            double distanceSquared = result.location().distanceSquared(start);
            if (distanceSquared > bestDistanceSquared) {
                best = result;
                bestDistanceSquared = distanceSquared;
            }
        }
        return best;
    }

    private static MoveResult sweep(Location start, Vector displacement, MarineMobType type) {
        double length = displacement.length();
        if (length < DIRECTION_EPSILON) {
            return new MoveResult(start, false);
        }

        int steps = Math.max(1, (int) Math.ceil(length / SWEEP_STEP));
        Vector increment = displacement.clone().multiply(1.0 / steps);
        Location current = start.clone();
        Location lastSafe = start.clone();
        int toleratedBodyScore = MarineCollisionGeometry.bodyCollisionScore(start, type);

        for (int i = 0; i < steps; i++) {
            current.add(increment);
            if (!hasWaterRoom(current) || carrierCollides(current)) {
                break;
            }

            int bodyScore = MarineCollisionGeometry.bodyCollisionScore(current, type);
            if (toleratedBodyScore == 0) {
                if (bodyScore > 0) {
                    break;
                }
            } else if (bodyScore > toleratedBodyScore) {
                break;
            } else {
                // A mob that spawned partly overlapping terrain may move through positions
                // that are equally good or better until it has completely escaped.
                toleratedBodyScore = Math.min(toleratedBodyScore, bodyScore);
            }
            lastSafe = current.clone();
        }

        boolean moved = lastSafe.distanceSquared(start) > 1.0E-8;
        return new MoveResult(lastSafe, moved);
    }

    private static boolean carrierCollides(Location location) {
        return solid(location.clone().add(0.0, 0.10, 0.0))
                || solid(location.clone().add(0.0, 0.90, 0.0))
                || solid(location.clone().add(0.0, 1.55, 0.0));
    }

    private static boolean solid(Location location) {
        Block block = location.getBlock();
        return block.getType().isSolid() && !block.isPassable();
    }

    private static boolean hasWaterRoom(Location location) {
        return isWaterAt(location)
                && (isWaterAt(location.clone().add(0.0, -0.55, 0.0))
                || isWaterAt(location.clone().add(0.0, 0.45, 0.0)));
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

    private static boolean hasPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private static Vector forwardFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static Vector rotateY(Vector vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = vector.getX() * cos + vector.getZ() * sin;
        double z = -vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record MoveResult(Location location, boolean moved) {
    }
}
