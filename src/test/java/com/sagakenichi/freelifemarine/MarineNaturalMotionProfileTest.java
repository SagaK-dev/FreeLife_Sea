package com.sagakenichi.freelifemarine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineNaturalMotionProfileTest {

    @Test
    void autonomousAquaticMobsKeepAPositiveCruiseRange() {
        for (MarineMobType type : new MarineMobType[] {MarineMobType.ORCA, MarineMobType.SHARK}) {
            double min = MarineNaturalMotionProfile.minCruiseBlocksPerTick(type);
            double base = MarineNaturalMotionProfile.baseCruiseBlocksPerTick(type);
            double max = MarineNaturalMotionProfile.maxCruiseBlocksPerTick(type);

            assertTrue(min > 0.0);
            assertTrue(base > min);
            assertTrue(max > base);
            assertTrue(MarineNaturalMotionProfile.minPace(type) > 0.0);
            assertTrue(MarineNaturalMotionProfile.maxPace(type) >= MarineNaturalMotionProfile.minPace(type));
        }
    }

    @Test
    void roamingTargetsStayWithinVanillaFishScale() {
        for (MarineMobType type : new MarineMobType[] {MarineMobType.ORCA, MarineMobType.SHARK}) {
            double minDistance = MarineNaturalMotionProfile.minRoamDistance(type);
            double maxDistance = MarineNaturalMotionProfile.maxRoamDistance(type);
            int minTicks = MarineNaturalMotionProfile.minRoamTargetTicks(type);
            int maxTicks = MarineNaturalMotionProfile.maxRoamTargetTicksExclusive(type);

            assertTrue(minDistance >= 2.0);
            assertTrue(maxDistance > minDistance);
            assertTrue(maxDistance <= 10.0);
            assertTrue(minTicks >= 20);
            assertTrue(maxTicks > minTicks);
        }
    }

    @Test
    void naturalPaceAndDepthVariationStayGentle() {
        for (long tick = 0; tick < 2_000; tick += 7) {
            double orcaPulse = MarineNaturalMotionProfile.pacePulse(MarineMobType.ORCA, tick, 1.3);
            double sharkPulse = MarineNaturalMotionProfile.pacePulse(MarineMobType.SHARK, tick, 2.1);
            assertTrue(orcaPulse >= 0.955 && orcaPulse <= 1.045);
            assertTrue(sharkPulse >= 0.945 && sharkPulse <= 1.055);
            assertTrue(Math.abs(MarineNaturalMotionProfile.verticalWave(
                    MarineMobType.ORCA, tick, 1.3)) <= 0.004);
            assertTrue(Math.abs(MarineNaturalMotionProfile.verticalWave(
                    MarineMobType.SHARK, tick, 2.1)) <= 0.005);
        }
    }

    @Test
    void largeAquaticMobsBreakBoatsButCrabsDoNot() {
        assertTrue(MarineNaturalMotionProfile.breaksBoats(MarineMobType.ORCA));
        assertTrue(MarineNaturalMotionProfile.breaksBoats(MarineMobType.SHARK));
        assertFalse(MarineNaturalMotionProfile.breaksBoats(MarineMobType.CRAB));
        assertTrue(MarineNaturalMotionProfile.collisionScanRadius(MarineMobType.ORCA) >= 6.0);
        assertTrue(MarineNaturalMotionProfile.collisionScanRadius(MarineMobType.SHARK) >= 4.5);
    }
}
