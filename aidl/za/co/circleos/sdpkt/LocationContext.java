/*
 * Location Context drives the per-tap limit and trust posture.
 *
 * Field surface read by Butler.WalletSkill (handleLocation) +
 * SdpktTitanium.WalletActivity:
 *   loc.type              — TYPE_*
 *   loc.locationLabel     — human-readable label, may be empty
 *   loc.typeName()        — fallback display name
 *   loc.perTapLimitCents
 *   loc.dailyLimitCents
 *   loc.confidencePercent — 0..100, model confidence in the labelling
 *   loc.speedMs           — float, instantaneous speed in m/s
 */
package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class LocationContext implements Parcelable {

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_HOME    = 1;
    public static final int TYPE_WORK    = 2;
    public static final int TYPE_TRANSIT = 3;
    public static final int TYPE_PUBLIC  = 4;
    public static final int TYPE_TRAVEL  = 5;
    // Posture-based labels used by the SdpktTitanium WalletActivity chip:
    // "known good" location (home + work + frequent venues collapse to
    // TYPE_KNOWN at the UI level), risky-area flag, and the in-motion
    // marker that disables high-trust limits while moving.
    public static final int TYPE_KNOWN   = 6;
    public static final int TYPE_RISKY   = 7;
    public static final int TYPE_MOVING  = 8;

    public int    type;
    public long   perTapLimitCents;
    public long   dailyLimitCents;
    public String locationLabel;
    public float  confidencePercent;
    public float  speedMs;

    public LocationContext() {
        this.type              = TYPE_UNKNOWN;
        this.perTapLimitCents  = 0;
        this.dailyLimitCents   = 0;
        this.locationLabel     = "";
        this.confidencePercent = 0f;
        this.speedMs           = 0f;
    }

    public String typeName() {
        switch (type) {
            case TYPE_HOME:    return "Home";
            case TYPE_WORK:    return "Work";
            case TYPE_TRANSIT: return "Transit";
            case TYPE_PUBLIC:  return "Public";
            case TYPE_TRAVEL:  return "Travel";
            default:           return "Unknown";
        }
    }

    private LocationContext(Parcel in) {
        this.type              = in.readInt();
        this.perTapLimitCents  = in.readLong();
        this.dailyLimitCents   = in.readLong();
        this.locationLabel     = in.readString();
        this.confidencePercent = in.readFloat();
        this.speedMs           = in.readFloat();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(type);
        out.writeLong(perTapLimitCents);
        out.writeLong(dailyLimitCents);
        out.writeString(locationLabel);
        out.writeFloat(confidencePercent);
        out.writeFloat(speedMs);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<LocationContext> CREATOR =
            new Parcelable.Creator<LocationContext>() {
        @Override public LocationContext createFromParcel(Parcel in) { return new LocationContext(in); }
        @Override public LocationContext[] newArray(int n) { return new LocationContext[n]; }
    };
}
