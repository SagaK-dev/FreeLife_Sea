package com.sagakenichi.freelifemarine;

/**
 * Deterministic airborne kinematics for marine carriers.
 *
 * <p>FallingBlockEntity in Minecraft 1.21.1 applies 0.04 gravity per tick and then
 * multiplies velocity by 0.98. Keeping the same constants makes an unsupported shark
 * or orca fall at the same rate as sand and gravel instead of using living-entity
 * gravity.</p>
 */
final class MarineAirKinematics {

    static final double GRAVITY_PER_TICK = 0.04;
    static final double VERTICAL_DRAG = 0.98;
    static final double HORIZONTAL_DRAG = 0.98;
    static final double TERMINAL_FALL_SPEED = -1.96;
    static final double MAX_SWEEP_STEP = 0.20;

    private MarineAirKinematics() {
    }

    static double nextVerticalVelocity(double current) {
        double next = (current - GRAVITY_PER_TICK) * VERTICAL_DRAG;
        return Math.max(TERMINAL_FALL_SPEED, next);
    }

    static double nextHorizontalVelocity(double current) {
        return current * HORIZONTAL_DRAG;
    }

    static int sweepSteps(double dx, double dy, double dz) {
        double largest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        return Math.max(1, (int) Math.ceil(largest / MAX_SWEEP_STEP));
    }
}
