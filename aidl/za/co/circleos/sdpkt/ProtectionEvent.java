/*
 * One device-protection event observed by the wallet. Returned by
 * IShongololoWallet.getProtectionEvents.
 *
 * Field surface read by SdpktTitanium WalletActivity (protection-log
 * dialog):
 *   ev.timestampMs
 *   ev.type / ev.typeName()  — what triggered the protection
 *   ev.amountCents           — tx amount if event is tx-related
 *   ev.reason                — short user-facing explanation
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class ProtectionEvent implements Parcelable {

    public static final int SEVERITY_INFO  = 0;
    public static final int SEVERITY_WARN  = 1;
    public static final int SEVERITY_BLOCK = 2;

    // Event type — drives the chip label / typeName().
    public static final int TYPE_RATE_LIMIT      = 0;
    public static final int TYPE_IOC_MATCH       = 1;
    public static final int TYPE_TAP_QUIRK       = 2;
    public static final int TYPE_AMOUNT_OUTLIER  = 3;
    public static final int TYPE_GEO_ANOMALY     = 4;
    public static final int TYPE_STRESS_SIGNAL   = 5;

    public long   timestampMs;
    public int    type;
    public int    severity;
    public String reason;
    public long   amountCents;
    public String relatedTxId;

    public ProtectionEvent() {
        this.timestampMs = 0;
        this.type        = TYPE_RATE_LIMIT;
        this.severity    = SEVERITY_INFO;
        this.reason      = "";
        this.amountCents = 0;
        this.relatedTxId = "";
    }

    /** Display label corresponding to {@link #type}. */
    public String typeName() {
        switch (type) {
            case TYPE_IOC_MATCH:      return "IoC match";
            case TYPE_TAP_QUIRK:      return "Tap quirk";
            case TYPE_AMOUNT_OUTLIER: return "Amount outlier";
            case TYPE_GEO_ANOMALY:    return "Geo anomaly";
            case TYPE_STRESS_SIGNAL:  return "Stress signal";
            default:                  return "Rate limit";
        }
    }

    private ProtectionEvent(Parcel in) {
        this.timestampMs = in.readLong();
        this.type        = in.readInt();
        this.severity    = in.readInt();
        this.reason      = in.readString();
        this.amountCents = in.readLong();
        this.relatedTxId = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(timestampMs);
        out.writeInt(type);
        out.writeInt(severity);
        out.writeString(reason);
        out.writeLong(amountCents);
        out.writeString(relatedTxId);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ProtectionEvent> CREATOR =
            new Parcelable.Creator<ProtectionEvent>() {
        @Override public ProtectionEvent createFromParcel(Parcel in) { return new ProtectionEvent(in); }
        @Override public ProtectionEvent[] newArray(int n) { return new ProtectionEvent[n]; }
    };
}
