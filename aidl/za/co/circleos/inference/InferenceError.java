/*
 * Failure mode for IInferenceCallback.onError.
 * Field access pattern: error.code / error.message / error.recoverable.
 * Constants are referenced as InferenceError.ERROR_* by the
 * CircleInferenceClient wrapper.
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class InferenceError implements Parcelable {

    /** No model loaded or service not ready. */
    public static final int ERROR_NOT_READY           = 1;
    /** Caller doesn't hold com.circleos.permission.ACCESS_INFERENCE. */
    public static final int ERROR_PERMISSION_DENIED   = 2;
    /** Request rejected (bad parameters). */
    public static final int ERROR_BAD_REQUEST         = 3;
    /** Out of memory / over capacity. */
    public static final int ERROR_RESOURCE_EXHAUSTED  = 4;
    /** The model itself failed (corrupt weights, GPU fault, etc.). */
    public static final int ERROR_MODEL_FAILURE       = 5;
    /** Operation was cancelled (by us or by a newer call from the same UID). */
    public static final int ERROR_CANCELLED           = 6;
    /** Unspecified service-side fault. */
    public static final int ERROR_INTERNAL            = 99;

    public int     code;
    public String  message;
    /** Hint to the client: is retry likely to help? */
    public boolean recoverable;

    public InferenceError() {
        this.code        = ERROR_INTERNAL;
        this.message     = "";
        this.recoverable = false;
    }

    private InferenceError(Parcel in) {
        this.code        = in.readInt();
        this.message     = in.readString();
        this.recoverable = in.readInt() != 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(code);
        out.writeString(message);
        out.writeInt(recoverable ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<InferenceError> CREATOR =
            new Parcelable.Creator<InferenceError>() {
        @Override public InferenceError createFromParcel(Parcel in) { return new InferenceError(in); }
        @Override public InferenceError[] newArray(int n) { return new InferenceError[n]; }
    };
}
