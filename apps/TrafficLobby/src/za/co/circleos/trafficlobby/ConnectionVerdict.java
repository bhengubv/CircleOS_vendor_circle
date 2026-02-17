/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

public final class ConnectionVerdict {
    public static final int ALLOW  = 0; // Known good — connect immediately
    public static final int LOBBY  = 1; // Inspect first — hold for user decision
    public static final int BLOCK  = 2; // Known bad — drop silently

    public final int    verdict;
    public final String reason;
    public final String indicator; // The matching IOC if applicable

    public ConnectionVerdict(int verdict, String reason, String indicator) {
        this.verdict   = verdict;
        this.reason    = reason;
        this.indicator = indicator;
    }

    public static ConnectionVerdict allow()  { return new ConnectionVerdict(ALLOW,  null, null); }
    public static ConnectionVerdict block(String reason, String ioc) {
        return new ConnectionVerdict(BLOCK, reason, ioc);
    }
    public static ConnectionVerdict lobby(String reason) {
        return new ConnectionVerdict(LOBBY, reason, null);
    }
}
