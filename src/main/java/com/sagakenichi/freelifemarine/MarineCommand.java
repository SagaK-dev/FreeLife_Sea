package com.sagakenichi.freelifemarine;

import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MarineCommand implements CommandExecutor, TabCompleter {

    private static final List<String> MOB_NAMES = List.of("shark", "orca", "crab");
    private static final List<String> SHOW_ACTIONS = List.of(
            "start", "stop", "status", "reload", "list",
            "set-center", "set-facing", "set-time", "add-time", "remove-time", "enable", "disable"
    );

    private final MarineMobService mobs;
    private final MarineFood food;
    private final OrcaShowManager shows;

    public MarineCommand(MarineMobService mobs, MarineFood food, OrcaShowManager shows) {
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
        if (args.length != 2) {
            sender.sendMessage("§eUsage: /" + label + " spawn <shark|orca|crab>");
            return true;
        }

        MarineMobType type = MarineMobType.fromInput(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown marine mob. Use shark, orca, or crab.");
            return true;
        }

        MarineMobService.MarineMob mob;
        try {
            if (sender instanceof Player player) {
                mob = mobs.spawn(player, type);
            } else if (sender instanceof BlockCommandSender blockSender) {
                Location spawn = blockSender.getBlock().getLocation().clone().add(0.5, 1.0, 0.5);
                mob = mobs.spawnAt(spawn, type, null, null);
            } else {
                sender.sendMessage("§cConsole has no spawn position. Run this from a player or command block.");
                return true;
            }
        } catch (RuntimeException ex) {
            sender.sendMessage("§cFailed to spawn " + type.displayName() + ". Check the server log for details.");
            throw ex;
        }

        sender.sendMessage("§aSpawned " + type.displayName() + " with " + (int) type.maxHealth() + " health.");
        if (mob.type() == MarineMobType.ORCA) {
            sender.sendMessage("§7Ride the orca, then use /" + label + " speed <1-50> to change its riding speed.");
        }
        return true;
    }

    private boolean handleCall(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.call")) {
            sender.sendMessage("You do not have permission to call orcas.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /" + label + " call (player only)");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("Usage: /" + label + " call");
            return true;
        }
        sender.sendMessage(mobs.callNearestOrca(player));
        return true;
    }

    private boolean handleRideSpeed(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.tune")) {
            sender.sendMessage("You do not have permission to tune orcas.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /" + label + " speed <blocks-per-second> (player only)");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " speed <blocks-per-second>");
            return true;
        }
        Double value = parseDouble(args[1]);
        if (value == null || !MarineMotionTuning.isValidRiddenSpeed(value)) {
            sender.sendMessage("Speed must be from " + trim(MarineMotionTuning.MIN_ORCA_RIDDEN_BLOCKS_PER_SECOND)
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
            sender.sendMessage("You do not have permission to tune orcas.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /" + label + " jump <3-13> (player only)");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " jump <3-13>");
            return true;
        }
        Integer value = parseInt(args[1]);
        if (value == null || !MarineMotionTuning.isValidJumpHeight(value)) {
            sender.sendMessage("Jump height must be from " + MarineMotionTuning.MIN_ORCA_JUMP_HEIGHT
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
        if (vehicle == null) {
            return null;
        }
        MarineMobService.MarineMob mob = mobs.find(vehicle);
        if (mob == null || mob.type() != MarineMobType.ORCA || !mob.id().equals(vehicle.getUniqueId())) {
            return null;
        }
        return mobs.isUsable(mob) ? mob : null;
    }

    private boolean handleFood(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.food")) {
            sender.sendMessage("You do not have permission to receive marine food.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be run by a player.");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage("Usage: /" + label + " food [1-64]");
            return true;
        }
        int amount = 1;
        if (args.length == 2) {
            Integer parsed = parseInt(args[1]);
            if (parsed == null) {
                player.sendMessage("Amount must be a number from 1 to 64.");
                return true;
            }
            amount = parsed;
        }
        if (amount < 1 || amount > 64) {
            player.sendMessage("Amount must be from 1 to 64.");
            return true;
        }
        food.give(player, amount);
        player.sendMessage("§b海の餌 §fx" + amount + " を受け取りました。手に持つか、水中へ投げて使えます。");
        return true;
    }

    private boolean handleShow(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("freelifemarine.show")) {
            sender.sendMessage("You do not have permission to manage orca shows.");
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
        sender.sendMessage("/" + label + " spawn <shark|orca|crab>");
        sender.sendMessage("/" + label + " call");
        sender.sendMessage("/" + label + " speed <1-50> | jump <3-13> (while driving an orca)");
        sender.sendMessage("/" + label + " food [1-64]");
        sender.sendMessage("/" + label + " show <...>");
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
            List<String> roots = new ArrayList<>();
            if (sender.hasPermission("freelifemarine.spawn")) roots.add("spawn");
            if (sender.hasPermission("freelifemarine.call")) roots.add("call");
            if (sender.hasPermission("freelifemarine.tune")) {
                roots.add("speed");
                roots.add("jump");
            }
            if (sender.hasPermission("freelifemarine.food")) roots.add("food");
            if (sender.hasPermission("freelifemarine.show")) roots.add("show");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return roots.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return MOB_NAMES.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("speed") || args[0].equalsIgnoreCase("ride-speed"))) {
            return List.of("1", "10", "20", "30", "40", "50").stream().filter(v -> v.startsWith(args[1])).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("jump") || args[0].equalsIgnoreCase("jump-height"))) {
            return List.of("3", "5", "8", "10", "13").stream().filter(v -> v.startsWith(args[1])).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("food")) {
            return List.of("1", "8", "16", "32", "64").stream().filter(value -> value.startsWith(args[1])).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("show")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return SHOW_ACTIONS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show")
                && List.of("start", "set-center", "set-facing", "enable", "disable").contains(args[1].toLowerCase(Locale.ROOT))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return shows.showIds().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("show")
                && List.of("set-time", "add-time", "remove-time").contains(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("10:00", "13:00", "15:30").stream().filter(value -> value.startsWith(args[2])).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("show")
                && List.of("set-time", "add-time", "remove-time").contains(args[1].toLowerCase(Locale.ROOT))) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return shows.showIds().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
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
