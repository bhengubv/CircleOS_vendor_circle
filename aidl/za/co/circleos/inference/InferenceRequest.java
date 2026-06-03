/*
 * Inference request parameter object. Constructed via the no-arg
 * constructor, fields assigned directly. Callers (Butler,
 * InferenceBridge, CircleInferenceClient) build it inline:
 *
 *   InferenceRequest req = new InferenceRequest();
 *   req.prompt       = userInput;
 *   req.systemPrompt = persona;
 *   req.maxTokens    = 512;
 *   req.temperature  = 0.7f;
 *   req.topP         = 0.9f;
 */
package za.co.circleos.inference;

import android.os.Parcel;
import android.os.Parcelable;

public final class InferenceRequest implements Parcelable {

    /** User prompt. */
    public String prompt;

    /** Optional system prompt (persona/instructions). May be null. */
    public String systemPrompt;

    /** Maximum number of tokens to generate. Default 512. */
    public int maxTokens;

    /** Sampling temperature 0.0 (deterministic) .. 1.0 (creative). */
    public float temperature;

    /** Top-p nucleus sampling threshold. */
    public float topP;

    public InferenceRequest() {
        this.prompt       = "";
        this.systemPrompt = null;
        this.maxTokens    = 512;
        this.temperature  = 0.7f;
        this.topP         = 0.9f;
    }

    private InferenceRequest(Parcel in) {
        this.prompt       = in.readString();
        this.systemPrompt = in.readString();
        this.maxTokens    = in.readInt();
        this.temperature  = in.readFloat();
        this.topP         = in.readFloat();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(prompt);
        out.writeString(systemPrompt);
        out.writeInt(maxTokens);
        out.writeFloat(temperature);
        out.writeFloat(topP);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<InferenceRequest> CREATOR =
            new Parcelable.Creator<InferenceRequest>() {
        @Override public InferenceRequest createFromParcel(Parcel in) { return new InferenceRequest(in); }
        @Override public InferenceRequest[] newArray(int n) { return new InferenceRequest[n]; }
    };
}
