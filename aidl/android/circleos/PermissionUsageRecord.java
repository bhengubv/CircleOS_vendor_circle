/*
 * One observed permission access. Field surface read by
 * packages/apps/CircleSettings.AppPrivacyDetailActivity:
 *   r.timestamp   — unix ms
 *   r.permission  — full permission name, e.g. "android.permission.CAMERA"
 *   r.action      — "granted" / "fake" / "denied"
 *   r.extra       — short context string or null
 */
package android.circleos;

import android.os.Parcel;
import android.os.Parcelable;

public final class PermissionUsageRecord implements Parcelable {

    public long   timestamp;
    public String permission;
    public String action;
    public String extra;

    public PermissionUsageRecord() {
        this.timestamp  = 0;
        this.permission = "";
        this.action     = "";
        this.extra      = null;
    }

    private PermissionUsageRecord(Parcel in) {
        this.timestamp  = in.readLong();
        this.permission = in.readString();
        this.action     = in.readString();
        this.extra      = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(timestamp);
        out.writeString(permission);
        out.writeString(action);
        out.writeString(extra);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<PermissionUsageRecord> CREATOR =
            new Parcelable.Creator<PermissionUsageRecord>() {
        @Override public PermissionUsageRecord createFromParcel(Parcel in) { return new PermissionUsageRecord(in); }
        @Override public PermissionUsageRecord[] newArray(int n) { return new PermissionUsageRecord[n]; }
    };
}
