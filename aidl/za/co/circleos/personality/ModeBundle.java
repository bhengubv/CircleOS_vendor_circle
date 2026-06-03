/*
 * Bundle metadata for a downloadable mode (tier 2/3). Returned by
 * ICirclePersonalityManager.getBundleInfo and used to show the
 * "Download N MB" confirmation dialog in ModeChooserActivity and
 * PersonalityMainActivity. Fields are read directly:
 *   bundle.displayName, bundle.sizeBytes.
 */
package za.co.circleos.personality;

import android.os.Parcel;
import android.os.Parcelable;

public final class ModeBundle implements Parcelable {

    public String displayName;
    public long   sizeBytes;
    /** sha256 of the bundle artifact for verification. */
    public String sha256;
    /** Bundle schema version. */
    public int    version;

    public ModeBundle() {
        this.displayName = "";
        this.sizeBytes   = 0L;
        this.sha256      = "";
        this.version     = 0;
    }

    private ModeBundle(Parcel in) {
        this.displayName = in.readString();
        this.sizeBytes   = in.readLong();
        this.sha256      = in.readString();
        this.version     = in.readInt();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(displayName);
        out.writeLong(sizeBytes);
        out.writeString(sha256);
        out.writeInt(version);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ModeBundle> CREATOR =
            new Parcelable.Creator<ModeBundle>() {
        @Override public ModeBundle createFromParcel(Parcel in) { return new ModeBundle(in); }
        @Override public ModeBundle[] newArray(int n) { return new ModeBundle[n]; }
    };
}
