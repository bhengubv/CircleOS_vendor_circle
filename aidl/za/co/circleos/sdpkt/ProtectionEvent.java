/*
 * One device-protection event observed by the wallet. Returned by
 * IShongololoWallet.getProtectionEvents.
 *
 * Examples: an attempted NFC session at a flagged terminal, a too-fast
 * sequence of transactions, a transfer to a counterparty marked as
 * fraudulent on the Data Acuity feed.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class ProtectionEvent implements Parcelable {

    public static final int SEVERITY_INFO    = 0;
    public static final int SEVERITY_WARN    = 1;
    public static final int SEVERITY_BLOCK   = 2;

    /** Unix-millis of the event. */
    public long timestampMs;

    /** One of SEVERITY_*. */
    public int severity;

    /**
     * Short category — "rate_limit", "ioc_match", "tap_quirk",
     * "amount_outlier", "geo_anomaly".
     */
    public String category;

    /** Human-readable summary. */
    public String summary;

    /** Associated tx id (if applicable), or empty. */
    public String relatedTxId;

    public ProtectionEvent() {
        this.timestampMs = 0;
        this.severity    = SEVERITY_INFO;
        this.category    = "";
        this.summary     = "";
        this.relatedTxId = "";
    }

    private ProtectionEvent(Parcel in) {
        this.timestampMs = in.readLong();
        this.severity    = in.readInt();
        this.category    = in.readString();
        this.summary     = in.readString();
        this.relatedTxId = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(timestampMs);
        out.writeInt(severity);
        out.writeString(category);
        out.writeString(summary);
        out.writeString(relatedTxId);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ProtectionEvent> CREATOR =
            new Parcelable.Creator<ProtectionEvent>() {
        @Override public ProtectionEvent createFromParcel(Parcel in) { return new ProtectionEvent(in); }
        @Override public ProtectionEvent[] newArray(int n) { return new ProtectionEvent[n]; }
    };
}
