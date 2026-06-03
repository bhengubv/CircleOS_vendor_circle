/*
 * Metadata for one on-device model. Returned by
 * ICircleInference.listModels(). InferenceHttpServer exposes these
 * fields as JSON in the Ollama-compatible /api/tags endpoint:
 *   m.id              → "name" + "model"
 *   m.name            → "displayName"
 *   m.sizeBytes       → "size"
 *   m.parameterCount  → "parameterCount"
 *   m.recommendedTier → "recommendedTier"
 *   m.isBundled       → "isBundled"
 *   m.isDownloaded    → "isDownloaded"
 *   m.backend         → "backend"
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class ModelInfo implements Parcelable {

    /** Stable model identifier. */
    public String id;

    /** Human-readable display name. */
    public String name;

    /** On-disk model size in bytes. */
    public long sizeBytes;

    /** Parameter count (e.g. 1_500_000_000 for a 1.5B model). */
    public long parameterCount;

    /**
     * Tier the model is recommended for (1–5; 1 = entry-level handset,
     * 5 = high-end). Used by loadModel(null) auto-selection.
     */
    public int recommendedTier;

    /** True if shipped in the OS image; cannot be uninstalled. */
    public boolean isBundled;

    /** True if currently present on disk (bundled OR downloaded). */
    public boolean isDownloaded;

    /**
     * Inference backend identifier — e.g. "llama.cpp-cpu",
     * "llama.cpp-gpu-opencl", "bitnet". Drives which native runtime
     * the service loads for this model.
     */
    public String backend;

    public ModelInfo() {
        this.id              = "";
        this.name            = "";
        this.sizeBytes       = 0L;
        this.parameterCount  = 0L;
        this.recommendedTier = 1;
        this.isBundled       = false;
        this.isDownloaded    = false;
        this.backend         = "";
    }

    private ModelInfo(Parcel in) {
        this.id              = in.readString();
        this.name            = in.readString();
        this.sizeBytes       = in.readLong();
        this.parameterCount  = in.readLong();
        this.recommendedTier = in.readInt();
        this.isBundled       = in.readInt() != 0;
        this.isDownloaded    = in.readInt() != 0;
        this.backend         = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(id);
        out.writeString(name);
        out.writeLong(sizeBytes);
        out.writeLong(parameterCount);
        out.writeInt(recommendedTier);
        out.writeInt(isBundled    ? 1 : 0);
        out.writeInt(isDownloaded ? 1 : 0);
        out.writeString(backend);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ModelInfo> CREATOR =
            new Parcelable.Creator<ModelInfo>() {
        @Override public ModelInfo createFromParcel(Parcel in) { return new ModelInfo(in); }
        @Override public ModelInfo[] newArray(int n) { return new ModelInfo[n]; }
    };
}
