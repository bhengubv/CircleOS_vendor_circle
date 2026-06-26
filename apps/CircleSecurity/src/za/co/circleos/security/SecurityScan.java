/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Lightweight risk assessment for the File DMZ (SEC-3/4). Extension-based today;
 * a DataAcuity-backed content scan (SEC-2) and a behavioural sandbox (SEC-1) are
 * the deeper layers gated on the owner's server-vs-app decision.
 */
package za.co.circleos.security;

import java.util.Locale;

final class SecurityScan {

    private static final String[] RISKY = {
            ".exe", ".apk", ".bat", ".cmd", ".scr", ".js", ".jar", ".msi", ".dll",
            ".vbs", ".ps1", ".sh", ".com", ".pif", ".cpl", ".hta", ".reg"
    };

    private SecurityScan() {}

    static boolean isRisky(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.US);
        for (String r : RISKY) {
            if (l.endsWith(r)) return true;
        }
        return false;
    }

    static String reason(String name) {
        return isRisky(name)
                ? "Executable or script — held for your review"
                : "No risky signature found";
    }
}
