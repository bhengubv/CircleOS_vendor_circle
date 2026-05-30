/*
 * Balance snapshot returned by IShongololoWallet.getBalance().
 *
 * Three slices: settled, in-flight (pending settlement), and the sum.
 * All values are in the smallest unit of the wallet's currency (cents
 * for ZAR/USD, paise for INR, etc.).
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class WalletBalance implements Parcelable {

    /** Settled amount on the central ledger. */
    public long confirmedCents;

    /** Total of transactions in the offline / settlement queue. */
    public long pendingCents;

    /** confirmed + pending. */
    public long totalCents;

    /** ISO 4217 currency code, e.g. "ZAR". */
    public String currency;

    public WalletBalance() {
        this.confirmedCents = 0;
        this.pendingCents   = 0;
        this.totalCents     = 0;
        this.currency       = "";
    }

    private WalletBalance(Parcel in) {
        this.confirmedCents = in.readLong();
        this.pendingCents   = in.readLong();
        this.totalCents     = in.readLong();
        this.currency       = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(confirmedCents);
        out.writeLong(pendingCents);
        out.writeLong(totalCents);
        out.writeString(currency);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<WalletBalance> CREATOR =
            new Parcelable.Creator<WalletBalance>() {
        @Override public WalletBalance createFromParcel(Parcel in) { return new WalletBalance(in); }
        @Override public WalletBalance[] newArray(int n) { return new WalletBalance[n]; }
    };
}
