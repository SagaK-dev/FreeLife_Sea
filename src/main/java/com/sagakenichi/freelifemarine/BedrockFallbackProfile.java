package com.sagakenichi.freelifemarine;

import org.bukkit.Material;

import java.util.List;

/**
 * Low-entity-count model used only for Bedrock clients connected through Geyser.
 * Bedrock does not currently render Java display entities, so these pieces are carried
 * by ordinary armor stands and use vanilla block items that Geyser can translate.
 */
final class BedrockFallbackProfile {

    private BedrockFallbackProfile() {
    }

    static List<Part> forType(MarineMobType type) {
        return switch (type) {
            case ORCA -> ORCA;
            case SHARK -> SHARK;
            case CRAB -> CRAB;
        };
    }

    private static final List<Part> ORCA = List.of(
            p(Material.BLACK_CONCRETE, 4.2, 0.18, 0.0, true),
            p(Material.BLACK_CONCRETE, 3.2, 0.20, 0.0, false),
            p(Material.BLACK_CONCRETE, 2.2, 0.22, 0.0, false),
            p(Material.BLACK_CONCRETE, 1.1, 0.24, 0.0, false),
            p(Material.BLACK_CONCRETE, 0.0, 0.24, 0.0, false),
            p(Material.BLACK_CONCRETE, -1.1, 0.22, 0.0, false),
            p(Material.BLACK_CONCRETE, -2.2, 0.18, 0.0, false),
            p(Material.BLACK_CONCRETE, -3.2, 0.12, 0.0, false),
            p(Material.BLACK_CONCRETE, -4.1, 0.08, 0.0, true),
            p(Material.BLACK_CONCRETE, 1.2, 0.10, 1.0, true),
            p(Material.BLACK_CONCRETE, 1.2, 0.10, -1.0, true),
            p(Material.BLACK_CONCRETE, -0.3, 1.22, 0.0, true),
            p(Material.BLACK_CONCRETE, -4.65, 0.08, 0.82, true),
            p(Material.BLACK_CONCRETE, -4.65, 0.08, -0.82, true),
            p(Material.WHITE_CONCRETE, 3.0, -0.48, 0.0, true),
            p(Material.WHITE_CONCRETE, 1.7, -0.58, 0.0, true),
            p(Material.WHITE_CONCRETE, 0.4, -0.62, 0.0, true),
            p(Material.WHITE_CONCRETE, -0.9, -0.56, 0.0, true),
            p(Material.WHITE_CONCRETE, 3.25, 0.38, 0.72, true),
            p(Material.WHITE_CONCRETE, 3.25, 0.38, -0.72, true)
    );

    private static final List<Part> SHARK = List.of(
            p(Material.LIGHT_GRAY_CONCRETE, 3.2, 0.12, 0.0, true),
            p(Material.GRAY_CONCRETE, 2.3, 0.14, 0.0, false),
            p(Material.GRAY_CONCRETE, 1.3, 0.16, 0.0, false),
            p(Material.GRAY_CONCRETE, 0.2, 0.16, 0.0, false),
            p(Material.GRAY_CONCRETE, -0.9, 0.14, 0.0, false),
            p(Material.GRAY_CONCRETE, -1.9, 0.10, 0.0, true),
            p(Material.GRAY_CONCRETE, -2.8, 0.06, 0.0, true),
            p(Material.GRAY_CONCRETE, 0.2, 0.78, 0.0, true),
            p(Material.GRAY_CONCRETE, 0.7, -0.08, 0.92, true),
            p(Material.GRAY_CONCRETE, 0.7, -0.08, -0.92, true),
            p(Material.GRAY_CONCRETE, -3.55, 0.52, 0.0, true),
            p(Material.GRAY_CONCRETE, -3.55, -0.42, 0.0, true),
            p(Material.WHITE_CONCRETE, 2.2, -0.45, 0.0, true),
            p(Material.WHITE_CONCRETE, 0.8, -0.52, 0.0, true),
            p(Material.WHITE_CONCRETE, -0.6, -0.45, 0.0, true)
    );

    private static final List<Part> CRAB = List.of(
            p(Material.RED_CONCRETE, 0.0, 0.18, 0.0, false),
            p(Material.RED_CONCRETE, 0.35, 0.18, 0.65, true),
            p(Material.RED_CONCRETE, 0.35, 0.18, -0.65, true),
            p(Material.RED_CONCRETE, -0.35, 0.12, 0.62, true),
            p(Material.RED_CONCRETE, -0.35, 0.12, -0.62, true),
            p(Material.BLACK_CONCRETE, 0.38, 0.44, 0.28, true),
            p(Material.BLACK_CONCRETE, 0.38, 0.44, -0.28, true)
    );

    private static Part p(Material material, double forward, double up, double right, boolean small) {
        return new Part(material, forward, up, right, small);
    }

    record Part(Material material, double forward, double up, double right, boolean small) {
        Part {
            if (material == null) {
                throw new IllegalArgumentException("Bedrock fallback material is required");
            }
        }
    }
}
