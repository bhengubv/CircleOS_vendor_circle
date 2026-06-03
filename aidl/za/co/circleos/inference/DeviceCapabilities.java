/*
 * Device-side inference capability snapshot. Returned by
 * ICircleInference.getDeviceCapabilities(). InferenceHttpServer
 * exposes these as the /api/capabilities JSON.
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class DeviceCapabilities implements Parcelable {

    public int     totalRamMb;
    public int     availableRamMb;
    public int     cpuCores;
    public boolean gpuAvailable;
    /** GPU type identifier (e.g. "adreno", "mali", "powervr"); null/"" if none. */
    public String  gpuType;
    /** Recommended model tier 1–5 based on detected hardware. */
    public int     recommendedTier;
    /** CPU feature flags ("neon", "sve", "i8mm", ...). */
    public String[] cpuFeatures;
    /** Inference backends compiled into this build ("llama.cpp-cpu", ...). */
    public String[] availableBackends;

    public DeviceCapabilities() {
        this.totalRamMb        = 0;
        this.availableRamMb    = 0;
        this.cpuCores          = 1;
        this.gpuAvailable      = false;
        this.gpuType           = "";
        this.recommendedTier   = 1;
        this.cpuFeatures       = new String[0];
        this.availableBackends = new String[0];
    }

    private DeviceCapabilities(Parcel in) {
        this.totalRamMb        = in.readInt();
        this.availableRamMb    = in.readInt();
        this.cpuCores          = in.readInt();
        this.gpuAvailable      = in.readInt() != 0;
        this.gpuType           = in.readString();
        this.recommendedTier   = in.readInt();
        this.cpuFeatures       = in.createStringArray();
        this.availableBackends = in.createStringArray();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(totalRamMb);
        out.writeInt(availableRamMb);
        out.writeInt(cpuCores);
        out.writeInt(gpuAvailable ? 1 : 0);
        out.writeString(gpuType);
        out.writeInt(recommendedTier);
        out.writeStringArray(cpuFeatures);
        out.writeStringArray(availableBackends);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<DeviceCapabilities> CREATOR =
            new Parcelable.Creator<DeviceCapabilities>() {
        @Override public DeviceCapabilities createFromParcel(Parcel in) { return new DeviceCapabilities(in); }
        @Override public DeviceCapabilities[] newArray(int n) { return new DeviceCapabilities[n]; }
    };
}
