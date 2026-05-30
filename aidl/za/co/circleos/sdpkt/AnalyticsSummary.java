/*
 * Daily / weekly / monthly spend rollup for the wallet's Insights tab.
 * Returned by IShongololoWallet.getAnalyticsSummary.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class AnalyticsSummary implements Parcelable {

    /** Total spend in cents over the past 24 hours. */
    public long spendLast24hCents;

    /** Total spend in cents over the past 7 days. */
    public long spendLast7dCents;

    /** Total spend in cents over the past 30 days. */
    public long spendLast30dCents;

    /** Average tx amount in cents over the past 30 days. */
    public long averageTxCents;

    /** Number of transactions in the past 30 days. */
    public int txCount30d;

    /** Top counterparty by frequency in the past 30 days, or empty. */
    public String topCounterparty;

    public AnalyticsSummary() {
        this.spendLast24hCents = 0;
        this.spendLast7dCents  = 0;
        this.spendLast30dCents = 0;
        this.averageTxCents    = 0;
        this.txCount30d        = 0;
        this.topCounterparty   = "";
    }

    private AnalyticsSummary(Parcel in) {
        this.spendLast24hCents = in.readLong();
        this.spendLast7dCents  = in.readLong();
        this.spendLast30dCents = in.readLong();
        this.averageTxCents    = in.readLong();
        this.txCount30d        = in.readInt();
        this.topCounterparty   = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(spendLast24hCents);
        out.writeLong(spendLast7dCents);
        out.writeLong(spendLast30dCents);
        out.writeLong(averageTxCents);
        out.writeInt(txCount30d);
        out.writeString(topCounterparty);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<AnalyticsSummary> CREATOR =
            new Parcelable.Creator<AnalyticsSummary>() {
        @Override public AnalyticsSummary createFromParcel(Parcel in) { return new AnalyticsSummary(in); }
        @Override public AnalyticsSummary[] newArray(int n) { return new AnalyticsSummary[n]; }
    };
}
