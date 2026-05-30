/*
 * Public-key + display-address handle for the local SDPKT wallet.
 * Returned by IShongololoWallet.getWalletKey().
 *
 * Fields are public so the WalletActivity can read them directly
 * (key.walletAddress) — same direct-field-access style the rest of the
 * SDPKT contract uses. shortAddress() returns a five-char prefix +
 * five-char suffix display form for surfaces that don't have room for
 * the full 32-byte address.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class WalletKey implements Parcelable {

    /** Full wallet address, base58 or bech32 form. */
    public String walletAddress;

    /** Ed25519 public-key fingerprint, hex (64 chars). */
    public String publicKeyHex;

    /** Unix-millis the wallet was initialised. */
    public long createdAtMs;

    public WalletKey() {
        this.walletAddress = "";
        this.publicKeyHex  = "";
        this.createdAtMs   = 0;
    }

    /** "5xkj…7w2c"-style compact display form for tight surfaces. */
    public String shortAddress() {
        if (walletAddress == null || walletAddress.length() < 12) {
            return walletAddress == null ? "" : walletAddress;
        }
        int n = walletAddress.length();
        return walletAddress.substring(0, 5) + "…" + walletAddress.substring(n - 5);
    }

    private WalletKey(Parcel in) {
        this.walletAddress = in.readString();
        this.publicKeyHex  = in.readString();
        this.createdAtMs   = in.readLong();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(walletAddress);
        out.writeString(publicKeyHex);
        out.writeLong(createdAtMs);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<WalletKey> CREATOR =
            new Parcelable.Creator<WalletKey>() {
        @Override public WalletKey createFromParcel(Parcel in) { return new WalletKey(in); }
        @Override public WalletKey[] newArray(int n) { return new WalletKey[n]; }
    };
}
