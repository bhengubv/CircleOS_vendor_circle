/*
 * Binder surface for SDPKT Titanium ("Shongololo" — isiZulu for the
 * millipede that carries lots of small things one segment at a time, an
 * apt metaphor for a wallet that holds many small offline transactions
 * before settling them all at once).
 *
 * Published under ServiceManager.getService("circle_wallet") by
 * com.circleos.server.wallet.ShongololoWalletService.
 *
 * Permissions:
 *   QUERY_SDPKT  — read-only status methods (UI / tile)
 *   USE_SDPKT    — send + NFC session methods
 *   MANAGE_SDPKT — initialise wallet, force sync, false-positive
 *                  reports (signature|privileged)
 *
 * Any method that mutates wallet state additionally requires a fresh
 * lock-screen authentication within the last 30 seconds.
 */

package za.co.circleos.sdpkt;

import za.co.circleos.sdpkt.AnalyticsSummary;
import za.co.circleos.sdpkt.CalibrationState;
import za.co.circleos.sdpkt.LocationContext;
import za.co.circleos.sdpkt.NfcTransferRequest;
import za.co.circleos.sdpkt.ProtectionEvent;
import za.co.circleos.sdpkt.ShongololoTransaction;
import za.co.circleos.sdpkt.SyncStatus;
import za.co.circleos.sdpkt.TransactionResult;
import za.co.circleos.sdpkt.WalletBalance;
import za.co.circleos.sdpkt.WalletKey;

interface IShongololoWallet {

    // ----- initialisation ------------------------------------------------

    /** True if a wallet has been initialised on this device. */
    boolean hasWallet();

    /**
     * Generate a new wallet key inside the TEE, persist its public part,
     * register with the central ledger, and become hasWallet()=true.
     */
    void initializeWallet();

    /** The local wallet's public key + display address. */
    WalletKey getWalletKey();

    // ----- balance + history --------------------------------------------

    /** Confirmed + pending + total cents. */
    WalletBalance getBalance();

    /**
     * Last {@code limit} transactions starting at {@code offset} (zero
     * is the most recent). Reverse-chronological.
     */
    List<ShongololoTransaction> getTransactions(int limit, int offset);

    /** Current settlement-queue status (online/syncing/offline + counts). */
    SyncStatus getSyncStatus();

    /** Number of transactions still pending settlement. */
    int getPendingCount();

    /**
     * The current Location Context — used to pick the per-tap spending
     * cap (e.g. higher at home, lower in transit).
     */
    LocationContext getLocationContext();

    /** True if the device-protection layer is currently engaged. */
    boolean isProtectionActive();

    /** Per-tap-limit calibration state — learning / settled / overridden. */
    CalibrationState getCalibrationState();

    /**
     * Effective per-tap limit in cents, honoring lock-screen mode if
     * the caller is a tile / lock-screen surface.
     */
    long getEffectivePerTapLimitCents(boolean lockScreen);

    // ----- maintenance --------------------------------------------------

    /**
     * Force an immediate settlement attempt against the central ledger.
     * Useful after the user reconnects to the internet and wants the
     * pending queue cleared without waiting for the next scheduled
     * sync.
     */
    void forceSyncNow();

    /**
     * The user just told us "that transaction was actually mine" after
     * we'd flagged it for review. Removes the most recent flag and
     * lowers the suspicion score for the related counterparty.
     */
    void reportFalsePositive();

    // ----- NFC peer-to-peer ---------------------------------------------

    /**
     * Open an NFC P2P transfer session. Returns an opaque session id
     * the caller passes to subsequent processNfcMessage / cancel /
     * accept calls.
     */
    String beginNfcSession(in NfcTransferRequest req);

    /**
     * Pass an incoming NFC APDU (base64) through the wallet and get the
     * next outgoing APDU (base64) back. Empty string means the session
     * is complete on our side and the next ISO-DEP exchange should
     * terminate.
     */
    String processNfcMessage(in String sessionId, in String incomingB64);

    /** Tear down an NFC session without committing anything. */
    void cancelNfcSession(in String sessionId);

    /**
     * Accept an incoming peer-to-peer transfer the user has just
     * approved on screen. Returns the on-wallet TransactionResult.
     */
    TransactionResult acceptIncomingTransfer(in String sessionId);

    /** The user said no — refund the sender and close out the session. */
    void declineIncomingTransfer(in String sessionId);

    // ----- introspection ------------------------------------------------

    /** Recent device-protection events, most recent first. */
    List<ProtectionEvent> getProtectionEvents(int limit);

    /** Daily / weekly / monthly spend rollup for the Insights tab. */
    AnalyticsSummary getAnalyticsSummary();

    /**
     * Export the entire local transaction history to a file on the
     * sandboxed export dir. Returns the absolute path.
     * @param format  "csv" | "json"
     */
    String exportTransactions(in String format);
}
