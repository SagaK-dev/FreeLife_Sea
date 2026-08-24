package com.sagakenichi.freelifemarine;

/**
 * Species-specific parameters for tropical-fish-style autonomous swimming.
 *
 * <p>The steering pattern follows vanilla fish AI, but the travel distances and speeds
 * are scaled for the much larger shark/orca bodies so they visibly cross a pool instead
 * of appearing to pivot in place.</p>
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
            case ORCA -> 35;
            case SHARK -> 30;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static int maxPaceHoldTicksExclusive(MarineMobType type) {
        return switch (type) {
            case ORCA -> 101;
            case SHARK -> 91;
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
            case ORCA -> 6.0;
            case SHARK -> 5.0;
            case CRAB -> 0.0;
        };
    }

    /**
     * Vanilla fish pick nearby random targets. Large marine mobs keep the same wandering
     * pattern but need a longer run so their higher cruise speed does not cause constant
     * tight circles around two- or three-block targets.
     */
    static double maxRoamDistance(MarineMobType type) {
        return switch (type) {
            case ORCA -> 18.0;
            case SHARK -> 15.0;
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
            case ORCA -> 40;
            case SHARK -> 34;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static int maxRoamTargetTicksExclusive(MarineMobType type) {
        return switch (type) {
            case ORCA -> 110;
            case SHARK -> 96;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static float maxTurnDegreesPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 6.5F;
            case SHARK -> 9.5F;
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
