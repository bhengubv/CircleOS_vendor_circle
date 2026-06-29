package com.circleos.server.wallet;

public class WalletMathTest {
    static int passed = 0, failed = 0;
    static void ck(boolean c, String label) {
        if (c) passed++; else { failed++; System.out.println("FAIL: " + label); }
    }

    public static void main(String[] args) {
        final long PT = 50_000, LK = 20_000, DAY = 200_000;

        // ---- limit checks ----
        WalletMath w = new WalletMath(PT, LK, DAY); w.available = 100_000;
        WalletMath wa = new WalletMath(PT, LK, DAY); wa.available = 10_000;
        ck("insufficient funds".equals(wa.checkSend(30_000, false, 0)), "over available rejected (within per-tap+daily)");
        ck("over per-tap limit".equals(w.checkSend(60_000, false, 0)), "over per-tap rejected");
        ck("over per-tap limit".equals(w.checkSend(25_000, true, 0)), "lock-screen has tighter per-tap");
        ck(w.checkSend(20_000, true, 0) == null, "lock-screen within limit allowed");
        ck(w.checkSend(50_000, false, 0) == null, "normal within limit allowed");
        ck("invalid amount".equals(w.checkSend(0, false, 0)), "zero amount rejected");
        ck("invalid amount".equals(w.checkSend(-5, false, 0)), "negative amount rejected");

        // ---- send finalize ----
        WalletMath s = new WalletMath(PT, LK, DAY); s.available = 100_000;
        s.finalizeSend(30_000, 0);
        ck(s.available == 70_000, "send debits available");
        ck(s.pendingOut == 30_000, "send reserves pendingOut");
        ck(s.dailySpent == 30_000, "send counts toward daily");
        ck(s.dailyRemaining() == 170_000, "daily remaining correct after send");
        s.settleSend(30_000);
        ck(s.pendingOut == 0, "settle-send clears pendingOut");
        ck(s.available == 70_000, "settle-send leaves available (already debited)");

        // ---- receive + settle ----
        WalletMath r = new WalletMath(PT, LK, DAY); r.available = 0;
        r.acceptRecv(40_000);
        ck(r.pendingIn == 40_000 && r.available == 0, "recv held as pendingIn, not yet spendable");
        r.settleRecv(40_000);
        ck(r.pendingIn == 0 && r.available == 40_000, "settle-recv moves pendingIn -> available");

        // ---- daily rollover ----
        WalletMath d = new WalletMath(PT, LK, DAY); d.available = 1_000_000;
        d.finalizeSend(170_000, 0);                       // day 0, remaining = 30_000 (< per-tap)
        ck(d.dailyRemaining() == 30_000, "daily consumed on day 0");
        ck("over daily limit".equals(d.checkSend(40_000, false, 0)), "over remaining-daily rejected (within per-tap)");
        ck(d.checkSend(50_000, false, WalletMath.DAY_MS * 2) == null, "allowed again after day rolls over");
        ck(d.dailySpent == 0, "daily counter reset on new day");
        ck(d.dailyRemaining() == 200_000, "daily fully restored on new day");

        // ---- floors at zero ----
        WalletMath n = new WalletMath(PT, LK, DAY); n.available = 10_000;
        n.finalizeSend(10_000, 0);
        ck(n.available == 0, "available floors at 0");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
        System.out.println("ALL GREEN");
    }
}
