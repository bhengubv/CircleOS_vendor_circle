/*
 * Returned by acceptIncomingTransfer.
 *
 * Consumer (WalletActivity) reads res.success + res.errorMessage to
 * branch on outcome. The outcome int + txId + newBalanceCents stay
 * for richer downstream UI / analytics.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class TransactionResult implements Parcelable {

    public static final int OUTCOME_OK            = 0;
    public static final int OUTCOME_REJECTED      = 1;
    public static final int OUTCOME_INSUFFICIENT  = 2;
    public static final int OUTCOME_NETWORK_ERROR = 3;

    /** True iff outcome == OUTCOME_OK. Read directly by WalletActivity. */
    public boolean success;

    /** One of OUTCOME_*. */
    public int outcome;

    /** New tx id when success == true, else empty. */
    public String txId;

    /** Balance after the operation, even when success == false. */
    public long newBalanceCents;

    /** Human-readable failure reason when success == false. */
    public String errorMessage;

    public TransactionResult() {
        this.success         = false;
        this.outcome         = OUTCOME_OK;
        this.txId            = "";
        this.newBalanceCents = 0;
        this.errorMessage    = "";
    }

    private TransactionResult(Parcel in) {
        this.success         = in.readInt() != 0;
        this.outcome         = in.readInt();
        this.txId            = in.readString();
        this.newBalanceCents = in.readLong();
        this.errorMessage    = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(success ? 1 : 0);
        out.writeInt(outcome);
        out.writeString(txId);
        out.writeLong(newBalanceCents);
        out.writeString(errorMessage);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<TransactionResult> CREATOR =
            new Parcelable.Creator<TransactionResult>() {
        @Override public TransactionResult createFromParcel(Parcel in) { return new TransactionResult(in); }
        @Override public TransactionResult[] newArray(int n) { return new TransactionResult[n]; }
    };
}
