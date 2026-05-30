/*
 * The Location Context drives the per-tap limit and trust posture.
 *
 * The wallet recognises a handful of canonical location types (home,
 * work, transit, etc.) and tunes its risk appetite accordingly:
 * higher limit at home, lower in transit. TYPE_UNKNOWN is the
 * default before the wallet learns a location.
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

    /** One of TYPE_*. */
    public int type;

    /** Per-tap limit at this location in cents. */
    public long perTapLimitCents;

    /**
     * Human-readable label the user has assigned, e.g. "Home", "Office
     * Sandton". Empty until the user names the location; UI then falls
     * back to typeName().
     */
    public String locationLabel;

    public LocationContext() {
        this.type             = TYPE_UNKNOWN;
        this.perTapLimitCents = 0;
        this.locationLabel    = "";
    }

    /** Localisable canonical name for type — "Home", "Work", … */
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
        this.type             = in.readInt();
        this.perTapLimitCents = in.readLong();
        this.locationLabel    = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(type);
        out.writeLong(perTapLimitCents);
        out.writeString(locationLabel);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<LocationContext> CREATOR =
            new Parcelable.Creator<LocationContext>() {
        @Override public LocationContext createFromParcel(Parcel in) { return new LocationContext(in); }
        @Override public LocationContext[] newArray(int n) { return new LocationContext[n]; }
    };
}
