package com.sagakenichi.freelifemarine;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BedrockFallbackProfileTest {

    @Test
    void everyMarineTypeHasACompactFallbackModel() {
        for (MarineMobType type : MarineMobType.values()) {
            List<BedrockFallbackProfile.Part> parts = BedrockFallbackProfile.forType(type);
            assertFalse(parts.isEmpty());
            assertTrue(parts.size() <= 24, "Fallback model should stay lightweight for " + type);
        }
    }

    @Test
    void orcaFallbackKeepsBlackBodyAndWhiteMarkings() {
        List<BedrockFallbackProfile.Part> parts = BedrockFallbackProfile.forType(MarineMobType.ORCA);
        assertTrue(parts.stream().anyMatch(part -> part.material() == Material.BLACK_CONCRETE));
        assertTrue(parts.stream().anyMatch(part -> part.material() == Material.WHITE_CONCRETE));
        assertTrue(parts.stream().mapToDouble(BedrockFallbackProfile.Part::forward).max().orElseThrow() > 4.0);
        assertTrue(parts.stream().mapToDouble(BedrockFallbackProfile.Part::forward).min().orElseThrow() < -4.0);
    }
}
