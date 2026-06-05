/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Apply the Circle private-DNS defaults to Settings.Global on first
 * boot. The PRODUCT_SYSTEM_PROPERTIES in vendor/circle/config/common.mk
 * encode the *intent* (ro.circleos.private_dns_mode = hostname,
 * specifier = dns.quad9.net) but the kernel resolver only honours the
 * values once they land in the SecureSettings db -- which only a
 * privileged Settings writer can do.
 *
 * This BroadcastReceiver fires on BOOT_COMPLETED + LOCKED_BOOT_COMPLETED
 * and is a no-op after the first successful write (idempotent gate on
 * a SharedPreferences flag).
 */
package za.co.circleos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

public final class CircleDnsBootInitializer extends BroadcastReceiver {

    private static final String TAG = "CircleDnsBoot";

    private static final String PREFS_FILE  = "circle_dns_init";
    private static final String KEY_APPLIED = "applied_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context dp = context.createDeviceProtectedStorageContext();
        final SharedPreferences prefs = dp.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_APPLIED, false)) {
            return;
        }
        final String mode = SystemProperties.get(
                "ro.circleos.private_dns_mode", "hostname");
        final String host = SystemProperties.get(
                "ro.circleos.private_dns_specifier", "dns.quad9.net");
        try {
            Settings.Global.putString(context.getContentResolver(),
                    Settings.Global.PRIVATE_DNS_MODE, mode);
            Settings.Global.putString(context.getContentResolver(),
                    Settings.Global.PRIVATE_DNS_SPECIFIER, host);
            prefs.edit().putBoolean(KEY_APPLIED, true).apply();
            Log.i(TAG, "Applied PRIVATE_DNS_MODE=" + mode
                    + " specifier=" + host);
        } catch (SecurityException se) {
            Log.w(TAG, "Need WRITE_SECURE_SETTINGS to apply DoH defaults", se);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to apply Circle DNS defaults", t);
        }
    }
}
