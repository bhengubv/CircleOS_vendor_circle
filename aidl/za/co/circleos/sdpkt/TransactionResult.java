/*
 * Returned by acceptIncomingTransfer. The simple shape — outcome enum
 * + resulting tx id + balance after — is enough for the WalletActivity
 * accept handler to update the UI in one Toast + one balance refresh.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class TransactionResult implements Parcelable {

    public static final int OUTCOME_OK         = 0;
    public static final int OUTCOME_REJECTED   = 1;
    public static final int OUTCOME_INSUFFICIENT = 2;
    public static final int OUTCOME_NETWORK_ERROR = 3;

    /** One of OUTCOME_*. */
    public int outcome;

    /** New tx id if outcome == OUTCOME_OK, else empty. */
    public String txId;

    /** Balance after the operation, even when outcome != OUTCOME_OK. */
    public long newBalanceCents;

    /** Human-readable explanation if outcome != OUTCOME_OK. */
    public String message;

    public TransactionResult() {
        this.outcome         = OUTCOME_OK;
        this.txId            = "";
        this.newBalanceCents = 0;
        this.message         = "";
    }

    private TransactionResult(Parcel in) {
        this.outcome         = in.readInt();
        this.txId            = in.readString();
        this.newBalanceCents = in.readLong();
        this.message         = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(outcome);
        out.writeString(txId);
        out.writeLong(newBalanceCents);
        out.writeString(message);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<TransactionResult> CREATOR =
            new Parcelable.Creator<TransactionResult>() {
        @Override public TransactionResult createFromParcel(Parcel in) { return new TransactionResult(in); }
        @Override public TransactionResult[] newArray(int n) { return new TransactionResult[n]; }
    };
}
