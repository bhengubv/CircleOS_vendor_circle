/*
 * Balance snapshot returned by IShongololoWallet.getBalance().
 *
 * Field surface read by Butler.WalletSkill + SdpktTitanium.WalletActivity:
 *   availableCents     — spendable now
 *   pendingInCents     — inbound, not yet settled
 *   pendingOutCents    — outbound, settling
 *   dailySpentCents    — total spent today against the daily cap
 *   dailyLimitCents    — today's daily cap (location-tuned)
 *
 * dailyRemainingCents() is a derived convenience the wallet skill
 * uses inline.
 */
package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class WalletBalance implements Parcelable {

    public long availableCents;
    public long pendingInCents;
    public long pendingOutCents;
    public long dailySpentCents;
    public long dailyLimitCents;
    /** ISO 4217 currency code, e.g. "ZAR". */
    public String currency;

    public WalletBalance() {
        this.availableCents  = 0;
        this.pendingInCents  = 0;
        this.pendingOutCents = 0;
        this.dailySpentCents = 0;
        this.dailyLimitCents = 0;
        this.currency        = "";
    }

    /** Remaining headroom against today's cap. */
    public long dailyRemainingCents() {
        long r = dailyLimitCents - dailySpentCents;
        return r < 0 ? 0 : r;
    }

    private WalletBalance(Parcel in) {
        this.availableCents  = in.readLong();
        this.pendingInCents  = in.readLong();
        this.pendingOutCents = in.readLong();
        this.dailySpentCents = in.readLong();
        this.dailyLimitCents = in.readLong();
        this.currency        = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(availableCents);
        out.writeLong(pendingInCents);
        out.writeLong(pendingOutCents);
        out.writeLong(dailySpentCents);
        out.writeLong(dailyLimitCents);
        out.writeString(currency);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<WalletBalance> CREATOR =
            new Parcelable.Creator<WalletBalance>() {
        @Override public WalletBalance createFromParcel(Parcel in) { return new WalletBalance(in); }
        @Override public WalletBalance[] newArray(int n) { return new WalletBalance[n]; }
    };
}
