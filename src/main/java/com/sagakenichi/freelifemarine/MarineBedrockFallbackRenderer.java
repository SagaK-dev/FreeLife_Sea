package com.sagakenichi.freelifemarine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bedrock-compatible visual fallback for Java display-entity models.
 *
 * <p>Geyser cannot currently translate BlockDisplay entities to Bedrock. When a Geyser
 * or Floodgate player is online, this renderer mirrors each marine mob with a small set
 * of vanilla armor stands wearing ordinary block items. Java players never see these
 * fallback entities; their original BlockDisplay model remains unchanged.</p>
 */
final class MarineBedrockFallbackRenderer implements Listener {

    private static final double NORMAL_HELMET_Y_OFFSET = 1.45;
    private static final double SMALL_HELMET_Y_OFFSET = 0.72;

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private final BedrockClientDetector bedrockClients = new BedrockClientDetector();
    private final Map<UUID, FallbackModel> models = new HashMap<>();
    private BukkitTask task;
    private long serverTick;

    MarineBedrockFallbackRenderer(JavaPlugin plugin, MarineMobService mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAllModels();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> syncVisibility(player), 2L);
    }

    private void tick() {
        serverTick++;
        if (serverTick % 100L == 1L) {
            bedrockClients.refresh();
        }

        boolean bedrockOnline = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bedrockClients.isBedrockPlayer(player)) {
                bedrockOnline = true;
                break;
            }
        }

        if (!bedrockOnline) {
            if (!models.isEmpty()) {
                removeAllModels();
            }
            return;
        }

        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Horse.class, Slime.class)) {
                MarineMobService.MarineMob mob = mobs.find(entity);
                if (mob == null || !mob.id().equals(entity.getUniqueId())) {
                    continue;
                }

                UUID id = entity.getUniqueId();
                seen.add(id);
                FallbackModel model = models.get(id);
                if (model == null || !model.isUsable(world, mob.type())) {
                    if (model != null) {
                        model.remove();
                    }
                    model = createModel(world, mob);
                    models.put(id, model);
                    syncVisibilityForModel(model);
                }
                updateModel(model, mob.location());
            }
        }

        models.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().remove();
            return true;
        });

        if (serverTick % 20L == 1L) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                syncVisibility(player);
            }
        }
    }

    private FallbackModel createModel(World world, MarineMobService.MarineMob mob) {
        List<FallbackPiece> pieces = new ArrayList<>();
        Location base = mob.location();
        for (BedrockFallbackProfile.Part part : BedrockFallbackProfile.forType(mob.type())) {
            Location target = relative(base, part.forward(), part.up(), part.right());
            ArmorStand stand = createStand(world, target, part);
            pieces.add(new FallbackPiece(part, stand));
        }
        return new FallbackModel(mob.type(), world.getUID(), pieces);
    }

    private static ArmorStand createStand(World world, Location target, BedrockFallbackProfile.Part part) {
        double helmetOffset = part.small() ? SMALL_HELMET_Y_OFFSET : NORMAL_HELMET_Y_OFFSET;
        Location standLocation = target.clone().add(0.0, -helmetOffset, 0.0);
        standLocation.setPitch(0.0F);
        return world.spawn(standLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(part.small());
            stand.setMarker(false);
            stand.setGravity(false);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setHeadPose(EulerAngle.ZERO);
            stand.setCanPickupItems(false);
            EntityEquipment equipment = stand.getEquipment();
            equipment.setHelmet(new ItemStack(part.material()));
        });
    }

    private static void updateModel(FallbackModel model, Location base) {
        for (FallbackPiece piece : model.pieces) {
            ArmorStand stand = piece.stand;
            if (!stand.isValid()) {
                continue;
            }
            BedrockFallbackProfile.Part part = piece.part;
            Location target = relative(base, part.forward(), part.up(), part.right());
            double helmetOffset = part.small() ? SMALL_HELMET_Y_OFFSET : NORMAL_HELMET_Y_OFFSET;
            target.add(0.0, -helmetOffset, 0.0);
            target.setYaw(base.getYaw());
            target.setPitch(0.0F);
            stand.teleport(target);
            stand.setRotation(base.getYaw(), 0.0F);
        }
    }

    private void syncVisibilityForModel(FallbackModel model) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncVisibility(player, model);
        }
    }

    private void syncVisibility(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (FallbackModel model : models.values()) {
            syncVisibility(player, model);
        }
    }

    private void syncVisibility(Player player, FallbackModel model) {
        boolean bedrock = bedrockClients.isBedrockPlayer(player);
        for (FallbackPiece piece : model.pieces) {
            if (!piece.stand.isValid()) {
                continue;
            }
            if (bedrock) {
                player.showEntity(plugin, piece.stand);
            } else {
                player.hideEntity(plugin, piece.stand);
            }
        }
    }

    private void removeAllModels() {
        for (FallbackModel model : models.values()) {
            model.remove();
        }
        models.clear();
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

    private static final class FallbackModel {
        private final MarineMobType type;
        private final UUID worldId;
        private final List<FallbackPiece> pieces;

        private FallbackModel(MarineMobType type, UUID worldId, List<FallbackPiece> pieces) {
            this.type = type;
            this.worldId = worldId;
            this.pieces = List.copyOf(pieces);
        }

        private boolean isUsable(World world, MarineMobType expectedType) {
            if (world == null || !worldId.equals(world.getUID()) || type != expectedType) {
                return false;
            }
            for (FallbackPiece piece : pieces) {
                if (!piece.stand.isValid()) {
                    return false;
                }
            }
            return true;
        }

        private void remove() {
            for (FallbackPiece piece : pieces) {
                if (piece.stand.isValid()) {
                    piece.stand.remove();
                }
            }
        }
    }

    private record FallbackPiece(BedrockFallbackProfile.Part part, ArmorStand stand) {
    }
}
