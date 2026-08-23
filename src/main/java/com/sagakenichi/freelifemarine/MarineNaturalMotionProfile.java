package com.sagakenichi.freelifemarine;

/**
 * Species-specific parameters for tropical-fish-style autonomous swimming.
 *
 * <p>Vanilla fish choose nearby random swim targets rather than committing to long
 * cross-pool routes. These values keep that short wandering cadence while scaling
 * turn rate and speed for the much larger shark and orca models.</p>
 */
final class MarineNaturalMotionProfile {

    private MarineNaturalMotionProfile() {
    }

    static double baseCruiseBlocksPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.22;
            case SHARK -> 0.18;
            case CRAB -> 0.05;
        };
    }

    static double minCruiseBlocksPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.13;
            case SHARK -> 0.11;
            case CRAB -> 0.04;
        };
    }

    static double maxCruiseBlocksPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.34;
            case SHARK -> 0.28;
            case CRAB -> 0.07;
        };
    }

    static double minPace(MarineMobType type) {
        return switch (type) {
            case ORCA -> 0.78;
            case SHARK -> 0.80;
            case CRAB -> 1.0;
        };
    }

    static double maxPace(MarineMobType type) {
        return switch (type) {
            case ORCA -> 1.08;
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
            case ORCA -> 0.0035;
            case SHARK -> 0.0045;
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
            case ORCA -> 2.8;
            case SHARK -> 2.2;
            case CRAB -> 0.0;
        };
    }

    /** Vanilla SwimAroundGoal searches roughly ten blocks horizontally. */
    static double maxRoamDistance(MarineMobType type) {
        return switch (type) {
            case ORCA, SHARK -> 10.0;
            case CRAB -> 0.0;
        };
    }

    /**
     * Vanilla fish can search seven blocks vertically. The large display models use a
     * slightly smaller range to avoid selecting water pockets that their body cannot fit.
     */
    static double maxRoamDepthChange(MarineMobType type) {
        return switch (type) {
            case ORCA -> 4.0;
            case SHARK -> 5.0;
            case CRAB -> 0.0;
        };
    }

    static int minRoamTargetTicks(MarineMobType type) {
        return switch (type) {
            case ORCA -> 34;
            case SHARK -> 28;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static int maxRoamTargetTicksExclusive(MarineMobType type) {
        return switch (type) {
            case ORCA -> 86;
            case SHARK -> 76;
            case CRAB -> Integer.MAX_VALUE;
        };
    }

    static float maxTurnDegreesPerTick(MarineMobType type) {
        return switch (type) {
            case ORCA -> 8.0F;
            case SHARK -> 12.0F;
            case CRAB -> 5.0F;
        };
    }

    static double collisionScanRadius(MarineMobType type) {
        return switch (type) {
            case ORCA -> 6.5;
            case SHARK -> 5.0;
            case CRAB -> 0.0;
        };
    }

    static boolean breaksBoats(MarineMobType type) {
        return type == MarineMobType.ORCA || type == MarineMobType.SHARK;
    }
}
