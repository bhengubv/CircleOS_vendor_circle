/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

public final class TrafficMode {
    /** Lobby ALL first-time connections. For journalists, activists, high-risk users. */
    public static final int PARANOID = 0;
    /** Lobby suspicious connections only. Default for most users. */
    public static final int BALANCED = 1;
    /** Block known-bad only, allow the rest. For speed-priority users. */
    public static final int RELAXED  = 2;

    private TrafficMode() {}
}
