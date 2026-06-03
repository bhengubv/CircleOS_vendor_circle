/*
 * Live RAM / CPU usage from the inference service. Returned by
 * ICircleInference.getResourceMetrics(). Reported under the
 * Ollama-compatible /api/resource_metrics path.
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class ResourceMetrics implements Parcelable {

    /** Total resident RAM of the service process, MB. */
    public int processRssMb;

    /** Currently-loaded model's footprint, MB. */
    public int modelRamMb;

    /** Service CPU% over the last second (0–100 × cores). */
    public int cpuPercent;

    /** Active stream count (per-UID inflight gen). */
    public int activeStreams;

    /** Tokens-per-second moving average over the last minute, or 0. */
    public float tokensPerSec;

    /** True if the GPU backend is active for the loaded model. */
    public boolean gpuActive;

    public ResourceMetrics() {
        this.processRssMb  = 0;
        this.modelRamMb    = 0;
        this.cpuPercent    = 0;
        this.activeStreams = 0;
        this.tokensPerSec  = 0f;
        this.gpuActive     = false;
    }

    private ResourceMetrics(Parcel in) {
        this.processRssMb  = in.readInt();
        this.modelRamMb    = in.readInt();
        this.cpuPercent    = in.readInt();
        this.activeStreams = in.readInt();
        this.tokensPerSec  = in.readFloat();
        this.gpuActive     = in.readInt() != 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(processRssMb);
        out.writeInt(modelRamMb);
        out.writeInt(cpuPercent);
        out.writeInt(activeStreams);
        out.writeFloat(tokensPerSec);
        out.writeInt(gpuActive ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ResourceMetrics> CREATOR =
            new Parcelable.Creator<ResourceMetrics>() {
        @Override public ResourceMetrics createFromParcel(Parcel in) { return new ResourceMetrics(in); }
        @Override public ResourceMetrics[] newArray(int n) { return new ResourceMetrics[n]; }
    };
}
