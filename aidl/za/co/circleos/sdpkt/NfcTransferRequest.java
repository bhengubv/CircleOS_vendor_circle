/*
 * Parameter object for IShongololoWallet.beginNfcSession.
 *
 * Constructed by the caller via the no-arg constructor, fields filled
 * in directly. WalletActivity uses it as `req.lockScreenMode = true`
 * for quick-pay tile launches and `req.lockScreenMode = false` for
 * full-app payments.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class NfcTransferRequest implements Parcelable {

    /**
     * True if the request is coming from the lock-screen quick-pay
     * surface — switches the wallet into reduced-limits mode.
     */
    public boolean lockScreenMode;

    /**
     * Explicit amount in cents the wallet is asked to send. Zero means
     * "let the user enter the amount in the dialog". Consumers
     * (WalletActivity, QuickPayTileService) read req.amountCents and
     * the wallet service applies the per-tap cap on top.
     */
    public long amountCents;

    /**
     * Optional memo to attach to the resulting transaction. Empty
     * skips the memo field.
     */
    public String memo;

    public NfcTransferRequest() {
        this.lockScreenMode = false;
        this.amountCents    = 0;
        this.memo           = "";
    }

    private NfcTransferRequest(Parcel in) {
        this.lockScreenMode = in.readInt() != 0;
        this.amountCents    = in.readLong();
        this.memo           = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(lockScreenMode ? 1 : 0);
        out.writeLong(amountCents);
        out.writeString(memo);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<NfcTransferRequest> CREATOR =
            new Parcelable.Creator<NfcTransferRequest>() {
        @Override public NfcTransferRequest createFromParcel(Parcel in) { return new NfcTransferRequest(in); }
        @Override public NfcTransferRequest[] newArray(int n) { return new NfcTransferRequest[n]; }
    };
}
