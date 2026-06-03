/*
 * Inference response. Returned by ICircleInference.generate() and
 * delivered via IInferenceCallback.onComplete().
 *
 * Field access by callers (InferenceHttpServer/Butler/CircleInferenceClient):
 *   resp.text                — full generated text
 *   resp.latencyMs           — wall time spent generating
 *   resp.promptTokens        — input tokens counted by the model
 *   resp.completionTokens    — tokens produced (matches the Ollama-
 *                              compatible eval_count field)
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class InferenceResponse implements Parcelable {

    public String text;
    public long   latencyMs;
    public int    promptTokens;
    public int    completionTokens;

    public InferenceResponse() {
        this.text             = "";
        this.latencyMs        = 0;
        this.promptTokens     = 0;
        this.completionTokens = 0;
    }

    private InferenceResponse(Parcel in) {
        this.text             = in.readString();
        this.latencyMs        = in.readLong();
        this.promptTokens     = in.readInt();
        this.completionTokens = in.readInt();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(text);
        out.writeLong(latencyMs);
        out.writeInt(promptTokens);
        out.writeInt(completionTokens);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<InferenceResponse> CREATOR =
            new Parcelable.Creator<InferenceResponse>() {
        @Override public InferenceResponse createFromParcel(Parcel in) { return new InferenceResponse(in); }
        @Override public InferenceResponse[] newArray(int n) { return new InferenceResponse[n]; }
    };
}
