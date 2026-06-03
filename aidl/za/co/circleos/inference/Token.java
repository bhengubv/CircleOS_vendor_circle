/*
 * A single streamed token. Delivered via IInferenceCallback.onToken.
 * Field access: token.text — that's all the clients touch today.
 * The other fields are reserved for future telemetry (per-token log
 * probabilities, position in stream, etc.) without an AIDL bump.
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class Token implements Parcelable {

    /** The token's surface form (1+ chars). */
    public String text;

    /** Position in the output stream, 0-indexed. */
    public int position;

    /** Log-probability of this token, or Float.NaN if not available. */
    public float logProb;

    /**
     * True iff this is the final token of the stream. InferenceHttpServer
     * propagates it as the Ollama-compat {@code done} field on each
     * streamed chunk.
     */
    public boolean isFinal;

    public Token() {
        this.text     = "";
        this.position = 0;
        this.logProb  = Float.NaN;
        this.isFinal  = false;
    }

    private Token(Parcel in) {
        this.text     = in.readString();
        this.position = in.readInt();
        this.logProb  = in.readFloat();
        this.isFinal  = in.readInt() != 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(text);
        out.writeInt(position);
        out.writeFloat(logProb);
        out.writeInt(isFinal ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<Token> CREATOR =
            new Parcelable.Creator<Token>() {
        @Override public Token createFromParcel(Parcel in) { return new Token(in); }
        @Override public Token[] newArray(int n) { return new Token[n]; }
    };
}
