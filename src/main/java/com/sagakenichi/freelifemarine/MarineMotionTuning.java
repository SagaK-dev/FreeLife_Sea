package com.sagakenichi.freelifemarine;

final class MarineMotionTuning {

    static final double PREVIOUS_ORCA_RIDDEN_BLOCKS_PER_SECOND = 33.6;
    static final double ORCA_RIDDEN_SPEED_MULTIPLIER = 50.0 / PREVIOUS_ORCA_RIDDEN_BLOCKS_PER_SECOND;
    static final double ORCA_RIDDEN_BLOCKS_PER_SECOND =
            PREVIOUS_ORCA_RIDDEN_BLOCKS_PER_SECOND * ORCA_RIDDEN_SPEED_MULTIPLIER;
    static final double ORCA_RIDDEN_BLOCKS_PER_TICK = ORCA_RIDDEN_BLOCKS_PER_SECOND / 20.0;

    static final double MIN_ORCA_RIDDEN_BLOCKS_PER_SECOND = 1.0;
    static final double MAX_ORCA_RIDDEN_BLOCKS_PER_SECOND = 50.0;
    static final int MIN_ORCA_JUMP_HEIGHT = 3;
    static final int MAX_ORCA_JUMP_HEIGHT = 13;
    static final int DEFAULT_ORCA_JUMP_HEIGHT = 10;

    // Java Edition can produce only a few thousandths of a block per tick from a
    // saddled horse while it is submerged. Keep the gate low enough to recognize
    // that forward input; the alignment check below still rejects drift and reverse input.
    private static final double RIDER_INPUT_MIN_SPEED = 0.002;
    private static final double RIDER_FORWARD_ALIGNMENT = 0.40;
    private static final double STALL_MINIMUM_DESCENT = -0.025;
    private static final double STALL_MAX_VERTICAL_VELOCITY = 0.15;
    private static final double FALL_KICK = 0.18;

    private MarineMotionTuning() {
    }

    static boolean hasForwardRiderIntent(double horizontalSpeed, double forwardAlignment) {
        return Double.isFinite(horizontalSpeed)
                && Double.isFinite(forwardAlignment)
                && horizontalSpeed >= RIDER_INPUT_MIN_SPEED
                && forwardAlignment >= RIDER_FORWARD_ALIGNMENT;
    }

    static boolean isValidRiddenSpeed(double blocksPerSecond) {
        return Double.isFinite(blocksPerSecond)
                && blocksPerSecond >= MIN_ORCA_RIDDEN_BLOCKS_PER_SECOND
                && blocksPerSecond <= MAX_ORCA_RIDDEN_BLOCKS_PER_SECOND;
    }

    static boolean isValidJumpHeight(int blocks) {
        return blocks >= MIN_ORCA_JUMP_HEIGHT && blocks <= MAX_ORCA_JUMP_HEIGHT;
    }

    static double blocksPerTick(double blocksPerSecond) {
        return blocksPerSecond / 20.0;
    }

    static boolean isUnsupportedAir(boolean inWater, boolean onGround) {
        return !inWater && !onGround;
    }

    static boolean needsFallKick(double previousY, double currentY, double verticalVelocity) {
        if (!Double.isFinite(previousY)) {
            return false;
        }
        double deltaY = currentY - previousY;
        return deltaY > STALL_MINIMUM_DESCENT && verticalVelocity <= STALL_MAX_VERTICAL_VELOCITY;
    }

    static double fallKickVelocity(double currentVerticalVelocity) {
        return Math.min(-FALL_KICK, currentVerticalVelocity - FALL_KICK);
    }
}
