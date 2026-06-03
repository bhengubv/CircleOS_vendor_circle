/*
 * Spend rollup for the wallet's Insights tab + Butler's spending-
 * analytics skill.
 *
 * Field surface read by Butler.WalletSkill (handleAnalytics):
 *   s.totalSentCents
 *   s.totalReceivedCents
 *   s.txCount             — total all-time
 *   s.txSentCount
 *   s.txReceivedCount
 *   s.avgSentCents
 *   s.peakDaySpentCents
 *   s.topPeerShort        — display short id of most-frequent peer
 *   s.blockedTxCount      — protection-engine blocks (suspected fraud)
 */
package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class AnalyticsSummary implements Parcelable {

    public long totalSentCents;
    public long totalReceivedCents;
    public int  txCount;
    public int  txSentCount;
    public int  txReceivedCount;
    public long avgSentCents;
    public long peakDaySpentCents;
    public String topPeerShort;
    public int  blockedTxCount;

    public AnalyticsSummary() {
        this.totalSentCents     = 0;
        this.totalReceivedCents = 0;
        this.txCount            = 0;
        this.txSentCount        = 0;
        this.txReceivedCount    = 0;
        this.avgSentCents       = 0;
        this.peakDaySpentCents  = 0;
        this.topPeerShort       = "";
        this.blockedTxCount     = 0;
    }

    private AnalyticsSummary(Parcel in) {
        this.totalSentCents     = in.readLong();
        this.totalReceivedCents = in.readLong();
        this.txCount            = in.readInt();
        this.txSentCount        = in.readInt();
        this.txReceivedCount    = in.readInt();
        this.avgSentCents       = in.readLong();
        this.peakDaySpentCents  = in.readLong();
        this.topPeerShort       = in.readString();
        this.blockedTxCount     = in.readInt();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(totalSentCents);
        out.writeLong(totalReceivedCents);
        out.writeInt(txCount);
        out.writeInt(txSentCount);
        out.writeInt(txReceivedCount);
        out.writeLong(avgSentCents);
        out.writeLong(peakDaySpentCents);
        out.writeString(topPeerShort);
        out.writeInt(blockedTxCount);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<AnalyticsSummary> CREATOR =
            new Parcelable.Creator<AnalyticsSummary>() {
        @Override public AnalyticsSummary createFromParcel(Parcel in) { return new AnalyticsSummary(in); }
        @Override public AnalyticsSummary[] newArray(int n) { return new AnalyticsSummary[n]; }
    };
}
