/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Per-package privacy policy. Returned by ICirclePrivacyManager.getPolicy
 * and written back via setPolicy. The fields are public + mutable to
 * match the direct-field access pattern used by CircleSettings (cf.
 * AppPrivacyDetailActivity which binds each field to a Switch).
 *
 * Default-deny: a freshly-constructed AppPrivacyPolicy has every flag
 * set to false and an empty allowedSensors list. The Privacy Engine
 * applies that to first-launch packages until the user grants
 * something.
 */

package android.circleos;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public final class AppPrivacyPolicy implements Parcelable {

    /** May the package open outbound network sockets? */
    public boolean networkAllowed;

    /** May the package read the contacts content provider? */
    public boolean contactsAllowed;

    /** May the package read external storage? */
    public boolean storageAllowed;

    /**
     * If true, network traffic from this package is routed through the
     * Traffic Lobby (DPI + DGA detection) instead of going direct. Used
     * for apps the user trusts but wants observed.
     */
    public boolean lobbyMode;

    /**
     * Free-form sensor identifiers the user has granted. Strings rather
     * than ints so the OS can ship new sensor classes without an AIDL
     * bump. Canonical names: "camera", "microphone", "fine_location",
     * "coarse_location", "body_sensors", "activity_recognition", etc.
     */
    public List<String> allowedSensors;

    public AppPrivacyPolicy() {
        this.networkAllowed  = false;
        this.contactsAllowed = false;
        this.storageAllowed  = false;
        this.lobbyMode       = false;
        this.allowedSensors  = new ArrayList<>();
    }

    private AppPrivacyPolicy(Parcel in) {
        this.networkAllowed  = in.readInt() != 0;
        this.contactsAllowed = in.readInt() != 0;
        this.storageAllowed  = in.readInt() != 0;
        this.lobbyMode       = in.readInt() != 0;
        this.allowedSensors  = new ArrayList<>();
        in.readStringList(this.allowedSensors);
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(networkAllowed  ? 1 : 0);
        out.writeInt(contactsAllowed ? 1 : 0);
        out.writeInt(storageAllowed  ? 1 : 0);
        out.writeInt(lobbyMode       ? 1 : 0);
        out.writeStringList(allowedSensors);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<AppPrivacyPolicy> CREATOR =
            new Parcelable.Creator<AppPrivacyPolicy>() {
        @Override public AppPrivacyPolicy createFromParcel(Parcel in) {
            return new AppPrivacyPolicy(in);
        }
        @Override public AppPrivacyPolicy[] newArray(int n) {
            return new AppPrivacyPolicy[n];
        }
    };
}
