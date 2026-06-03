/*
 * Binder surface for the on-device inference service.
 * Published under ServiceManager.getService("circle.inference").
 * Permission: com.circleos.permission.ACCESS_INFERENCE.
 */
package za.co.circleos.inference;

import za.co.circleos.inference.DeviceCapabilities;
import za.co.circleos.inference.IInferenceCallback;
import za.co.circleos.inference.InferenceRequest;
import za.co.circleos.inference.InferenceResponse;
import za.co.circleos.inference.ModelInfo;
import za.co.circleos.inference.ResourceMetrics;

interface ICircleInference {

    /** Protocol version. Bumped on any AIDL-shape change. */
    int getServiceVersion();

    /** Snapshot of host capabilities (RAM, cores, GPU, recommended tier). */
    DeviceCapabilities getDeviceCapabilities();

    /** All models on device (bundled + downloaded). */
    List<ModelInfo> listModels();

    /** Id of the currently loaded model, or null. */
    String getLoadedModelId();

    /**
     * Load a specific model. Pass null for the service to pick the
     * optimal one for the device's tier. {@code callback.onModelLoaded}
     * is invoked when the model is ready; {@code onError} on failure.
     */
    void loadModel(in String modelId, in IInferenceCallback callback);

    /** Live RAM / CPU usage snapshot. */
    ResourceMetrics getResourceMetrics();

    /**
     * Synchronous one-shot inference. Blocks until complete or fails.
     * Do NOT call on the main thread.
     */
    InferenceResponse generate(in InferenceRequest request);

    /**
     * Streaming inference. Tokens arrive on the callback's binder
     * thread. The service guarantees at most one in-flight stream per
     * calling UID — a second concurrent call cancels the first.
     */
    void generateStream(in InferenceRequest request, in IInferenceCallback callback);

    /** Cancel any in-flight generation for the calling UID. */
    void cancelGeneration();
}
