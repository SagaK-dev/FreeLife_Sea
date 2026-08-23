package com.sagakenichi.freelifemarine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineActivityProfileTest {

    @Test
    void orcasRoamAtHighSpeedAndBurstAtLevelTen() {
        assertEquals(7, MarineActivityProfile.minRoamLevel(MarineMobType.ORCA));
        assertEquals(9, MarineActivityProfile.maxRoamLevel(MarineMobType.ORCA));
        assertEquals(10, MarineActivityProfile.burstLevel(MarineMobType.ORCA));
        assertTrue(MarineActivityProfile.burstChance(MarineMobType.ORCA) >= 0.50);
        assertTrue(MarineActivityProfile.accelerationMultiplier(MarineMobType.ORCA) >= 2.0);
    }

    @Test
    void sharksRemainFastButBelowOrcaMaximum() {
        assertEquals(6, MarineActivityProfile.minRoamLevel(MarineMobType.SHARK));
        assertEquals(8, MarineActivityProfile.maxRoamLevel(MarineMobType.SHARK));
        assertEquals(9, MarineActivityProfile.burstLevel(MarineMobType.SHARK));
        assertTrue(MarineActivityProfile.accelerationMultiplier(MarineMobType.SHARK) >= 1.8);
    }

    @Test
    void autonomousSwimmingKeepsBreachesEffectivelyDisabled() {
        assertTrue(MarineActivityProfile.maxBehaviorTicksExclusive(MarineMobType.ORCA) <= 56);
        assertTrue(MarineActivityProfile.maxBehaviorTicksExclusive(MarineMobType.SHARK) <= 76);

        int orcaMinJump = MarineActivityProfile.minJumpDelayTicks(MarineMobType.ORCA);
        int sharkMinJump = MarineActivityProfile.minJumpDelayTicks(MarineMobType.SHARK);
        assertTrue(orcaMinJump >= 1_000_000_000);
        assertTrue(sharkMinJump >= 1_000_000_000);
        assertTrue(MarineActivityProfile.maxJumpDelayTicksExclusive(MarineMobType.ORCA) > orcaMinJump);
        assertTrue(MarineActivityProfile.maxJumpDelayTicksExclusive(MarineMobType.SHARK) > sharkMinJump);

        assertTrue(MarineActivityProfile.maxYawChange(MarineMobType.ORCA) >= 65.0);
    }
}
