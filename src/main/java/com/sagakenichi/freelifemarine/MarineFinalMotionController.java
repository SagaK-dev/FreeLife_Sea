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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Final per-tick motion pass for behavior that must win after the normal marine AI.
 * Airborne movement is integrated manually so carrier physics cannot leave an animal
 * suspended. Ridden orcas follow the pilot's three-dimensional gaze while in water.
 * Autonomous aquatic movement also receives a final speed floor and independent breach
 * scheduler so high activity cannot be cancelled by an earlier controller in the tick.
 */
final class MarineFinalMotionController {

    private static final double DIRECTION_EPSILON = 1.0E-6;
    private static final double SUPPORT_PROBE = 0.10;
    private static final double DEEP_RIDER_MAX_VERTICAL = 0.85;
    private static final double SHALLOW_RIDER_MAX_VERTICAL = 0.12;
    private static final double BREACH_RIDER_MAX_VERTICAL = 0.95;
    private static final double RIDDEN_SWEEP_STEP = 0.24;

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private final Map<UUID, AirState> airborne = new HashMap<>();
    private final Map<UUID, Long> nextAutonomousBreachTick = new HashMap<>();
    private final Map<UUID, BreachLaunch> breachLaunches = new HashMap<>();
    private final Map<UUID, Long> lastForwardIntentTick = new HashMap<>();
    private long serverTick;
    private BukkitTask task;

    MarineFinalMotionController(JavaPlugin plugin, MarineMobService mobs) {
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
        airborne.clear();
        nextAutonomousBreachTick.clear();
        breachLaunches.clear();
        lastForwardIntentTick.clear();
    }

    private void tick() {
        serverTick++;
        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Horse.class, Slime.class)) {
                MarineMobService.MarineMob mob = mobs.find(entity);
                if (mob == null || !mob.id().equals(entity.getUniqueId())) {
                    continue;
                }
                UUID id = entity.getUniqueId();
                seen.add(id);

                Location location = entity.getLocation();
                boolean inWater = isStrictWaterContact(location);
                boolean supported = entity.isOnGround() || hasSolidSupport(location);
                if (!inWater && !supported) {
                    breachLaunches.remove(id);
                    if (entity instanceof Horse horse) {
                        horse.setAI(false);
                    }
                    integrateAirborne(entity, mob.type());
                    continue;
                }

                airborne.remove(id);
                entity.setGravity(true);

                Player pilot = entity instanceof Horse horse ? firstPlayerPassenger(horse) : null;
                if (pilot == null) {
                    lastForwardIntentTick.remove(id);
                }
                if (pilot != null && mob.type() == MarineMobType.ORCA && inWater && !mob.showControlled()) {
                    breachLaunches.remove(id);
                    steerRiddenOrca((Horse) entity, pilot, mob);
                    continue;
                }

                if (!inWater || mob.showControlled() || mob.commandControlled() || pilot != null
                        || mob.type().movementStyle() != MarineMobType.MovementStyle.AQUATIC) {
                    breachLaunches.remove(id);
                    continue;
                }

                BreachLaunch activeLaunch = breachLaunches.get(id);
                if (activeLaunch != null) {
                    if (activeLaunch.waterTicksRemaining > 0) {
                        entity.setGravity(true);
                        entity.setVelocity(new Vector(
                                activeLaunch.horizontalX,
                                activeLaunch.vertical,
                                activeLaunch.horizontalZ));
                        activeLaunch.waterTicksRemaining--;
                        continue;
                    }
                    breachLaunches.remove(id);
                }

                if (tryStartAutonomousBreach(entity, mob)) {
                    continue;
                }

                applyAutonomousSpeedFloor(entity, mob);
            }
        }
        airborne.keySet().retainAll(seen);
        nextAutonomousBreachTick.keySet().retainAll(seen);
        breachLaunches.keySet().retainAll(seen);
        lastForwardIntentTick.keySet().retainAll(seen);
    }

    private boolean tryStartAutonomousBreach(Entity entity, MarineMobService.MarineMob mob) {
        UUID id = entity.getUniqueId();
        long next = nextAutonomousBreachTick.computeIfAbsent(id,
                ignored -> serverTick + randomBreachDelay(mob.type()));
        if (serverTick < next) {
            return false;
        }

        Location location = entity.getLocation();
        if (!isWithinOneBlockOfSurface(location)
                || !hasClearJumpColumn(location, MarineJumpProfile.clearanceBlocks(mob.type()))) {
            nextAutonomousBreachTick.put(id, serverTick + 20L);
            return false;
        }

        int height = ThreadLocalRandom.current().nextInt(
                MarineJumpProfile.minHeightBlocks(mob.type()),
                MarineJumpProfile.maxHeightExclusive(mob.type()));
        int speedLevel = MarineJumpProfile.speedLevelForHeight(mob.type(), height);
        double horizontalSpeed = MarineSpeedLevel.of(speedLevel).blocksPerTick();
        Vector forward = forwardFromYaw(entity.getLocation().getYaw());
        double vertical = MarineJumpProfile.initialVerticalVelocity(height);

        BreachLaunch launch = new BreachLaunch(
                forward.getX() * horizontalSpeed,
                vertical,
                forward.getZ() * horizontalSpeed,
                3);
        breachLaunches.put(id, launch);
        nextAutonomousBreachTick.put(id, serverTick + randomBreachDelay(mob.type()));
        entity.setGravity(true);
        entity.setVelocity(new Vector(launch.horizontalX, launch.vertical, launch.horizontalZ));
        return true;
    }

    private static long randomBreachDelay(MarineMobType type) {
        int min = MarineActivityProfile.minJumpDelayTicks(type);
        int maxExclusive = MarineActivityProfile.maxJumpDelayTicksExclusive(type);
        if (min == Integer.MAX_VALUE || maxExclusive == Integer.MAX_VALUE) {
            return Long.MAX_VALUE / 4;
        }
        return ThreadLocalRandom.current().nextInt(min, maxExclusive);
    }

    private static void applyAutonomousSpeedFloor(Entity entity, MarineMobService.MarineMob mob) {
        Vector velocity = entity.getVelocity();
        double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
        double minimum = MarineSpeedLevel.of(MarineActivityProfile.minRoamLevel(mob.type())).blocksPerTick();
        if (horizontalSpeed >= minimum * 0.92) {
            return;
        }

        Vector direction = velocity.clone().setY(0.0);
        if (direction.lengthSquared() < DIRECTION_EPSILON) {
            direction = forwardFromYaw(entity.getLocation().getYaw());
        } else {
            direction.normalize();
        }

        Location ahead = entity.getLocation().clone().add(direction.clone().multiply(1.4));
        if (!isWaterAt(ahead) && !isWaterAt(ahead.clone().add(0.0, -0.65, 0.0))) {
            return;
        }

        double targetSpeed = Math.min(minimum,
                horizontalSpeed + Math.max(0.08, minimum * 0.22));
        Vector boosted = direction.multiply(targetSpeed).setY(velocity.getY());
        entity.setVelocity(boosted);
    }

    private void integrateAirborne(Entity entity, MarineMobType type) {
        UUID id = entity.getUniqueId();
        AirState state = airborne.computeIfAbsent(id, ignored -> AirState.from(entity.getVelocity()));
        entity.setGravity(false);

        state.vertical = MarineAirKinematics.nextVerticalVelocity(state.vertical);
        Vector displacement = new Vector(state.horizontalX, state.vertical, state.horizontalZ);
        MoveResult result = sweepMove(entity.getLocation(), displacement, type);

        if (result.enteredWater) {
            entity.teleport(result.location);
            entity.setGravity(true);
            entity.setVelocity(new Vector(
                    state.horizontalX,
                    Math.min(state.vertical, -0.05),
                    state.horizontalZ));
            airborne.remove(id);
            return;
        }

        if (result.hitSolid) {
            entity.teleport(result.location);
            entity.setGravity(true);
            entity.setVelocity(new Vector());
            airborne.remove(id);
            return;
        }

        entity.teleport(result.location);
        entity.setVelocity(new Vector());
        state.horizontalX = MarineAirKinematics.nextHorizontalVelocity(state.horizontalX);
        state.horizontalZ = MarineAirKinematics.nextHorizontalVelocity(state.horizontalZ);
    }

    private static MoveResult sweepMove(Location start, Vector displacement, MarineMobType type) {
        int steps = MarineAirKinematics.sweepSteps(
                displacement.getX(), displacement.getY(), displacement.getZ());
        Vector increment = displacement.clone().multiply(1.0 / steps);
        Location current = start.clone();
        Location lastSafe = start.clone();

        for (int i = 0; i < steps; i++) {
            current.add(increment);
            if (isStrictWaterContact(current)) {
                return new MoveResult(current.clone(), true, false);
            }
            if (collidesAt(current) || MarineCollisionGeometry.bodyCollides(current, type)) {
                return new MoveResult(lastSafe, false, true);
            }
            lastSafe = current.clone();
        }
        return new MoveResult(current, false, false);
    }

    private void steerRiddenOrca(Horse horse, Player pilot, MarineMobService.MarineMob mob) {
        Vector nativeVelocity = horse.getVelocity();
        Vector nativeHorizontal = nativeVelocity.clone().setY(0.0);
        double nativeHorizontalSpeed = nativeHorizontal.length();

        Vector look = pilot.getEyeLocation().getDirection();
        if (look.lengthSquared() < DIRECTION_EPSILON) {
            look = forwardFromYaw(pilot.getLocation().getYaw());
        } else {
            look.normalize();
        }
        Vector lookHorizontal = look.clone().setY(0.0);
        if (lookHorizontal.lengthSquared() < DIRECTION_EPSILON) {
            lookHorizontal = forwardFromYaw(pilot.getLocation().getYaw());
        } else {
            lookHorizontal.normalize();
        }

        double alignment = nativeHorizontalSpeed < DIRECTION_EPSILON
                ? -1.0
                : nativeHorizontal.clone().normalize().dot(lookHorizontal);
        boolean detectedForward = MarineMotionTuning.hasForwardRiderIntent(
                nativeHorizontalSpeed, alignment);
        boolean conflictingInput = MarineMotionTuning.hasConflictingRiderIntent(
                nativeHorizontalSpeed, alignment);
        UUID id = horse.getUniqueId();

        if (conflictingInput) {
            lastForwardIntentTick.remove(id);
        } else if (detectedForward) {
            lastForwardIntentTick.put(id, serverTick);
        }

        boolean forwardInput = detectedForward || (!conflictingInput
                && MarineMotionTuning.forwardIntentGraceActive(
                serverTick, lastForwardIntentTick.getOrDefault(id, -1L)));

        horse.setAI(true);
        horse.setGravity(false);
        if (!forwardInput) {
            horse.setVelocity(new Vector());
            horse.setRotation(pilot.getLocation().getYaw(),
                    (float) clamp(pilot.getLocation().getPitch(), -70.0, 70.0));
            return;
        }

        Location location = horse.getLocation();
        double speed = mob.riddenSpeedBlocksPerTick();
        boolean shallow = shallowPool(location);
        boolean breachReady = isWithinOneBlockOfSurface(location) && look.getY() > 0.20;
        double maxVertical = breachReady
                ? BREACH_RIDER_MAX_VERTICAL
                : shallow ? SHALLOW_RIDER_MAX_VERTICAL : DEEP_RIDER_MAX_VERTICAL;
        double vertical = clamp(look.getY() * speed, -maxVertical, maxVertical);

        if (shallow && !breachReady) {
            if (vertical > 0.0 && !isWaterAt(location.clone().add(0.0, 0.85, 0.0))) {
                vertical = Math.min(vertical, 0.035);
            }
            if (vertical < 0.0 && !isWaterAt(location.clone().add(0.0, -0.85, 0.0))) {
                vertical = Math.max(vertical, -0.025);
            }
        }

        double horizontalSpeed = Math.sqrt(Math.max(0.0, speed * speed - vertical * vertical));
        Vector displacement = lookHorizontal.multiply(horizontalSpeed).setY(vertical);
        Location destination = sweepRiddenMove(location, displacement, pilot.getLocation().getYaw());
        destination.setYaw(pilot.getLocation().getYaw());
        destination.setPitch((float) clamp(pilot.getLocation().getPitch(), -70.0, 70.0));
        horse.teleport(destination);
        horse.setVelocity(new Vector());
    }

    private static Location sweepRiddenMove(Location start, Vector displacement, float travelYaw) {
        int steps = Math.max(1, (int) Math.ceil(displacement.length() / RIDDEN_SWEEP_STEP));
        Vector increment = displacement.clone().multiply(1.0 / steps);
        Location current = start.clone();
        Location lastSafe = start.clone();

        // If an orca is already partly inside terrain, allow movement that reduces the
        // overlap so the rider can escape instead of becoming permanently pinned.
        int toleratedBodyScore = MarineCollisionGeometry.bodyCollisionScore(start, MarineMobType.ORCA);
        for (int i = 0; i < steps; i++) {
            current.add(increment);
            current.setYaw(travelYaw);
            if (collidesAt(current)) {
                return lastSafe;
            }
            int bodyScore = MarineCollisionGeometry.bodyCollisionScore(current, MarineMobType.ORCA);
            if (toleratedBodyScore == 0) {
                if (bodyScore > 0) {
                    return lastSafe;
                }
            } else if (bodyScore > toleratedBodyScore) {
                return lastSafe;
            } else {
                toleratedBodyScore = Math.min(toleratedBodyScore, bodyScore);
            }
            lastSafe = current.clone();
        }
        return current;
    }

    private static Player firstPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean shallowPool(Location location) {
        return isWaterAt(location)
                && isWaterAt(location.clone().add(0.0, -1.0, 0.0))
                && !isWaterAt(location.clone().add(0.0, -2.0, 0.0));
    }

    private static boolean isWithinOneBlockOfSurface(Location location) {
        if (!isWaterAt(location)) {
            return false;
        }
        return !isWaterAt(location.clone().add(0.0, 1.35, 0.0))
                || !isWaterAt(location.clone().add(0.0, 2.05, 0.0));
    }

    private static boolean hasClearJumpColumn(Location location, int height) {
        for (int y = 1; y <= height; y++) {
            Block block = location.clone().add(0.0, y, 0.0).getBlock();
            if (block.getType().isSolid() && !block.isPassable()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSolidSupport(Location location) {
        Location probe = location.clone().add(0.0, -SUPPORT_PROBE, 0.0);
        Block block = probe.getBlock();
        return block.getType().isSolid() && !block.isPassable();
    }

    private static boolean collidesAt(Location location) {
        return solidCollision(location)
                || solidCollision(location.clone().add(0.0, 0.90, 0.0))
                || solidCollision(location.clone().add(0.0, 1.65, 0.0));
    }

    private static boolean solidCollision(Location location) {
        Block block = location.getBlock();
        return block.getType().isSolid() && !block.isPassable();
    }

    private static boolean isStrictWaterContact(Location location) {
        return isWaterAt(location)
                || isWaterAt(location.clone().add(0.0, 0.35, 0.0));
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

    private static Vector forwardFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class AirState {
        private double horizontalX;
        private double vertical;
        private double horizontalZ;

        private AirState(double horizontalX, double vertical, double horizontalZ) {
            this.horizontalX = horizontalX;
            this.vertical = vertical;
            this.horizontalZ = horizontalZ;
        }

        private static AirState from(Vector velocity) {
            return new AirState(velocity.getX(), velocity.getY(), velocity.getZ());
        }
    }

    private static final class BreachLaunch {
        private final double horizontalX;
        private final double vertical;
        private final double horizontalZ;
        private int waterTicksRemaining;

        private BreachLaunch(double horizontalX, double vertical, double horizontalZ, int waterTicksRemaining) {
            this.horizontalX = horizontalX;
            this.vertical = vertical;
            this.horizontalZ = horizontalZ;
            this.waterTicksRemaining = waterTicksRemaining;
        }
    }

    private record MoveResult(Location location, boolean enteredWater, boolean hitSolid) {
    }
}
