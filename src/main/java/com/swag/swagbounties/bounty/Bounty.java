package com.swag.swagbounties.bounty;

import java.util.UUID;

/**
 * Immutable value object representing a single bounty placed on a target player.
 */
public final class Bounty {

    private final UUID targetUUID;
    private final UUID creatorUUID;
    private final double reward;
    private final boolean anonymous;
    private final long placedAt;

    /**
     * Convenience constructor used when placing a new bounty at runtime.
     * {@code placedAt} is set to the current epoch millisecond timestamp.
     */
    public Bounty(UUID targetUUID, UUID creatorUUID, double reward, boolean anonymous) {
        this(targetUUID, creatorUUID, reward, anonymous, System.currentTimeMillis());
    }

    /**
     * Full constructor used when loading a bounty from persistent storage.
     *
     * @param placedAt epoch-millisecond timestamp recorded when the bounty was originally placed
     */
    public Bounty(UUID targetUUID, UUID creatorUUID, double reward, boolean anonymous, long placedAt) {
        this.targetUUID = targetUUID;
        this.creatorUUID = creatorUUID;
        this.reward = reward;
        this.anonymous = anonymous;
        this.placedAt = placedAt;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public UUID getCreatorUUID() {
        return creatorUUID;
    }

    public double getReward() {
        return reward;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public long getPlacedAt() {
        return placedAt;
    }
}
