package com.sagakenichi.freelifemarine;

/**
 * Species-specific parameters for tropical-fish-style autonomous swimming.
 *
 * <p>The steering pattern follows vanilla fish AI, but travel distance, hold time and
 * speed are scaled for the much larger shark/orca bodies. Long roam legs keep them from
 * repeatedly turning in place after the cruise-speed increase.</p>
 */
final class MarineNaturalMotionProfile {

    private MarineNaturalMotionProfile() {
    }

    static double baseCruiseBlocksPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.48;   // 9.6 blocks/s
            case SHARK -> 0.40;  // 8.0 blocks/s
            case CRAB -> 0.05;
        };
    }

    static double minCruiseBlocksPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.32;   // 6.4 blocks/s
            case SHARK -> 0.28;  // 5.6 blocks/s
            case CRAB -> 0.04;
        };
    }

    static double maxCruiseBlocksPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.68;   // 13.6 blocks/s
            case SHARK -> 0.58;  // 11.6 blocks/s
            case CRAB -> 0.07;
        };
    }

    static double minPace(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.82;
            case SHARK -> 0.84;
            case CRAB -> 1.0;
        };
    }

    static double maxPace(MarineMobType type) {
        return switch (type) {
            case ORCA -> 1.10;
            case SHARK -> 1.12;
            case CRAB -> 1.0;
        };
    }

    static int minPaceHoldTicks(MarineMobType type) {
        return switch (type) {
            case ORCA -> 50;
            case SHARK -> 45;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static int maxPaceHoldTicksExclusive(MarineMobType type) {
        return switch (type) {
            case ORCA -> 141;
            case SHARK -> 126;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static double pacePulse(MarineMobType type, long tick, double phase) {
        double amplitude = switch (type) {
            case ORCA -> 0.035;
            case SHARK -> 0.045;
            case CRAB -> 0.0;
        };
        double frequency = switch (type) {
            case ORCA -> 0.036;
            case SHARK -> 0.043;
            case CRAB -> 0.0;
        };
        return 1.0 + Math.sin(tick * frequency + phase) * amplitude;
    }

    static double verticalWave(MarineMobType type, long tick, double phase) {
        double amplitude = switch (type) {
            case ORCA -> 0.0045;
            case SHARK -> 0.0050;
            case CRAB -> 0.0;
        };
        double frequency = switch (type) {
            case ORCA -> 0.052;
            case SHARK -> 0.061;
            case CRAB -> 0.0;
        };
        return Math.sin(tick * frequency + phase * 1.17) * amplitude;
    }

    static double minRoamDistance(MarineMobType type) {
        return switch (type) {
            case ORCA -> 10.0;
            case SHARK -> 8.0;
            case CRAB -> 0.0;
        };
    }

    static double maxRoamDistance(MarineMobType type) {
        return switch (type) {
            case ORCA -> 28.0;
            case SHARK -> 24.0;
            case CRAB -> 0.0;
        };
    }

    static double maxRoamDepthChange(MarineMobType type) {
        return switch (type) {
            case ORCA -> 5.0;
            case SHARK -> 5.5;
            case CRAB -> 0.0;
        };
    }

    static int minRoamTargetTicks(MarineMobType type) {
        return switch (type) {
            case ORCA -> 120;  // at least 6 seconds before expiry
            case SHARK -> 100; // at least 5 seconds before expiry
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static int maxRoamTargetTicksExclusive(MarineMobType type) {
        return switch (type) {
            case ORCA -> 261;  // up to about 13 seconds
            case SHARK -> 221; // up to about 11 seconds
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static float maxTurnDegreesPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 4.0F;
            case SHARK -> 6.0F;
            case CRAB -> 5.0F;
        };
    }

    static double collisionScanRadius(MarineMobType type) {
        return switch (type) {
            case ORCA -> 7.5;
            case SHARK -> 6.0;
            case CRAB -> 0.0;
        };
    }

    static boolean breaksBoats(MarineMobType type) {
        return type == MarineMobType.ORCA || type == MarineMobType.SHARK;
    }
}
