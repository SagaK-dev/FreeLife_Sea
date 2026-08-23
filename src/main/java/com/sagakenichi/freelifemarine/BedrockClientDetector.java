package com.sagakenichi.freelifemarine;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional bridge to Geyser/Floodgate. Reflection keeps FreeLife Sea usable on plain
 * Spigot servers while still allowing Bedrock-specific rendering when either API exists.
 */
final class BedrockClientDetector {

    private DetectionMethod geyser;
    private DetectionMethod floodgate;

    BedrockClientDetector() {
        refresh();
    }

    void refresh() {
        if (geyser == null) {
            geyser = resolve("org.geysermc.geyser.api.GeyserApi", "api", "isBedrockPlayer");
        }
        if (floodgate == null) {
            floodgate = resolve("org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer");
        }
    }

    boolean isBedrockPlayer(Player player) {
        return player != null && isBedrockPlayer(player.getUniqueId());
    }

    boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        if (geyser != null && geyser.test(uuid)) {
            return true;
        }
        return floodgate != null && floodgate.test(uuid);
    }

    boolean isAvailable() {
        return geyser != null || floodgate != null;
    }

    private static DetectionMethod resolve(String className, String accessorName, String testName) {
        try {
            Class<?> apiClass = Class.forName(className);
            Method accessor = apiClass.getMethod(accessorName);
            Object api = accessor.invoke(null);
            if (api == null) {
                return null;
            }
            Method test = apiClass.getMethod(testName, UUID.class);
            return new DetectionMethod(api, test);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private record DetectionMethod(Object api, Method test) {
        private boolean test(UUID uuid) {
            try {
                return Boolean.TRUE.equals(test.invoke(api, uuid));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }
}
