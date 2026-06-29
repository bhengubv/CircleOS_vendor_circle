package com.circleos.server.wallet;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class WalletFrameTest {
    static int passed = 0, failed = 0;
    static void ck(boolean c, String label) {
        if (c) passed++; else { failed++; System.out.println("FAIL: " + label); }
    }

    public static void main(String[] args) {
        // ---- frame / decode roundtrip ----
        String j = "{\"amt\":12345,\"cur\":\"ZAR\"}";
        WalletFrame.Decoded d = WalletFrame.decode(WalletFrame.frame(WalletFrame.MSG_OFFER, j));
        ck(d != null, "decode non-null");
        ck(d.type == WalletFrame.MSG_OFFER, "type roundtrips (OFFER)");
        ck(j.equals(d.json), "json payload roundtrips exactly");

        // ---- each message type distinct + roundtrips ----
        for (int t : new int[]{ WalletFrame.MSG_DISCOVER, WalletFrame.MSG_HELLO,
                                WalletFrame.MSG_ACCEPTED, WalletFrame.MSG_ERROR }) {
            WalletFrame.Decoded dd = WalletFrame.decode(WalletFrame.frame(t, "{}"));
            ck(dd != null && dd.type == t && "{}".equals(dd.json), "type " + t + " roundtrips");
        }

        // ---- empty payload (type-only) ----
        WalletFrame.Decoded e = WalletFrame.decode(WalletFrame.frame(WalletFrame.MSG_HELLO, ""));
        ck(e != null && e.type == WalletFrame.MSG_HELLO && e.json.isEmpty(), "empty-payload frame ok");

        // ---- malformed inputs -> null (fail-safe) ----
        ck(WalletFrame.decode(null) == null, "decode(null) -> null");
        ck(WalletFrame.decode("") == null, "decode(empty) -> null");
        ck(WalletFrame.decode("!!!not base64!!!") == null, "decode(garbage) -> null");

        // ---- offerBytes canonical + deterministic ----
        byte[] a = WalletFrame.offerBytes("rcv_abc", 5000, "lunch");
        byte[] b = WalletFrame.offerBytes("rcv_abc", 5000, "lunch");
        ck(Arrays.equals(a, b), "offerBytes deterministic");
        ck(Arrays.equals(a, "SDPKT|rcv_abc|5000|lunch".getBytes(StandardCharsets.UTF_8)),
                "offerBytes exact canonical form");
        ck(!Arrays.equals(a, WalletFrame.offerBytes("rcv_abc", 5001, "lunch")),
                "offerBytes differs on amount (tamper-evident)");
        ck(!Arrays.equals(a, WalletFrame.offerBytes("rcv_xyz", 5000, "lunch")),
                "offerBytes differs on receiver session");
        ck(Arrays.equals(WalletFrame.offerBytes(null, 100, null),
                "SDPKT||100|".getBytes(StandardCharsets.UTF_8)), "offerBytes null-safe");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
        System.out.println("ALL GREEN");
    }
}
