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

    boolean hasWallet();
    void initializeWallet();
    WalletKey getWalletKey();

    // ----- balance + history --------------------------------------------

    WalletBalance getBalance();
    List<ShongololoTransaction> getTransactions(int limit, int offset);
    SyncStatus getSyncStatus();
    int getPendingCount();

    // ----- limits + location context ------------------------------------

    LocationContext getLocationContext();
    boolean isProtectionActive();
    CalibrationState getCalibrationState();

    /** Effective per-tap cap (location-tuned). */
    long getEffectivePerTapLimitCents(boolean lockScreen);
    /** Effective per-day cap (location-tuned). */
    long getEffectiveDailyLimitCents();
    /** Headroom against today's cap. */
    long getDailyRemainingCents();
    /** Total of pending-offline transactions awaiting settlement. */
    long getOfflineAccumulationCents();

    // ----- maintenance --------------------------------------------------

    void forceSyncNow();
    void reportFalsePositive();

    // ----- NFC peer-to-peer ---------------------------------------------

    String beginNfcSession(in NfcTransferRequest req);
    String processNfcMessage(in String sessionId, in String incomingB64);
    void cancelNfcSession(in String sessionId);
    TransactionResult acceptIncomingTransfer(in String sessionId);
    void declineIncomingTransfer(in String sessionId);

    // ----- introspection ------------------------------------------------

    List<ProtectionEvent> getProtectionEvents(int limit);
    AnalyticsSummary getAnalyticsSummary();
    String exportTransactions(in String format);
}
