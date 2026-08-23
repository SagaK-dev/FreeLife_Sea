package com.sagakenichi.freelifemarine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class MarineCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_ACTIONS = List.of("spawn", "call", "speed", "jump", "food", "show");
    private static final List<String> MOB_NAMES = List.of("shark", "orca", "crab");
    private static final List<String> SHOW_ACTIONS = List.of(
            "start", "stop", "status", "reload", "list",
            "set-center", "set-facing", "set-time", "add-time", "remove-time", "enable", "disable"
    );

    private final JavaPlugin plugin;
    private final MarineMobService mobs;
    private final MarineFood food;
    private final OrcaShowManager shows;

    public MarineCommand(JavaPlugin plugin, MarineMobService mobs, MarineFood food, OrcaShowManager shows) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.food = food;
        this.shows = shows;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> handleSpawn(sender, label, args);
            case "food" -> handleFood(sender, label, args);
            case "show" -> handleShow(sender, label, args);
            case "call" -> handleCall(sender, label, args);
            case "speed", "ride-speed" -> handleRideSpeed(sender, label, args);
            case "jump", "jump-height" -> handleJumpHeight(sender, label, args);
            default -> {
                sender.sendMessage("§cUnknown subcommand: " + args[0]);
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean handleSpawn(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof BlockCommandSender) && !sender.hasPermission("freelifemarine.spawn")) {
            sender.sendMessage("§cYou do not have permission to spawn marine mobs.");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("§eUsage: /" + label + " spawn <shark|orca|crab> [player]");
            return true;
        }

        MarineMobType type = parseMobType(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown marine mob '" + args[1] + "'. Use shark, orca, or crab.");
            return true;
        }

        Player targetPlayer = null;
        if (args.length == 3) {
            targetPlayer = Bukkit.getPlayerExact(args[2]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage("§cPlayer '" + args[2] + "' is not online.");
                return true;
            }
        }

        String source = sender.getName();
        String worldName = targetPlayer != null ? targetPlayer.getWorld().getName()
                : sender instanceof Player player ? player.getWorld().getName()
                : sender instanceof BlockCommandSender block ? block.getBlock().getWorld().getName()
                : "<no-world>";
        plugin.getLogger().info("Marine spawn command accepted: sender=" + source
                + ", mob=" + type.name().toLowerCase(Locale.ROOT)
                + ", world=" + worldName
                + (targetPlayer == null ? "" : ", target=" + targetPlayer.getName()));

        MarineMobService.MarineMob mob;
        try {
            if (targetPlayer != null) {
                mob = mobs.spawn(targetPlayer, type);
            } else if (sender instanceof Player player) {
                mob = mobs.spawn(player, type);
            } else if (sender instanceof BlockCommandSender blockSender) {
                Location spawn = blockSender.getBlock().getLocation().clone().add(0.5, 1.0, 0.5);
                mob = mobs.spawnAt(spawn, type, null, null);
            } else {
                sender.sendMessage("§cConsole has no spawn position. Use /" + label
                        + " spawn <shark|orca|crab> <player> or run it from a command block.");
                return true;
            }
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Marine spawn failed after command was accepted: sender=" + source
                            + ", mob=" + type.name().toLowerCase(Locale.ROOT)
                            + ", world=" + worldName, ex);
            sender.sendMessage("§cFailed to spawn " + type.displayName()
                    + ". The failure was written to the server log by FreeLifeMarineMobs.");
            return true;
        }

        plugin.getLogger().info("Marine spawn completed: mob=" + type.name().toLowerCase(Locale.ROOT)
                + ", id=" + mob.id() + ", world=" + worldName);
        sender.sendMessage("§aSpawned " + type.displayName() + " with " + (int) type.maxHealth() + " health.");
        if (mob.type() == MarineMobType.ORCA) {
            sender.sendMessage("§7Ride the orca, then use /" + label + " speed <1-50> to change its riding speed.");
        }
        return true;
    }

    private static MarineMobType parseMobType(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ouca", "ocra", "シャチ" -> MarineMobType.ORCA;
            case "サメ" -> MarineMobType.SHARK;
            case "カニ" -> MarineMobType.CRAB;
            default -> MarineMobType.fromInput(normalized);
        };
    }

    private boolean handleCall(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.call")) {
            sender.sendMessage("§cYou do not have permission to call orcas.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§eUsage: /" + label + " call (player only)");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§eUsage: /" + label + " call");
            return true;
        }
        sender.sendMessage(mobs.callNearestOrca(player));
        return true;
    }

    private boolean handleRideSpeed(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.tune")) {
            sender.sendMessage("§cYou do not have permission to tune orcas.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§eUsage: /" + label + " speed <blocks-per-second> (player only)");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§eUsage: /" + label + " speed <blocks-per-second>");
            return true;
        }
        Double value = parseDouble(args[1]);
        if (value == null || !MarineMotionTuning.isValidRiddenSpeed(value)) {
            sender.sendMessage("§cSpeed must be from " + trim(MarineMotionTuning.MIN_ORCA_RIDDEN_BLOCKS_PER_SECOND)
                    + " to " + trim(MarineMotionTuning.MAX_ORCA_RIDDEN_BLOCKS_PER_SECOND) + " blocks/second.");
            return true;
        }
        MarineMobService.MarineMob mob = mountedDriverOrca(player);
        if (mob == null) {
            sender.sendMessage("§cYou must be riding the orca in the driver seat before changing its speed.");
            return true;
        }
        mobs.setRiddenSpeed(mob, value);
        sender.sendMessage("§aOrca ride speed set to " + trim(value) + " blocks/second.");
        return true;
    }

    private boolean handleJumpHeight(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.tune")) {
            sender.sendMessage("§cYou do not have permission to tune orcas.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§eUsage: /" + label + " jump <3-13> (player only)");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§eUsage: /" + label + " jump <3-13>");
            return true;
        }
        Integer value = parseInt(args[1]);
        if (value == null || !MarineMotionTuning.isValidJumpHeight(value)) {
            sender.sendMessage("§cJump height must be from " + MarineMotionTuning.MIN_ORCA_JUMP_HEIGHT
                    + " to " + MarineMotionTuning.MAX_ORCA_JUMP_HEIGHT + " blocks.");
            return true;
        }
        MarineMobService.MarineMob mob = mountedDriverOrca(player);
        if (mob == null) {
            sender.sendMessage("§cYou must be riding the orca in the driver seat before changing its jump setting.");
            return true;
        }
        mobs.setJumpHeight(mob, value);
        sender.sendMessage("§aOrca jump setting set to " + value + " blocks.");
        return true;
    }

    private MarineMobService.MarineMob mountedDriverOrca(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return null;
        MarineMobService.MarineMob mob = mobs.find(vehicle);
        if (mob == null || mob.type() != MarineMobType.ORCA || !mob.id().equals(vehicle.getUniqueId())) return null;
        return mobs.isUsable(mob) ? mob : null;
    }

    private boolean handleFood(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.food")) {
            sender.sendMessage("§cYou do not have permission to receive marine food.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command must be run by a player.");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage("§eUsage: /" + label + " food [1-64]");
            return true;
        }
        int amount = 1;
        if (args.length == 2) {
            Integer parsed = parseInt(args[1]);
            if (parsed == null) {
                player.sendMessage("§cAmount must be a number from 1 to 64.");
                return true;
            }
            amount = parsed;
        }
        if (amount < 1 || amount > 64) {
            player.sendMessage("§cAmount must be from 1 to 64.");
            return true;
        }
        food.give(player, amount);
        player.sendMessage("§b海の餌 §fx" + amount + " を受け取りました。手に持つか、水中へ投げて使えます。");
        return true;
    }

    private boolean handleShow(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.show")) {
            sender.sendMessage("§cYou do not have permission to manage orca shows.");
            return true;
        }
        if (args.length < 2) {
            sendShowUsage(sender, label);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                String id = args.length >= 3 ? args[2] : null;
                if (sender instanceof Player player) sender.sendMessage(shows.startShow(id, player));
                else sender.sendMessage(shows.startShow(id));
            }
            case "stop" -> sender.sendMessage(shows.stopShow());
            case "status" -> sender.sendMessage(shows.status());
            case "reload" -> {
                shows.reload();
                sender.sendMessage("Reloaded orca show configuration.");
            }
            case "list" -> {
                List<String> ids = shows.showIds();
                sender.sendMessage(ids.isEmpty() ? "No shows are configured." : "Configured shows: " + String.join(", ", ids));
            }
            case "set-center" -> {
                if (!(sender instanceof Player player)) sender.sendMessage("set-center must be run by a player at the desired pool center.");
                else sender.sendMessage(shows.setCenter(player, args.length >= 3 ? args[2] : null));
            }
            case "set-facing" -> {
                if (!(sender instanceof Player player)) sender.sendMessage("set-facing must be run by a player looking toward the show direction.");
                else sender.sendMessage(shows.setFacing(player, args.length >= 3 ? args[2] : null));
            }
            case "set-time" -> {
                if (args.length < 3) sender.sendMessage("Usage: /" + label + " show set-time <HH:mm> [id]");
                else sender.sendMessage(shows.setSingleTime(args.length >= 4 ? args[3] : null, args[2]));
            }
            case "add-time" -> {
                if (args.length < 3) sender.sendMessage("Usage: /" + label + " show add-time <HH:mm> [id]");
                else sender.sendMessage(shows.addTime(args.length >= 4 ? args[3] : null, args[2]));
            }
            case "remove-time" -> {
                if (args.length < 3) sender.sendMessage("Usage: /" + label + " show remove-time <HH:mm> [id]");
                else sender.sendMessage(shows.removeTime(args.length >= 4 ? args[3] : null, args[2]));
            }
            case "enable" -> sender.sendMessage(shows.setEnabled(args.length >= 3 ? args[2] : null, true));
            case "disable" -> sender.sendMessage(shows.setEnabled(args.length >= 3 ? args[2] : null, false));
            default -> sendShowUsage(sender, label);
        }
        return true;
    }

    private static void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§6FreeLife Sea commands:");
        sender.sendMessage("§e/" + label + " spawn <shark|orca|crab> [player]");
        sender.sendMessage("§e/" + label + " call");
        sender.sendMessage("§e/" + label + " speed <1-50> | jump <3-13> §7(while driving an orca)");
        sender.sendMessage("§e/" + label + " food [1-64]");
        sender.sendMessage("§e/" + label + " show <...>");
    }

    private static void sendShowUsage(CommandSender sender, String label) {
        sender.sendMessage("/" + label + " show start [id] | stop | status | list | reload");
        sender.sendMessage("/" + label + " show set-center [id] | set-facing [id]");
        sender.sendMessage("/" + label + " show set-time <HH:mm> [id]");
        sender.sendMessage("/" + label + " show add-time <HH:mm> [id] | remove-time <HH:mm> [id]");
        sender.sendMessage("/" + label + " show enable [id] | disable [id]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matchPrefix(ROOT_ACTIONS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return matchPrefix(MOB_NAMES, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("speed") || args[0].equalsIgnoreCase("ride-speed"))) {
            return matchPrefix(List.of("1", "10", "20", "30", "40", "50"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("jump") || args[0].equalsIgnoreCase("jump-height"))) {
            return matchPrefix(List.of("3", "5", "8", "10", "13"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("food")) {
            return matchPrefix(List.of("1", "8", "16", "32", "64"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            return matchPrefix(SHOW_ACTIONS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show")
                && List.of("start", "set-center", "set-facing", "enable", "disable")
                .contains(args[1].toLowerCase(Locale.ROOT))) {
            return matchPrefix(shows.showIds(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show")
                && List.of("set-time", "add-time", "remove-time").contains(args[1].toLowerCase(Locale.ROOT))) {
            return matchPrefix(List.of("10:00", "13:00", "15:30"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("show")
                && List.of("set-time", "add-time", "remove-time").contains(args[1].toLowerCase(Locale.ROOT))) {
            return matchPrefix(shows.showIds(), args[3]);
        }
        return List.of();
    }

    private static List<String> matchPrefix(List<String> values, String rawPrefix) {
        String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
