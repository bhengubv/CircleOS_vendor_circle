/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * One observed permission access by a package. Emitted by the
 * Permission Service whenever an app actually exercises a granted
 * permission, so the user can review what is happening rather than
 * just what was *allowed* to happen. Surfaced in the AppPrivacyDetail
 * UI under "what has this app done?".
 *
 * Fields are public for direct read access — records are immutable
 * after construction, so there is no setter discipline to enforce.
 */

package android.circleos;

import android.os.Parcel;
import android.os.Parcelable;

public final class PermissionUsageRecord implements Parcelable {

    /** Unix-millis timestamp of the access. */
    public long timestampMs;

    /** The permission name accessed, e.g. "android.permission.CAMERA". */
    public String permission;

    /**
     * Outcome of the access:
     *   "granted"   — call succeeded and data was returned
     *   "fake"      — call succeeded but the Fake Response Provider
     *                 returned synthetic data
     *   "denied"    — call returned SecurityException
     */
    public String outcome;

    /** Brief context — e.g. activity class or service the call came from. */
    public String context;

    public PermissionUsageRecord() {
        this.timestampMs = 0;
        this.permission  = "";
        this.outcome     = "";
        this.context     = "";
    }

    private PermissionUsageRecord(Parcel in) {
        this.timestampMs = in.readLong();
        this.permission  = in.readString();
        this.outcome     = in.readString();
        this.context     = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(timestampMs);
        out.writeString(permission);
        out.writeString(outcome);
        out.writeString(context);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<PermissionUsageRecord> CREATOR =
            new Parcelable.Creator<PermissionUsageRecord>() {
        @Override public PermissionUsageRecord createFromParcel(Parcel in) {
            return new PermissionUsageRecord(in);
        }
        @Override public PermissionUsageRecord[] newArray(int n) {
            return new PermissionUsageRecord[n];
        }
    };
}
