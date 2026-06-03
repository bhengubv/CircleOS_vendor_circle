/*
 * Per-tap-limit + protection-engine calibration state.
 *
 * SDPKT learns the user's typical spend pattern + stress signature
 * over the first 30 days before enforcing protection thresholds.
 *
 * Field surface read by Butler.WalletSkill (handleCalibration) +
 * SdpktTitanium.WalletActivity:
 *   c.stateName()             — "Learning" / "Calibrated" / "Override"
 *   c.isLearning()
 *   c.daysRemaining
 *   c.accelThresholdCenti     — accelerometer stress threshold, m/s² × 100
 *   c.restingHeartRateBpm     — calibrated resting HR
 *   c.falsePositiveCount      — user-reported false alarms during the period
 */
package za.co.circleos.sdpkt;

import android.os.Parcel;
import android.os.Parcelable;

public final class CalibrationState implements Parcelable {

    public static final int STATE_LEARNING   = 0;
    public static final int STATE_CALIBRATED = 1;
    public static final int STATE_OVERRIDE   = 2;

    public int     state;
    public int     daysRemaining;
    public long    settledPerTapLimitCents;
    public boolean manualOverride;
    public int     accelThresholdCenti;
    public int     restingHeartRateBpm;
    public int     falsePositiveCount;

    public CalibrationState() {
        this.state                   = STATE_LEARNING;
        this.daysRemaining           = 0;
        this.settledPerTapLimitCents = 0;
        this.manualOverride          = false;
        this.accelThresholdCenti     = 0;
        this.restingHeartRateBpm     = 0;
        this.falsePositiveCount      = 0;
    }

    public String stateName() {
        switch (state) {
            case STATE_CALIBRATED: return "Calibrated";
            case STATE_OVERRIDE:   return "Manual override";
            default:               return "Learning";
        }
    }

    public boolean isLearning() {
        return state == STATE_LEARNING && daysRemaining > 0 && !manualOverride;
    }

    private CalibrationState(Parcel in) {
        this.state                   = in.readInt();
        this.daysRemaining           = in.readInt();
        this.settledPerTapLimitCents = in.readLong();
        this.manualOverride          = in.readInt() != 0;
        this.accelThresholdCenti     = in.readInt();
        this.restingHeartRateBpm     = in.readInt();
        this.falsePositiveCount      = in.readInt();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(state);
        out.writeInt(daysRemaining);
        out.writeLong(settledPerTapLimitCents);
        out.writeInt(manualOverride ? 1 : 0);
        out.writeInt(accelThresholdCenti);
        out.writeInt(restingHeartRateBpm);
        out.writeInt(falsePositiveCount);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<CalibrationState> CREATOR =
            new Parcelable.Creator<CalibrationState>() {
        @Override public CalibrationState createFromParcel(Parcel in) { return new CalibrationState(in); }
        @Override public CalibrationState[] newArray(int n) { return new CalibrationState[n]; }
    };
}
