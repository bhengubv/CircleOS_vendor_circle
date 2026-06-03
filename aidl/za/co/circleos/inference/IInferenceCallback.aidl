/*
 * Callback for ICircleInference.generateStream / loadModel.
 * Runs on the binder thread pool of the caller — listeners must
 * return quickly; post to a Handler for UI work.
 */
package za.co.circleos.inference;

import za.co.circleos.inference.InferenceError;
import za.co.circleos.inference.InferenceResponse;
import za.co.circleos.inference.Token;

oneway interface IInferenceCallback {
    /** One generated token. */
    void onToken(in Token token);

    /**
     * Fired exactly once when generation finishes successfully.
     * The full text + timings come back in {@code response}.
     */
    void onComplete(in InferenceResponse response);

    /** Fired exactly once on terminal failure. */
    void onError(in InferenceError error);

    /**
     * Fired by loadModel() when the requested (or auto-selected)
     * model has finished loading and is ready for inference.
     */
    void onModelLoaded(String modelId);
}
