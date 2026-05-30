/*
 * Current settlement-queue state. WalletActivity reads
 * sync.state and switches on it: STATE_SYNCING shows the spinner,
 * STATE_OFFLINE flips the status row to "offline mode", STATE_IDLE
 * means everything is settled.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class SyncStatus implements Parcelable {

    public static final int STATE_IDLE    = 0;
    public static final int STATE_SYNCING = 1;
    public static final int STATE_OFFLINE = 2;
    public static final int STATE_ERROR   = 3;

    /** One of STATE_*. */
    public int state;

    /** Outstanding pending-settlement TXs. */
    public int pendingCount;

    /** Unix-millis of the last successful settlement. */
    public long lastSettlementMs;

    /** Reason for the most recent error, or empty if state != STATE_ERROR. */
    public String errorReason;

    public SyncStatus() {
        this.state            = STATE_IDLE;
        this.pendingCount     = 0;
        this.lastSettlementMs = 0;
        this.errorReason      = "";
    }

    private SyncStatus(Parcel in) {
        this.state            = in.readInt();
        this.pendingCount     = in.readInt();
        this.lastSettlementMs = in.readLong();
        this.errorReason      = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(state);
        out.writeInt(pendingCount);
        out.writeLong(lastSettlementMs);
        out.writeString(errorReason);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<SyncStatus> CREATOR =
            new Parcelable.Creator<SyncStatus>() {
        @Override public SyncStatus createFromParcel(Parcel in) { return new SyncStatus(in); }
        @Override public SyncStatus[] newArray(int n) { return new SyncStatus[n]; }
    };
}
