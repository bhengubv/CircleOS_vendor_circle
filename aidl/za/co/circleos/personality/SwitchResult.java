/*
 * Outcome of any mutating call on ICirclePersonalityManager
 * (activate / create / update / delete / import). Fields accessed
 * directly: r.success, r.requiresBundle, r.pendingBundleId,
 * r.errorMessage.
 */
package za.co.circleos.personality;

import android.os.Parcel;
import android.os.Parcelable;

public final class SwitchResult implements Parcelable {

    /** True iff the requested operation completed. */
    public boolean success;

    /**
     * If true, the requested mode is downloadable but not yet present.
     * Caller should kick off downloadBundle({@link #pendingBundleId})
     * and retry once IBundleCallback.onComplete fires success.
     */
    public boolean requiresBundle;

    /** Bundle id to download. Empty when {@link #requiresBundle} is false. */
    public String pendingBundleId;

    /** Human-readable failure reason when {@link #success}=false. */
    public String errorMessage;

    public SwitchResult() {
        this.success         = false;
        this.requiresBundle  = false;
        this.pendingBundleId = "";
        this.errorMessage    = "";
    }

    private SwitchResult(Parcel in) {
        this.success         = in.readInt() != 0;
        this.requiresBundle  = in.readInt() != 0;
        this.pendingBundleId = in.readString();
        this.errorMessage    = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(success        ? 1 : 0);
        out.writeInt(requiresBundle ? 1 : 0);
        out.writeString(pendingBundleId);
        out.writeString(errorMessage);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<SwitchResult> CREATOR =
            new Parcelable.Creator<SwitchResult>() {
        @Override public SwitchResult createFromParcel(Parcel in) { return new SwitchResult(in); }
        @Override public SwitchResult[] newArray(int n) { return new SwitchResult[n]; }
    };
}
