package com.sagakenichi.freelifemarine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Final autonomous swimming pass for unpiloted aquatic marine mobs.
 *
 * <p>The motion intentionally follows the shape of vanilla fish AI: pick a nearby random
 * water destination, ease toward the requested speed, turn toward the destination instead
 * of snapping, add a tiny submerged buoyancy term, and gently correct vertical position.
 * Unlike vanilla tropical fish, sharks and orcas do not flee from nearby players.</p>
 */
final class MarineNaturalBehaviorController {

    private static final double DIRECTION_EPSILON = 1.0E-6;
    private static final double STRONG_VERTICAL_MANEUVER = 0.075;
    private static final double BODY_MARGIN = 0.10;
    private static final double WAYPOINT_REACHED_DISTANCE = 1.40;
    private static final int WAYPOINT_STAGNANT_TICKS = 18;
    private static final double WAYPOINT_PROGRESS_EPSILON = 0.035;

    // Vanilla FishMoveControl / FishEntity style terms.
    private static final double SPEED_LERP = 0.125;
    private static final double WATER_DRAG = 0.90;
    private static final double SUBMERGED_BUOYANCY = 0.005;
    private static final double TARGET_VERTICAL_STEER = 0.10;
    private static final double MAX_VERTICAL_SPEED = 0.060;

    private static final double[] WATER_SEARCH_ANGLES = {
            0.0, 12.0, -12.0, 24.0, -24.0, 40.0, -40.0,
            62.0, -62.0, 90.0, -90.0, 130.0, -130.0, 180.0
    };

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private final Map<UUID, SwimState> swimStates = new HashMap<>();
    private BukkitTask task;
    private long serverTick;

    MarineNaturalBehaviorController(JavaPlugin plugin, MarineMobService mobs) {
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
        swimStates.clear();
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
                if (mob.type().movementStyle() != MarineMobType.MovementStyle.AQUATIC) {
                    continue;
                }

                breakCollidingBoats(entity, mob.type());

                if (mob.showControlled() || mob.commandControlled() || hasPlayerPassenger(entity)) {
                    swimStates.remove(id);
                    continue;
                }

                Location location = entity.getLocation();
                if (!isWaterContact(location)) {
                    swimStates.remove(id);
                    continue;
                }

                /*
                 * Autonomous sharks/orcas now stay in the water like vanilla tropical fish.
                 * Other controllers run earlier in the tick and may have started an automatic
                 * breach/dive. Collapse that large Y impulse before applying fish-style swim.
                 * Show jumps, /marine call jumps and rider breaches are skipped above and keep
                 * their existing behavior.
                 */
                Vector velocity = entity.getVelocity();
                if (Math.abs(velocity.getY()) > STRONG_VERTICAL_MANEUVER) {
                    velocity.setY(clamp(velocity.getY() * 0.15, -0.035, 0.035));
                    entity.setVelocity(velocity);
                }

                SwimState state = swimStates.computeIfAbsent(id,
                        ignored -> SwimState.create(mob.type(), serverTick));
                state.updatePace(mob.type(), serverTick);
                applyFishStyleSwim(entity, mob, state);
            }
        }

        swimStates.keySet().retainAll(seen);
    }

    private void applyFishStyleSwim(Entity entity, MarineMobService.MarineMob mob, SwimState state) {
        Location location = entity.getLocation();
        Vector velocity = entity.getVelocity();
        double currentHorizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());

        boolean stagnating = state.isStagnating(location);
        int bodyCollisionScore = MarineCollisionGeometry.bodyCollisionScore(location, mob.type());
        if (stagnating || bodyCollisionScore > 0) {
            state.invalidateRoamTarget();
            Vector escape = findEscapeDirection(location, velocity, mob.type(), bodyCollisionScore);
            if (escape != null) {
                applyEscapeMotion(entity, mob, state, escape, velocity);
                return;
            }
        }

        if (state.needsRoamTarget(location, serverTick)) {
            state.setRoamTarget(chooseRandomWaterTarget(location, mob.type()), mob.type(), serverTick);
        }

        Location target = state.roamTarget;
        Vector preferred = preferredHorizontalDirection(location, velocity, target);

        Vector openDirection = findOpenWaterDirection(location, preferred, mob.type());
        if (openDirection == null) {
            state.invalidateRoamTarget();
            Vector escape = findEscapeDirection(location, velocity, mob.type(), bodyCollisionScore);
            if (escape != null) {
                applyEscapeMotion(entity, mob, state, escape, velocity);
            } else {
                Vector coast = velocity.clone().multiply(0.72);
                coast.setY(clamp(coast.getY(), -0.025, 0.025));
                entity.setVelocity(coast);
            }
            return;
        }

        float desiredYaw = yawFromVector(openDirection);
        float yaw = turnTowards(location.getYaw(), desiredYaw,
                MarineNaturalMotionProfile.maxTurnDegreesPerTick(mob.type()));
        Vector facing = forwardFromYaw(yaw);

        double baseSpeed = MarineNaturalMotionProfile.baseCruiseBlocksPerTick(mob.type());
        double pulse = MarineNaturalMotionProfile.pacePulse(mob.type(), serverTick, state.phase);
        double targetSpeed = baseSpeed * state.pace * pulse;
        targetSpeed = clamp(targetSpeed,
                MarineNaturalMotionProfile.minCruiseBlocksPerTick(mob.type()),
                MarineNaturalMotionProfile.maxCruiseBlocksPerTick(mob.type()));

        // FishMoveControl uses a 0.125 lerp toward requested movement speed.
        double boundedCurrent = Math.min(currentHorizontalSpeed,
                MarineNaturalMotionProfile.maxCruiseBlocksPerTick(mob.type()) * 1.25);
        double nextSpeed = boundedCurrent + (targetSpeed - boundedCurrent) * SPEED_LERP;
        if (nextSpeed < 0.01) {
            nextSpeed = MarineNaturalMotionProfile.minCruiseBlocksPerTick(mob.type());
        }

        double vertical = fishVerticalVelocity(location, velocity.getY(), target, nextSpeed,
                mob.type(), state.phase);
        Vector natural = facing.multiply(nextSpeed).setY(vertical);
        entity.setVelocity(natural);
        entity.setRotation(yaw, (float) clamp(-vertical * 120.0, -10.0, 10.0));
    }

    private static Vector preferredHorizontalDirection(Location location, Vector velocity, Location target) {
        if (target != null && target.getWorld() == location.getWorld()) {
            Vector toward = target.toVector().subtract(location.toVector()).setY(0.0);
            if (toward.lengthSquared() >= DIRECTION_EPSILON) {
                return toward.normalize();
            }
        }

        Vector current = velocity.clone().setY(0.0);
        if (current.lengthSquared() >= DIRECTION_EPSILON) {
            return current.normalize();
        }
        return forwardFromYaw(location.getYaw());
    }

    private double fishVerticalVelocity(Location location, double currentVertical,
                                        Location target, double horizontalSpeed,
                                        MarineMobType type, double phase) {
        double vertical = currentVertical * WATER_DRAG + SUBMERGED_BUOYANCY;

        if (target != null && target.getWorld() == location.getWorld()) {
            double dx = target.getX() - location.getX();
            double dy = target.getY() - location.getY();
            double dz = target.getZ() - location.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > DIRECTION_EPSILON) {
                vertical += horizontalSpeed * (dy / distance) * TARGET_VERTICAL_STEER;
            }
        } else {
            // Vanilla FishEntity applies a tiny sink when it has no movement target.
            vertical -= SUBMERGED_BUOYANCY;
        }

        vertical += MarineNaturalMotionProfile.verticalWave(type, serverTick, phase);

        boolean waterAbove = isWaterAt(location.clone().add(0.0, 0.85, 0.0));
        boolean waterBelow = isWaterAt(location.clone().add(0.0, -0.85, 0.0));
        if (!waterAbove) {
            // Keep autonomous animals submerged instead of letting random swim breach.
            vertical = Math.min(vertical, -0.012);
        }
        if (!waterBelow) {
            vertical = Math.max(vertical, 0.012);
        }

        return clamp(vertical, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
    }

    private static Location chooseRandomWaterTarget(Location origin, MarineMobType type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double minDistance = MarineNaturalMotionProfile.minRoamDistance(type);
        double maxDistance = MarineNaturalMotionProfile.maxRoamDistance(type);
        double maxDepthChange = MarineNaturalMotionProfile.maxRoamDepthChange(type);

        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            double distance = random.nextDouble(minDistance, Math.nextUp(maxDistance));
            double y = random.nextDouble(-maxDepthChange, Math.nextUp(maxDepthChange));

            Vector travel = new Vector(Math.cos(angle), 0.0, Math.sin(angle)).multiply(distance);
            Location candidate = origin.clone().add(travel).add(0.0, y, 0.0);
            candidate.setYaw(yawFromVector(travel));

            Location water = nearestUsableWaterLayer(candidate, type);
            if (water != null) {
                return water;
            }
        }
        return null;
    }

    private static Location nearestUsableWaterLayer(Location candidate, MarineMobType type) {
        double[] offsets = {0.0, -1.0, 1.0, -2.0, 2.0, -3.0, 3.0, -4.0, 4.0};
        for (double offset : offsets) {
            Location probe = candidate.clone().add(0.0, offset, 0.0);
            if (!hasWaterRoom(probe)) {
                continue;
            }
            if (!MarineCollisionGeometry.bodyCollides(probe, type)) {
                return probe;
            }
        }
        return null;
    }

    private static Vector findOpenWaterDirection(Location location, Vector preferred, MarineMobType type) {
        double nearProbe = type == MarineMobType.ORCA ? 0.80 : 0.65;
        double farProbe = type == MarineMobType.ORCA ? 1.80 : 1.40;

        Vector unit = preferred.clone().setY(0.0);
        if (unit.lengthSquared() < DIRECTION_EPSILON) {
            unit = forwardFromYaw(location.getYaw());
        } else {
            unit.normalize();
        }

        int currentScore = MarineCollisionGeometry.bodyCollisionScore(location, type);
        Vector improvingDirection = null;
        int bestScore = currentScore;

        for (double degrees : WATER_SEARCH_ANGLES) {
            Vector candidate = rotateY(unit, Math.toRadians(degrees)).normalize();
            Location near = location.clone().add(candidate.clone().multiply(nearProbe));
            Location far = location.clone().add(candidate.clone().multiply(farProbe));
            float candidateYaw = yawFromVector(candidate);
            near.setYaw(candidateYaw);
            far.setYaw(candidateYaw);

            if (!hasWaterRoom(near) || !hasWaterRoom(far)) {
                continue;
            }

            if (currentScore == 0) {
                if (!MarineCollisionGeometry.bodyCollides(near, type)
                        && !MarineCollisionGeometry.bodyCollides(far, type)) {
                    return candidate;
                }
                continue;
            }

            int candidateScore = MarineCollisionGeometry.bodyCollisionScore(far, type);
            if (candidateScore < bestScore) {
                bestScore = candidateScore;
                improvingDirection = candidate;
                if (candidateScore == 0) {
                    return candidate;
                }
            }
        }
        return improvingDirection;
    }

    private static Vector findEscapeDirection(Location location, Vector velocity,
                                              MarineMobType type, int currentScore) {
        Vector base = velocity.clone().setY(0.0);
        if (base.lengthSquared() < DIRECTION_EPSILON) {
            base = forwardFromYaw(location.getYaw());
        } else {
            base.normalize();
        }

        double[] escapeAngles = {
                90.0, -90.0, 120.0, -120.0, 150.0, -150.0, 180.0, 55.0, -55.0
        };
        Vector best = null;
        int bestScore = currentScore > 0 ? currentScore : Integer.MAX_VALUE;

        for (double degrees : escapeAngles) {
            Vector candidate = rotateY(base, Math.toRadians(degrees)).normalize();
            Location near = location.clone().add(candidate.clone().multiply(0.90));
            Location far = location.clone().add(candidate.clone().multiply(2.20));
            float candidateYaw = yawFromVector(candidate);
            near.setYaw(candidateYaw);
            far.setYaw(candidateYaw);

            if (!hasWaterRoom(near) || !hasWaterRoom(far)) {
                continue;
            }

            int score = MarineCollisionGeometry.bodyCollisionScore(far, type);
            if (currentScore == 0 && score == 0) {
                return candidate;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean hasWaterRoom(Location location) {
        return isWaterAt(location)
                && (isWaterAt(location.clone().add(0.0, -0.65, 0.0))
                || isWaterAt(location.clone().add(0.0, 0.45, 0.0)));
    }

    private void applyEscapeMotion(Entity entity, MarineMobService.MarineMob mob, SwimState state,
                                   Vector escape, Vector previousVelocity) {
        float desiredYaw = yawFromVector(escape);
        float yaw = turnTowards(entity.getLocation().getYaw(), desiredYaw,
                MarineNaturalMotionProfile.maxTurnDegreesPerTick(mob.type()) * 1.6F);
        Vector direction = forwardFromYaw(yaw);

        double currentSpeed = Math.hypot(previousVelocity.getX(), previousVelocity.getZ());
        double target = Math.max(MarineNaturalMotionProfile.baseCruiseBlocksPerTick(mob.type()),
                currentSpeed * 0.80);
        target = Math.min(target, MarineNaturalMotionProfile.maxCruiseBlocksPerTick(mob.type()));
        double nextSpeed = currentSpeed + (target - currentSpeed) * 0.25;

        double vertical = clamp(previousVelocity.getY() * WATER_DRAG, -0.035, 0.035);
        Vector motion = direction.multiply(nextSpeed).setY(vertical);
        entity.setVelocity(motion);
        entity.setRotation(yaw, 0.0F);
        state.invalidateRoamTarget();
    }

    private void breakCollidingBoats(Entity anchor, MarineMobType type) {
        if (!MarineNaturalMotionProfile.breaksBoats(type)) {
            return;
        }
        double radius = MarineNaturalMotionProfile.collisionScanRadius(type);
        Location base = anchor.getLocation();

        for (Entity nearby : anchor.getWorld().getNearbyEntities(base, radius, 3.5, radius)) {
            if (!(nearby instanceof Boat boat) || !boat.isValid()) {
                continue;
            }
            if (!bodyOverlapsBoat(base, type, boat.getBoundingBox())) {
                continue;
            }
            breakBoat(boat);
        }
    }

    private static boolean bodyOverlapsBoat(Location base, MarineMobType type, BoundingBox boatBox) {
        for (MarineHitboxProfile.Hitbox hitbox : MarineHitboxProfile.forType(type)) {
            Location center = relative(base, hitbox.forward(), hitbox.up(), hitbox.right());
            double halfWidth = hitbox.width() * 0.5 + BODY_MARGIN;
            BoundingBox body = new BoundingBox(
                    center.getX() - halfWidth,
                    center.getY() - BODY_MARGIN,
                    center.getZ() - halfWidth,
                    center.getX() + halfWidth,
                    center.getY() + hitbox.height() + BODY_MARGIN,
                    center.getZ() + halfWidth);
            if (body.overlaps(boatBox)) {
                return true;
            }
        }
        return false;
    }

    private static void breakBoat(Boat boat) {
        Location effect = boat.getLocation().clone().add(0.0, 0.35, 0.0);
        World world = boat.getWorld();

        if (boat instanceof ChestBoat chestBoat) {
            for (ItemStack stack : chestBoat.getInventory().getContents()) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                world.dropItemNaturally(boat.getLocation(), stack.clone());
            }
            chestBoat.getInventory().clear();
        }

        boat.eject();
        boat.remove();
        world.spawnParticle(Particle.BLOCK, effect, 28,
                0.65, 0.35, 0.65, 0.08, Material.OAK_PLANKS.createBlockData());
        world.spawnParticle(Particle.SPLASH, effect, 18,
                0.70, 0.25, 0.70, 0.10);
        world.playSound(effect, Sound.BLOCK_WOOD_BREAK, 1.25F, 0.82F);
    }

    private static boolean hasPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
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

    private static Vector rotateY(Vector vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = vector.getX() * cos + vector.getZ() * sin;
        double z = -vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    private static float yawFromVector(Vector vector) {
        return (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
    }

    private static float turnTowards(float current, float target, float maxStep) {
        float difference = normalizeYaw(target - current);
        if (difference > 180.0F) {
            difference -= 360.0F;
        }
        difference = (float) clamp(difference, -maxStep, maxStep);
        return normalizeYaw(current + difference);
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SwimState {
        private final double phase;
        private double pace;
        private double targetPace;
        private long nextPaceChangeTick;
        private Location roamTarget;
        private long roamTargetExpiresTick;
        private double lastRoamDistance = Double.POSITIVE_INFINITY;
        private int stagnantTicks;

        private SwimState(double phase, double pace, double targetPace, long nextPaceChangeTick) {
            this.phase = phase;
            this.pace = pace;
            this.targetPace = targetPace;
            this.nextPaceChangeTick = nextPaceChangeTick;
        }

        private static SwimState create(MarineMobType type, long tick) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double pace = random.nextDouble(
                    MarineNaturalMotionProfile.minPace(type),
                    Math.nextUp(MarineNaturalMotionProfile.maxPace(type)));
            long next = tick + random.nextInt(
                    MarineNaturalMotionProfile.minPaceHoldTicks(type),
                    MarineNaturalMotionProfile.maxPaceHoldTicksExclusive(type));
            return new SwimState(random.nextDouble(0.0, Math.PI * 2.0), pace, pace, next);
        }

        private void updatePace(MarineMobType type, long tick) {
            if (tick >= nextPaceChangeTick) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                targetPace = random.nextDouble(
                        MarineNaturalMotionProfile.minPace(type),
                        Math.nextUp(MarineNaturalMotionProfile.maxPace(type)));
                nextPaceChangeTick = tick + random.nextInt(
                        MarineNaturalMotionProfile.minPaceHoldTicks(type),
                        MarineNaturalMotionProfile.maxPaceHoldTicksExclusive(type));
            }
            pace += (targetPace - pace) * 0.045;
        }

        private boolean needsRoamTarget(Location location, long tick) {
            return roamTarget == null
                    || roamTarget.getWorld() != location.getWorld()
                    || tick >= roamTargetExpiresTick
                    || roamTarget.distanceSquared(location)
                    <= WAYPOINT_REACHED_DISTANCE * WAYPOINT_REACHED_DISTANCE
                    || !isWaterAt(roamTarget);
        }

        private void setRoamTarget(Location target, MarineMobType type, long tick) {
            roamTarget = target == null ? null : target.clone();
            if (target == null) {
                roamTargetExpiresTick = tick + 10L;
            } else {
                roamTargetExpiresTick = tick + ThreadLocalRandom.current().nextInt(
                        MarineNaturalMotionProfile.minRoamTargetTicks(type),
                        MarineNaturalMotionProfile.maxRoamTargetTicksExclusive(type));
            }
            lastRoamDistance = Double.POSITIVE_INFINITY;
            stagnantTicks = 0;
        }

        private boolean isStagnating(Location location) {
            if (roamTarget == null || roamTarget.getWorld() != location.getWorld()) {
                lastRoamDistance = Double.POSITIVE_INFINITY;
                stagnantTicks = 0;
                return false;
            }

            double distance = Math.sqrt(roamTarget.distanceSquared(location));
            if (lastRoamDistance - distance >= WAYPOINT_PROGRESS_EPSILON) {
                stagnantTicks = 0;
            } else {
                stagnantTicks++;
            }
            lastRoamDistance = distance;
            return stagnantTicks >= WAYPOINT_STAGNANT_TICKS;
        }

        private void invalidateRoamTarget() {
            roamTarget = null;
            roamTargetExpiresTick = 0L;
            lastRoamDistance = Double.POSITIVE_INFINITY;
            stagnantTicks = 0;
        }
    }
}
