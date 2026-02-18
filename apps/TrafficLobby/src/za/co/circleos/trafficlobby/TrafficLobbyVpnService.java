/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.IpPrefix;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local VPN service that intercepts all app traffic for Traffic Lobby analysis.
 *
 * Architecture:
 *   App → TUN interface → IPv4 parser → ThreatMatcher + BeaconDetector → ALLOW/LOBBY/BLOCK
 *
 * Packet handling:
 *   ALLOW  — forwarded immediately
 *   LOBBY  — forwarded, notification shown with Allow/Block actions
 *   BLOCK  — packet dropped (app receives connection timeout)
 *
 * Spyware detection: sensor-active + outbound upload > 1 MB to unknown host
 * triggers Kill App / Quarantine notification.
 */
public class TrafficLobbyVpnService extends VpnService {

    private static final String TAG = "TrafficLobby.VPN";

    private static final String ALERT_CHANNEL_ID = "trafficlobby_alerts";
    private static final String LOBBY_CHANNEL_ID = "trafficlobby_lobby";

    static final String ACTION_KILL_APP       = "za.co.circleos.trafficlobby.KILL_APP";
    static final String ACTION_QUARANTINE_APP = "za.co.circleos.trafficlobby.QUARANTINE_APP";
    static final String ACTION_ALLOW_HOST     = "za.co.circleos.trafficlobby.ALLOW_HOST";
    static final String ACTION_BLOCK_HOST     = "za.co.circleos.trafficlobby.BLOCK_HOST";
    static final String EXTRA_PACKAGE         = "package";
    static final String EXTRA_HOST            = "host";

    private final AtomicInteger mNotifId = new AtomicInteger(100);

    /**
     * Circle Mesh transport ports excluded from VPN inspection.
     * WiFi Direct mesh: 9847 (TCP), mDNS/NSD mesh: 8723 (TCP)
     */
    private static final int[] MESH_BYPASS_PORTS = {9847, 8723};

    private ParcelFileDescriptor    mTunInterface;
    private TrafficAnalyzer         mAnalyzer;
    private SpywareBehaviorDetector mSpywareDetector;
    private NotificationManager     mNotifManager;
    private final ExecutorService   mExecutor = Executors.newCachedThreadPool();
    private volatile boolean        mRunning  = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mRunning) return START_STICKY;

        mNotifManager    = getSystemService(NotificationManager.class);
        mAnalyzer        = new TrafficAnalyzer(this);
        mSpywareDetector = new SpywareBehaviorDetector();
        mSpywareDetector.setListener(this::handleSpywareAlert);

        createNotificationChannels();
        registerAlertReceiver();

        try {
            Builder builder = new Builder()
                    .setSession("CircleOS Traffic Lobby")
                    .addAddress("10.0.0.1", 24)
                    .addDnsServer("10.0.0.1")
                    .addRoute("0.0.0.0", 0)
                    .excludeRoute(new IpPrefix(InetAddress.getByName("192.168.49.0"), 24))
                    .excludeRoute(new IpPrefix(InetAddress.getByName("224.0.0.0"), 4))
                    .setBlocking(true);

            mTunInterface = builder.establish();
            if (mTunInterface == null) {
                Log.e(TAG, "Failed to establish VPN — permission not granted");
                stopSelf();
                return START_NOT_STICKY;
            }

            mRunning = true;
            mExecutor.submit(this::runPacketLoop);
            Log.i(TAG, "Traffic Lobby VPN established");

        } catch (Exception e) {
            Log.e(TAG, "VPN setup failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        mExecutor.shutdownNow();
        try { unregisterReceiver(mAlertReceiver); } catch (Exception ignored) {}
        try { if (mTunInterface != null) mTunInterface.close(); } catch (IOException ignored) {}
        super.onDestroy();
    }

    // ── Packet loop ───────────────────────────────────────────────────────────

    /**
     * Reads raw IPv4 packets from the TUN fd, inspects each one, and either
     * forwards or drops it based on threat analysis.
     *
     * IPv4 header (RFC 791):
     *   byte[0]  version(4) | IHL (header length in 32-bit words)
     *   byte[9]  protocol (6=TCP, 17=UDP)
     *   byte[12..15] source IP
     *   byte[16..19] destination IP
     * TCP/UDP transport header (at offset IHL):
     *   byte[0..1] source port, byte[2..3] destination port
     */
    private void runPacketLoop() {
        try (FileInputStream  in  = new FileInputStream(mTunInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(mTunInterface.getFileDescriptor())) {

            byte[] packet = new byte[32767];
            while (mRunning) {
                int len = in.read(packet);
                if (len < 20) {
                    if (len > 0) out.write(packet, 0, len);
                    continue;
                }

                // Only inspect IPv4 — forward all other versions unchanged
                if (((packet[0] >> 4) & 0xF) != 4) {
                    out.write(packet, 0, len);
                    continue;
                }

                int ihl      = (packet[0] & 0xF) * 4; // IP header length in bytes
                int protocol = packet[9] & 0xFF;
                String srcIp  = formatIp(packet, 12);
                String destIp = formatIp(packet, 16);

                int srcPort = 0, destPort = 0;
                if ((protocol == 6 || protocol == 17) && ihl + 4 <= len) {
                    srcPort  = ((packet[ihl]     & 0xFF) << 8) | (packet[ihl + 1] & 0xFF);
                    destPort = ((packet[ihl + 2] & 0xFF) << 8) | (packet[ihl + 3] & 0xFF);
                }

                // Never inspect Circle Mesh transport ports
                if (isMeshPort(destPort)) {
                    out.write(packet, 0, len);
                    continue;
                }

                String packageName = resolvePackage(srcIp, srcPort, protocol);

                ConnectionVerdict verdict = mAnalyzer.evaluate(
                        packageName != null ? packageName : "unknown",
                        null, destIp, destPort,
                        System.currentTimeMillis());

                if (verdict.verdict == ConnectionVerdict.BLOCK) {
                    Log.w(TAG, "BLOCKED " + destIp + ":" + destPort
                            + " — " + verdict.reason + " [" + verdict.indicator + "]");
                    continue; // drop
                }

                // Track TCP upload bytes for exfiltration detection
                if (protocol == 6) {
                    boolean exfil = mAnalyzer.recordUpload(destIp + ":" + destPort, len);
                    if (exfil && packageName != null) {
                        mSpywareDetector.onSuspiciousUpload(packageName, destIp);
                    }
                }

                if (verdict.verdict == ConnectionVerdict.LOBBY) {
                    Log.i(TAG, "LOBBY " + destIp + ":" + destPort + " — " + verdict.reason);
                    showLobbyAlert(packageName, destIp, destPort, verdict.reason);
                }

                out.write(packet, 0, len);
            }

        } catch (IOException e) {
            if (mRunning) Log.e(TAG, "Packet loop error", e);
        }
    }

    // ── UID / package resolution ──────────────────────────────────────────────

    /**
     * Maps a TCP/UDP socket (srcIp:srcPort) to the owning Android package by
     * reading /proc/net/tcp (or /proc/net/udp).
     *
     * /proc/net/tcp columns: sl local_addr rem_addr st tx:rx tr retr uid ...
     * Addresses are little-endian hex: DDCCBBAA:PPPP (IP bytes reversed, port big-endian).
     */
    private String resolvePackage(String srcIp, int srcPort, int protocol) {
        if (srcPort == 0) return null;
        String procFile = (protocol == 6) ? "/proc/net/tcp" : "/proc/net/udp";
        String hexLocal = ipToHexLe(srcIp) + ":" + String.format("%04X", srcPort);
        try (BufferedReader br = new BufferedReader(new FileReader(procFile))) {
            br.readLine(); // skip header line
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                if (f.length < 8) continue;
                if (f[1].equalsIgnoreCase(hexLocal)) {
                    int uid = Integer.parseInt(f[7]);
                    String[] pkgs = getPackageManager().getPackagesForUid(uid);
                    return (pkgs != null && pkgs.length > 0) ? pkgs[0] : null;
                }
            }
        } catch (Exception e) {
            Log.v(TAG, "resolvePackage: " + e.getMessage());
        }
        return null;
    }

    /** Convert "a.b.c.d" to the little-endian hex format used in /proc/net/tcp. */
    private static String ipToHexLe(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) return "00000000";
        return String.format("%02X%02X%02X%02X",
                Integer.parseInt(p[3]), Integer.parseInt(p[2]),
                Integer.parseInt(p[1]), Integer.parseInt(p[0]));
    }

    private static String formatIp(byte[] pkt, int off) {
        return (pkt[off] & 0xFF) + "." + (pkt[off+1] & 0xFF) + "."
             + (pkt[off+2] & 0xFF) + "." + (pkt[off+3] & 0xFF);
    }

    private static boolean isMeshPort(int port) {
        for (int p : MESH_BYPASS_PORTS) if (p == port) return true;
        return false;
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private void showLobbyAlert(String pkg, String destIp, int destPort, String reason) {
        if (mNotifManager == null) return;
        String host  = destIp + ":" + destPort;
        String label = pkg != null ? pkg : "Unknown app";
        int id = mNotifId.getAndIncrement() % 1000;

        PendingIntent allowPi = PendingIntent.getBroadcast(this, id,
                new Intent(ACTION_ALLOW_HOST).setPackage(getPackageName())
                        .putExtra(EXTRA_HOST, host),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent blockPi = PendingIntent.getBroadcast(this, id + 500,
                new Intent(ACTION_BLOCK_HOST).setPackage(getPackageName())
                        .putExtra(EXTRA_HOST, host),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        mNotifManager.notify(id, new Notification.Builder(this, LOBBY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Traffic Lobby: Review connection")
                .setContentText(label + " → " + host)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(label + " → " + host + "\n\n" + reason))
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.checkbox_on_background, "Allow", allowPi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_delete, "Block", blockPi).build())
                .setAutoCancel(true)
                .build());
    }

    private void handleSpywareAlert(String packageName, String destination, String[] behaviors) {
        Log.w(TAG, "SPYWARE ALERT: " + packageName + " → " + destination);
        if (mNotifManager == null) return;
        int id = mNotifId.getAndIncrement() % 1000;

        PendingIntent killPi = PendingIntent.getBroadcast(this, id,
                new Intent(ACTION_KILL_APP).setPackage(getPackageName())
                        .putExtra(EXTRA_PACKAGE, packageName),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent quarantinePi = PendingIntent.getBroadcast(this, id + 500,
                new Intent(ACTION_QUARANTINE_APP).setPackage(getPackageName())
                        .putExtra(EXTRA_PACKAGE, packageName),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        StringBuilder sb = new StringBuilder();
        for (String b : behaviors) sb.append("• ").append(b).append("\n");

        mNotifManager.notify(id, new Notification.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Spyware Detected: " + packageName)
                .setContentText("Data exfiltration to " + destination)
                .setStyle(new Notification.BigTextStyle().bigText(sb.toString()))
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_delete, "Kill App", killPi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_lock_lock, "Quarantine", quarantinePi).build())
                .setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(false)
                .build());
    }

    // ── Alert receiver ────────────────────────────────────────────────────────

    private final BroadcastReceiver mAlertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case ACTION_KILL_APP: {
                    String pkg = intent.getStringExtra(EXTRA_PACKAGE);
                    if (pkg == null) return;
                    android.app.ActivityManager am =
                            getSystemService(android.app.ActivityManager.class);
                    if (am != null) am.killBackgroundProcesses(pkg);
                    mAnalyzer.blockDestination(pkg);
                    Log.i(TAG, "Force-killed: " + pkg);
                    break;
                }
                case ACTION_QUARANTINE_APP: {
                    String pkg = intent.getStringExtra(EXTRA_PACKAGE);
                    if (pkg == null) return;
                    try {
                        android.os.IBinder binder =
                                android.os.ServiceManager.getService("circle.quarantine");
                        if (binder == null) {
                            Log.w(TAG, "circle.quarantine unavailable");
                            return;
                        }
                        za.co.circleos.security.ICircleQuarantine q =
                                za.co.circleos.security.ICircleQuarantine.Stub.asInterface(binder);
                        ApplicationInfo info =
                                getPackageManager().getApplicationInfo(pkg, 0);
                        q.quarantineFile(info.sourceDir, pkg + ": spyware upload detected");
                        Log.i(TAG, "Quarantined " + pkg + " (" + info.sourceDir + ")");
                    } catch (Exception e) {
                        Log.e(TAG, "Quarantine failed for " + pkg, e);
                    }
                    break;
                }
                case ACTION_ALLOW_HOST: {
                    String host = intent.getStringExtra(EXTRA_HOST);
                    if (host != null) { mAnalyzer.allowDestination(host);
                        Log.i(TAG, "Whitelisted: " + host); }
                    break;
                }
                case ACTION_BLOCK_HOST: {
                    String host = intent.getStringExtra(EXTRA_HOST);
                    if (host != null) { mAnalyzer.blockDestination(host);
                        Log.i(TAG, "Blacklisted: " + host); }
                    break;
                }
            }
        }
    };

    private void registerAlertReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_KILL_APP);
        f.addAction(ACTION_QUARANTINE_APP);
        f.addAction(ACTION_ALLOW_HOST);
        f.addAction(ACTION_BLOCK_HOST);
        registerReceiver(mAlertReceiver, f, Context.RECEIVER_NOT_EXPORTED);
    }

    private void createNotificationChannels() {
        if (mNotifManager == null) return;
        mNotifManager.createNotificationChannel(new NotificationChannel(
                ALERT_CHANNEL_ID, "Spyware Alerts",
                NotificationManager.IMPORTANCE_HIGH));
        mNotifManager.createNotificationChannel(new NotificationChannel(
                LOBBY_CHANNEL_ID, "Traffic Lobby Decisions",
                NotificationManager.IMPORTANCE_DEFAULT));
    }
}
