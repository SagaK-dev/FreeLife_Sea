package com.sagakenichi.freelifemarine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarineMotionTuningTest {

    @Test
    void riddenOrcaDefaultsToFiftyBlocksPerSecond() {
        assertEquals(50.0, MarineMotionTuning.ORCA_RIDDEN_BLOCKS_PER_SECOND, 1.0E-9);
        assertEquals(2.5, MarineMotionTuning.ORCA_RIDDEN_BLOCKS_PER_TICK, 1.0E-9);
        assertTrue(MarineMotionTuning.isValidRiddenSpeed(1.0));
        assertTrue(MarineMotionTuning.isValidRiddenSpeed(50.0));
        assertFalse(MarineMotionTuning.isValidRiddenSpeed(0.99));
        assertFalse(MarineMotionTuning.isValidRiddenSpeed(50.01));
    }

    @Test
    void lowSpeedForwardInputWorksForJavaAndTranslatedClients() {
        assertTrue(MarineMotionTuning.hasForwardRiderIntent(0.10, 1.0));
        assertTrue(MarineMotionTuning.hasForwardRiderIntent(0.10, 0.55));
        assertTrue(MarineMotionTuning.hasForwardRiderIntent(0.005, 1.0));
        assertTrue(MarineMotionTuning.hasForwardRiderIntent(0.002, 0.55));
        assertFalse(MarineMotionTuning.hasForwardRiderIntent(0.0019, 1.0));
        assertFalse(MarineMotionTuning.hasForwardRiderIntent(0.10, 0.0));
        assertFalse(MarineMotionTuning.hasForwardRiderIntent(0.10, -1.0));
        assertFalse(MarineMotionTuning.hasForwardRiderIntent(Double.NaN, 1.0));
    }

    @Test
    void forwardIntentGraceBridgesSparseJavaHorseInputTicks() {
        long detectedAt = 100L;
        assertTrue(MarineMotionTuning.forwardIntentGraceActive(detectedAt, detectedAt));
        assertTrue(MarineMotionTuning.forwardIntentGraceActive(
                detectedAt + MarineMotionTuning.RIDER_FORWARD_INTENT_GRACE_TICKS, detectedAt));
        assertFalse(MarineMotionTuning.forwardIntentGraceActive(
                detectedAt + MarineMotionTuning.RIDER_FORWARD_INTENT_GRACE_TICKS + 1L, detectedAt));
        assertFalse(MarineMotionTuning.forwardIntentGraceActive(50L, -1L));

        assertTrue(MarineMotionTuning.hasConflictingRiderIntent(0.01, -1.0));
        assertTrue(MarineMotionTuning.hasConflictingRiderIntent(0.01, 0.0));
        assertFalse(MarineMotionTuning.hasConflictingRiderIntent(0.01, 0.55));
        assertFalse(MarineMotionTuning.hasConflictingRiderIntent(0.001, -1.0));
    }

    @Test
    void unsupportedAirExcludesWaterAndGround() {
        assertTrue(MarineMotionTuning.isUnsupportedAir(false, false));
        assertFalse(MarineMotionTuning.isUnsupportedAir(true, false));
        assertFalse(MarineMotionTuning.isUnsupportedAir(false, true));
    }

    @Test
    void hoverIsKickedDownButNormalJumpAndFallAreUntouched() {
        assertTrue(MarineMotionTuning.needsFallKick(70.0, 70.0, 0.0));
        assertTrue(MarineMotionTuning.needsFallKick(70.0, 70.0, 0.11));
        assertFalse(MarineMotionTuning.needsFallKick(Double.NaN, 70.0, 0.0));
        assertFalse(MarineMotionTuning.needsFallKick(70.0, 70.2, 0.45));
        assertFalse(MarineMotionTuning.needsFallKick(70.0, 69.8, -0.20));
        assertEquals(-0.18, MarineMotionTuning.fallKickVelocity(0.0), 1.0E-9);
    }
}
