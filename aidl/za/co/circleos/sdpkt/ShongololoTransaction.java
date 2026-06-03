/*
 * One entry in the local transaction history.
 *
 * Field surface read by Butler.WalletSkill + SdpktTitanium.WalletActivity:
 *   tx.type            — direction enum, TYPE_*
 *   tx.status          — settlement status, STATUS_*
 *   tx.amountCents     — wallet smallest unit, always positive
 *   tx.currency        — ISO 4217
 *   tx.createdAtMs     — initiation time
 *   tx.settledAtMs     — settled time, 0 while pending
 *   tx.receiverPubkey  — recipient pubkey (set when type == TYPE_SEND)
 *   tx.senderDeviceId  — sender mesh id (set when type == TYPE_RECV)
 *   tx.memo            — optional memo, ≤ 64 chars
 *   tx.failureReason   — empty unless STATUS_REJECTED / _REVERSED
 */
package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class ShongololoTransaction implements Parcelable {

    // Direction
    public static final int TYPE_SEND   = 1;
    public static final int TYPE_RECV   = 2;
    public static final int TYPE_REFUND = 3;
    public static final int TYPE_FEE    = 4;

    // Status — used by Butler.WalletSkill.statusLabel switch
    public static final int STATUS_SETTLED            = 0;
    public static final int STATUS_PENDING_SETTLEMENT = 1;
    public static final int STATUS_REVERSED           = 2;
    public static final int STATUS_REJECTED           = 3;
    public static final int STATUS_EXPIRED            = 4;

    /** UUID hex (no dashes). */
    public String txId;

    /** One of TYPE_*. */
    public int type;

    /** One of STATUS_*. */
    public int status;

    /** Amount in the wallet's smallest unit. Always positive. */
    public long amountCents;

    /** ISO 4217 currency code, e.g. "ZAR". */
    public String currency;

    /** Recipient pubkey when type == TYPE_SEND, else "". */
    public String receiverPubkey;

    /** Sender rotating device id when type == TYPE_RECV, else "". */
    public String senderDeviceId;

    /** Free-form memo, possibly empty. */
    public String memo;

    /** Unix ms when the user initiated the tx. */
    public long createdAtMs;

    /** Unix ms when the central ledger settled it, or 0 if pending. */
    public long settledAtMs;

    /** Transport: "online" | "nfc" | "mesh" | "offline_queue". */
    public String channel;

    /** Reason for STATUS_REJECTED / STATUS_REVERSED, else "". */
    public String failureReason;

    public ShongololoTransaction() {
        this.txId           = "";
        this.type           = TYPE_SEND;
        this.status         = STATUS_SETTLED;
        this.amountCents    = 0;
        this.currency       = "";
        this.receiverPubkey = "";
        this.senderDeviceId = "";
        this.memo           = "";
        this.createdAtMs    = 0;
        this.settledAtMs    = 0;
        this.channel        = "";
        this.failureReason  = "";
    }

    private ShongololoTransaction(Parcel in) {
        this.txId           = in.readString();
        this.type           = in.readInt();
        this.status         = in.readInt();
        this.amountCents    = in.readLong();
        this.currency       = in.readString();
        this.receiverPubkey = in.readString();
        this.senderDeviceId = in.readString();
        this.memo           = in.readString();
        this.createdAtMs    = in.readLong();
        this.settledAtMs    = in.readLong();
        this.channel        = in.readString();
        this.failureReason  = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(txId);
        out.writeInt(type);
        out.writeInt(status);
        out.writeLong(amountCents);
        out.writeString(currency);
        out.writeString(receiverPubkey);
        out.writeString(senderDeviceId);
        out.writeString(memo);
        out.writeLong(createdAtMs);
        out.writeLong(settledAtMs);
        out.writeString(channel);
        out.writeString(failureReason);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ShongololoTransaction> CREATOR =
            new Parcelable.Creator<ShongololoTransaction>() {
        @Override public ShongololoTransaction createFromParcel(Parcel in) { return new ShongololoTransaction(in); }
        @Override public ShongololoTransaction[] newArray(int n) { return new ShongololoTransaction[n]; }
    };
}
