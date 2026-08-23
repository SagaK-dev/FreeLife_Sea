package com.sagakenichi.freelifemarine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarineAirKinematicsTest {

    @Test
    void gravityTurnsAnApexIntoImmediateDescent() {
        double next = MarineAirKinematics.nextVerticalVelocity(0.0);
        assertTrue(next < 0.0);
        assertEquals(-0.0392, next, 1.0E-9);
    }

    @Test
    void repeatedFallingBlockGravityKeepsFallingInsteadOfHovering() {
        double vertical = 0.0;
        double displacement = 0.0;
        for (int tick = 0; tick < 10; tick++) {
            vertical = MarineAirKinematics.nextVerticalVelocity(vertical);
            displacement += vertical;
        }
        assertTrue(vertical < -0.35);
        assertTrue(displacement < -2.0);
    }

    @Test
    void sweepSubdivisionPreventsLargeSingleStepFalls() {
        assertEquals(1, MarineAirKinematics.sweepSteps(0.0, -0.10, 0.0));
        assertEquals(5, MarineAirKinematics.sweepSteps(0.0, -1.00, 0.0));
        assertEquals(14, MarineAirKinematics.sweepSteps(2.8, -0.40, 0.0));
    }
}
