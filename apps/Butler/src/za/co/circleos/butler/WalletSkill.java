/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.butler;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import za.co.circleos.sdpkt.IShongololoWallet;
import za.co.circleos.sdpkt.LocationContext;
import za.co.circleos.sdpkt.ShongololoTransaction;
import za.co.circleos.sdpkt.WalletBalance;
import za.co.circleos.sdpkt.WalletKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Butler wallet skill — intercepts wallet-related queries before sending to LLM.
 *
 * By answering wallet queries from live service data (rather than the LLM),
 * responses are accurate, immediate, and don't expose financial data to the
 * inference model.
 *
 * Handled queries (keyword-based, case-insensitive):
 *   "balance" / "how much" / "funds"    → formatted balance + limits
 *   "transactions" / "history" / "paid" → last 5 transactions
 *   "address" / "wallet address"         → short wallet address
 *   "send" / "pay" / "transfer"          → instructions to use the Wallet app
 *   "limit" / "per tap" / "daily"        → effective per-tap and daily limits
 *   "location context" / "where am i"   → current location context + limits
 *
 * Returns null if the query is not wallet-related (falls through to LLM).
 */
public final class WalletSkill {

    private static final String TAG = "Butler.WalletSkill";

    private static IShongololoWallet sWallet;

    private WalletSkill() {}

    /**
     * Attempt to handle a user message as a wallet query.
     *
     * @param input Raw user message text.
     * @return A formatted string response, or null if this skill doesn't handle it.
     */
    public static String tryHandle(String input) {
        if (input == null || input.isEmpty()) return null;
        String lower = input.toLowerCase(Locale.US);

        // Wallet-related keyword check
        if (!containsAny(lower, "balance", "funds", "wallet", "shongololo", "₷",
                         "transaction", "history", "paid", "received", "sent",
                         "address", "send", "pay", "transfer", "limit",
                         "per tap", "daily", "location", "where am i",
                         "how much", "what do i have")) {
            return null;
        }

        IShongololoWallet wallet = getWallet();
        if (wallet == null) {
            return "The Shongololo Wallet service is not available right now.";
        }

        try {
            // ── Balance ───────────────────────────────────────────
            if (containsAny(lower, "balance", "funds", "how much", "what do i have")) {
                return handleBalance(wallet);
            }

            // ── Transaction history ───────────────────────────────
            if (containsAny(lower, "transaction", "history", "paid", "received", "sent")) {
                return handleHistory(wallet);
            }

            // ── Wallet address ─────────────────────────────────────
            if (containsAny(lower, "address", "wallet address")) {
                return handleAddress(wallet);
            }

            // ── Send / pay ─────────────────────────────────────────
            if (containsAny(lower, "send", "pay", "transfer")) {
                return handleSend(lower);
            }

            // ── Limits ─────────────────────────────────────────────
            if (containsAny(lower, "limit", "per tap", "daily", "max")) {
                return handleLimits(wallet);
            }

            // ── Location context ───────────────────────────────────
            if (containsAny(lower, "location context", "where am i", "location limit")) {
                return handleLocation(wallet);
            }

            // Generic wallet info
            return handleBalance(wallet);

        } catch (RemoteException e) {
            Log.e(TAG, "WalletSkill IPC error", e);
            return "I couldn't reach the wallet service right now.";
        }
    }

    /* ── Handlers ──────────────────────────────────────────── */

    private static String handleBalance(IShongololoWallet w) throws RemoteException {
        if (!w.hasWallet()) return "Your wallet hasn't been set up yet. Open the Shongololo Wallet app to get started.";
        WalletBalance b = w.getBalance();
        StringBuilder sb = new StringBuilder();
        sb.append("**Shongololo Balance**\n");
        sb.append("Available: ").append(fmt(b.availableCents)).append("\n");
        if (b.pendingInCents > 0) sb.append("Incoming (pending): ").append(fmt(b.pendingInCents)).append("\n");
        if (b.pendingOutCents > 0) sb.append("Outgoing (settling): ").append(fmt(b.pendingOutCents)).append("\n");
        sb.append("Daily remaining: ").append(fmt(b.dailyRemainingCents()));
        int pending = w.getPendingCount();
        if (pending > 0) sb.append("\n⚠ ").append(pending).append(" transaction(s) pending settlement");
        return sb.toString();
    }

    private static String handleHistory(IShongololoWallet w) throws RemoteException {
        List<ShongololoTransaction> txs = w.getTransactions(5, 0);
        if (txs == null || txs.isEmpty()) return "No transactions yet.";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
        StringBuilder sb = new StringBuilder("**Recent Transactions**\n");
        for (ShongololoTransaction tx : txs) {
            boolean isSend = tx.type == ShongololoTransaction.TYPE_SEND;
            String date = sdf.format(new Date(tx.createdAtMs));
            String peer = isSend ? tx.receiverPubkey : tx.senderDeviceId;
            if (peer != null && peer.length() > 12) {
                peer = peer.substring(0, 8) + "…";
            }
            String status = statusLabel(tx.status);
            sb.append(isSend ? "↑ Sent " : "↓ Received ")
              .append(fmt(tx.amountCents))
              .append(peer != null && !peer.isEmpty() ? " · " + peer : "")
              .append(" · ").append(date)
              .append(status.isEmpty() ? "" : " (" + status + ")")
              .append("\n");
        }
        return sb.toString().trim();
    }

    private static String handleAddress(IShongololoWallet w) throws RemoteException {
        WalletKey key = w.getWalletKey();
        if (key == null) return "No wallet key found.";
        return "**Your Wallet Address**\n`" + key.walletAddress + "`\n\nShare this with others so they can send you ₷.";
    }

    private static String handleSend(String lower) {
        // Extract amount if mentioned
        double amount = 0;
        String[] words = lower.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i].replaceAll("[₷,]", "");
            try { amount = Double.parseDouble(w); break; } catch (NumberFormatException ignored) {}
        }
        String amountStr = amount > 0
                ? String.format(Locale.US, "₷%.2f", amount)
                : "the amount you want to send";
        return "To send " + amountStr + ":\n1. Open the **Shongololo Wallet** app\n"
             + "2. Tap **TAP TO PAY** and enter the amount\n"
             + "3. Hold your phone close to the recipient's phone\n\n"
             + "NFC transfers work offline — no internet needed.";
    }

    private static String handleLimits(IShongololoWallet w) throws RemoteException {
        long perTap  = w.getEffectivePerTapLimitCents(false);
        long lockScr = w.getEffectivePerTapLimitCents(true);
        long daily   = w.getEffectiveDailyLimitCents();
        long offRemain = w.getOfflineAccumulationCents();
        return "**Effective Transaction Limits**\n"
             + "Per tap: " + fmt(perTap) + "\n"
             + "Lock screen: " + fmt(lockScr) + "\n"
             + "Daily remaining: " + fmt(w.getDailyRemainingCents()) + " of " + fmt(daily) + "\n"
             + "Offline accumulation used: " + fmt(offRemain);
    }

    private static String handleLocation(IShongololoWallet w) throws RemoteException {
        LocationContext loc = w.getLocationContext();
        String label = (loc.locationLabel != null && !loc.locationLabel.isEmpty())
                ? loc.locationLabel : loc.typeName();
        return "**Location Context**\n"
             + "Context: " + label + "\n"
             + "Per-tap limit: " + fmt(loc.perTapLimitCents) + "\n"
             + "Daily limit: " + fmt(loc.dailyLimitCents) + "\n"
             + "Confidence: " + (int) loc.confidencePercent + "%"
             + (loc.speedMs > 1f
                ? "\nSpeed: " + String.format(Locale.US, "%.0f km/h", loc.speedMs * 3.6f)
                : "");
    }

    /* ── Helpers ───────────────────────────────────────────── */

    private static String fmt(long cents) {
        return String.format(Locale.US, "₷%,d.%02d", cents / 100, Math.abs(cents % 100));
    }

    private static String statusLabel(int status) {
        switch (status) {
            case ShongololoTransaction.STATUS_PENDING_SETTLEMENT: return "pending";
            case ShongololoTransaction.STATUS_REVERSED:          return "reversed";
            case ShongololoTransaction.STATUS_REJECTED:          return "rejected";
            default:                                              return "";
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private static IShongololoWallet getWallet() {
        if (sWallet != null) return sWallet;
        try {
            IBinder b = ServiceManager.getService("circle.sdpkt");
            if (b != null) sWallet = IShongololoWallet.Stub.asInterface(b);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get circle.sdpkt", e);
        }
        return sWallet;
    }
}
