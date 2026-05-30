/*
 * Per-tap-limit calibration state.
 *
 * SDPKT learns the user's typical spend pattern over the first 30 days
 * and uses it to set a per-tap limit. Until calibration finishes,
 * isLearning() is true; the UI shows "Learning your spend, N days
 * remaining" rather than a confident limit.
 */

package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class CalibrationState implements Parcelable {

    /** Days remaining until calibration converges. Zero means settled. */
    public int daysRemaining;

    /** Cents-per-tap once calibration settles. May be 0 while learning. */
    public long settledPerTapLimitCents;

    /** True if the user has manually overridden the auto-calibrated limit. */
    public boolean manualOverride;

    public CalibrationState() {
        this.daysRemaining           = 0;
        this.settledPerTapLimitCents = 0;
        this.manualOverride          = false;
    }

    /**
     * True while the wallet is still in the 30-day calibration window
     * and has not been manually overridden. Mirrors WalletActivity's
     * branching on cal.isLearning().
     */
    public boolean isLearning() {
        return daysRemaining > 0 && !manualOverride;
    }

    private CalibrationState(Parcel in) {
        this.daysRemaining           = in.readInt();
        this.settledPerTapLimitCents = in.readLong();
        this.manualOverride          = in.readInt() != 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(daysRemaining);
        out.writeLong(settledPerTapLimitCents);
        out.writeInt(manualOverride ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<CalibrationState> CREATOR =
            new Parcelable.Creator<CalibrationState>() {
        @Override public CalibrationState createFromParcel(Parcel in) { return new CalibrationState(in); }
        @Override public CalibrationState[] newArray(int n) { return new CalibrationState[n]; }
    };
}
