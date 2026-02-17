/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.trafficlobby;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Matches network destinations against threat intelligence feeds.
 * Loads blocklists from /data/circle/security/feeds/ (updated by ThreatFeedUpdater).
 */
public class ThreatMatcher {

    private static final String TAG       = "TrafficLobby.Threats";
    private static final String FEEDS_DIR = "/data/circle/security/feeds/";

    private final Set<String> mBlockedIps      = new HashSet<>();
    private final Set<String> mBlockedDomains  = new HashSet<>();
    private final Set<String> mUserWhitelist   = new HashSet<>();
    private final Set<String> mUserBlacklist   = new HashSet<>();

    public ThreatMatcher(Context context) {
        loadFeeds();
        loadUserLists(context);
    }

    public ConnectionVerdict evaluate(String host, String ip) {
        String hostLower = host != null ? host.toLowerCase() : "";
        String ipStr     = ip   != null ? ip   : "";

        // User overrides take highest priority
        if (mUserWhitelist.contains(hostLower) || mUserWhitelist.contains(ipStr))
            return ConnectionVerdict.allow();

        if (mUserBlacklist.contains(hostLower) || mUserBlacklist.contains(ipStr))
            return ConnectionVerdict.block("User blacklist", hostLower.isEmpty() ? ipStr : hostLower);

        // Threat feed matches
        if (!ipStr.isEmpty() && mBlockedIps.contains(ipStr))
            return ConnectionVerdict.block("Known C2 IP", ipStr);

        if (!hostLower.isEmpty() && mBlockedDomains.contains(hostLower))
            return ConnectionVerdict.block("Known malicious domain", hostLower);

        // DGA entropy check
        if (!hostLower.isEmpty() && isDgaDomain(hostLower))
            return ConnectionVerdict.lobby("High-entropy domain (possible DGA)");

        return ConnectionVerdict.allow();
    }

    /** Shannon entropy > 3.5 on the leftmost label suggests DGA-generated domain. */
    private boolean isDgaDomain(String domain) {
        String label = domain.contains(".") ? domain.substring(0, domain.indexOf('.')) : domain;
        if (label.length() < 8) return false;
        int[] freq = new int[256];
        for (char c : label.toCharArray()) freq[c]++;
        double entropy = 0;
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / label.length();
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy > 3.5;
    }

    public void addToUserWhitelist(String host) { mUserWhitelist.add(host.toLowerCase()); }
    public void addToUserBlacklist(String host) { mUserBlacklist.add(host.toLowerCase()); }

    private void loadFeeds() {
        loadSet(FEEDS_DIR + "c2_ips.txt",     mBlockedIps);
        loadSet(FEEDS_DIR + "c2_domains.txt", mBlockedDomains);
        Log.i(TAG, "Loaded " + mBlockedIps.size() + " IPs, " + mBlockedDomains.size() + " domains");
    }

    private void loadUserLists(Context ctx) {
        File wl = new File(ctx.getFilesDir(), "user_whitelist.txt");
        File bl = new File(ctx.getFilesDir(), "user_blacklist.txt");
        loadSet(wl.getAbsolutePath(), mUserWhitelist);
        loadSet(bl.getAbsolutePath(), mUserBlacklist);
    }

    private void loadSet(String path, Set<String> set) {
        File f = new File(path);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) set.add(line.toLowerCase());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load " + path + ": " + e.getMessage());
        }
    }

    public void reload() {
        mBlockedIps.clear();
        mBlockedDomains.clear();
        loadFeeds();
    }
}
