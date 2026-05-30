/*
 * One entry in the local transaction history. WalletActivity branches
 * on tx.type — TYPE_SEND vs TYPE_RECV — to colour and frame the row.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class ShongololoTransaction implements Parcelable {

    public static final int TYPE_SEND   = 1;
    public static final int TYPE_RECV   = 2;
    public static final int TYPE_REFUND = 3;
    public static final int TYPE_FEE    = 4;

    /** UUID hex (no dashes). */
    public String txId;

    /** One of TYPE_*. */
    public int type;

    /** Amount in the wallet's smallest unit. Always positive. */
    public long amountCents;

    /** ISO 4217 currency code, e.g. "ZAR". */
    public String currency;

    /** Counterparty's display label, possibly empty for non-NFC sends. */
    public String counterparty;

    /** Free-form memo, possibly empty. Max 64 chars enforced at send. */
    public String memo;

    /** Unix-millis the user initiated the tx. */
    public long createdAtMs;

    /** Unix-millis the central ledger settled it, or 0 if still pending. */
    public long settledAtMs;

    /** "pending" | "settled" | "rejected" | "expired". */
    public String state;

    /** "online" | "nfc" | "mesh" | "offline_queue". */
    public String channel;

    public ShongololoTransaction() {
        this.txId         = "";
        this.type         = TYPE_SEND;
        this.amountCents  = 0;
        this.currency     = "";
        this.counterparty = "";
        this.memo         = "";
        this.createdAtMs  = 0;
        this.settledAtMs  = 0;
        this.state        = "";
        this.channel      = "";
    }

    private ShongololoTransaction(Parcel in) {
        this.txId         = in.readString();
        this.type         = in.readInt();
        this.amountCents  = in.readLong();
        this.currency     = in.readString();
        this.counterparty = in.readString();
        this.memo         = in.readString();
        this.createdAtMs  = in.readLong();
        this.settledAtMs  = in.readLong();
        this.state        = in.readString();
        this.channel      = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(txId);
        out.writeInt(type);
        out.writeLong(amountCents);
        out.writeString(currency);
        out.writeString(counterparty);
        out.writeString(memo);
        out.writeLong(createdAtMs);
        out.writeLong(settledAtMs);
        out.writeString(state);
        out.writeString(channel);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ShongololoTransaction> CREATOR =
            new Parcelable.Creator<ShongololoTransaction>() {
        @Override public ShongololoTransaction createFromParcel(Parcel in) { return new ShongololoTransaction(in); }
        @Override public ShongololoTransaction[] newArray(int n) { return new ShongololoTransaction[n]; }
    };
}
