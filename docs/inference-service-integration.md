# CircleOS Inference Service — Developer Integration Guide

## Overview

The CircleOS Inference Service (`circle.inference`) provides shared on-device LLM
inference to all apps via a Binder IPC interface. One model in memory serves all
apps — no per-app bundling, no cloud dependency, no per-query cost.

**Package:** `za.co.circleos.inference`
**Service name:** `circle.inference`
**Permission:** `com.circleos.permission.ACCESS_INFERENCE` (normal — auto-granted)

---

## Quick Start (using the client library)

Add to your `Android.bp`:

```
android_app {
    name: "MyApp",
    ...
    libs: ["circle_inference_client"],
}
```

In your `AndroidManifest.xml`:

```xml
<uses-permission android:name="com.circleos.permission.ACCESS_INFERENCE" />
```

In your Java code:

```java
import za.co.circleos.inference.client.CircleInferenceClient;
import za.co.circleos.inference.client.StreamingCallback;

CircleInferenceClient client = new CircleInferenceClient();

// Connect (do this once, e.g. in onStart)
if (!client.connect()) {
    // Service not available — handle gracefully
    return;
}

// Stream a response
client.generate("Explain photosynthesis in 2 sentences", new StreamingCallback() {
    @Override public void onToken(String text) {
        // Update UI token by token (runs on Binder thread — post to main if needed)
        runOnUiThread(() -> appendText(text));
    }

    @Override public void onComplete(String fullText, long latencyMs, int tokens) {
        Log.i(TAG, tokens + " tokens in " + latencyMs + "ms");
    }

    @Override public void onError(int code, String message, boolean recoverable) {
        Log.e(TAG, "Inference error " + code + ": " + message);
    }
});
```

---

## Direct AIDL Usage

For full control, bind to the AIDL interface directly:

```java
import android.os.IBinder;
import android.os.ServiceManager;
import za.co.circleos.inference.*;

IBinder binder = ServiceManager.getService("circle.inference");
ICircleInference service = ICircleInference.Stub.asInterface(binder);

// Check capabilities
DeviceCapabilities caps = service.getDeviceCapabilities();
Log.i(TAG, "Device tier: " + caps.recommendedTier);
Log.i(TAG, "Backends: " + caps.availableBackends);

// List available models
List<ModelInfo> models = service.listModels();

// Build a request
InferenceRequest req = new InferenceRequest();
req.prompt       = "Write a haiku about privacy.";
req.systemPrompt = "You are a creative poet.";
req.maxTokens    = 128;
req.temperature  = 0.9f;

// Synchronous (blocking — use a background thread)
InferenceResponse response = service.generate(req);
Log.i(TAG, response.text);

// Streaming
service.generateStream(req, new IInferenceCallback.Stub() {
    @Override public void onToken(Token token)           { /* append token.text */ }
    @Override public void onComplete(InferenceResponse r){ /* done */ }
    @Override public void onError(InferenceError e)      { /* handle error */ }
    @Override public void onModelLoaded(String modelId)  {}
});
```

---

## Device Tier Reference

The service auto-selects the best model for the device. Your app can query the
tier and adapt its prompts or expectations accordingly.

| Tier | RAM     | Backend      | Typical Model       |
|------|---------|--------------|---------------------|
| 1    | < 3 GB  | llama.cpp Q4 | Qwen 0.5B           |
| 2    | 3–6 GB  | llama.cpp Q4 | Qwen 1.5B / BitNet 2B |
| 3    | 6–8 GB  | BitNet.cpp   | BitNet 2B-4T        |
| 4    | 8–12 GB | BitNet.cpp   | BitNet 7B (future)  |
| 5    | 12 GB+  | BitNet.cpp   | BitNet 14B+ (future)|

---

## Error Codes

| Code | Constant                       | Meaning                        | Recoverable |
|------|--------------------------------|--------------------------------|-------------|
| 1    | `ERROR_NO_MODEL_LOADED`        | Call `loadModel()` first       | Yes         |
| 2    | `ERROR_MODEL_NOT_FOUND`        | Model ID not in manifest       | No          |
| 3    | `ERROR_MODEL_INTEGRITY_FAILED` | SHA-256 mismatch               | No          |
| 4    | `ERROR_INSUFFICIENT_MEMORY`    | Not enough RAM                 | Yes         |
| 5    | `ERROR_NATIVE_NOT_AVAILABLE`   | Native library missing         | No          |
| 6    | `ERROR_CANCELLED`              | Cancelled by caller            | Yes         |
| 7    | `ERROR_CONTEXT_EXCEEDED`       | Prompt too long                | Yes         |
| 8    | `ERROR_THERMAL_ABORT`          | Device too hot                 | Yes         |
| 9    | `ERROR_PERMISSION_DENIED`      | Missing ACCESS_INFERENCE       | No          |
| 100  | `ERROR_INTERNAL`               | Unexpected error               | Yes         |

---

## Best Practices

- **Background threads only** — all inference methods block; never call from main thread
- **Check tier before prompting** — Tier 1 devices generate slowly; keep prompts short
- **Respect latency** — show a loading indicator; streaming latency is typically 200ms–2s
  first-token for Tier 2 devices
- **System prompts** — set a concise system prompt to improve output quality and safety
- **Stop sequences** — use `req.stopSequences` to terminate at natural boundaries
- **Cancel promptly** — call `cancel()` when the user navigates away to free resources
  for other apps

---

## Geek Network Apps Integration

Butler, SDPKT, BidBaas, TagMe, TheJobCenter, and Bruh all use the same service.
No additional setup is needed — connect and generate.

| App           | Suggested system prompt seed                              |
|---------------|-----------------------------------------------------------|
| SDPKT         | "You are a financial assistant. Be precise and factual."  |
| BidBaas       | "You write professional tender bid documents."            |
| TagMe         | "Suggest concise tags for the given content."             |
| TheJobCenter  | "You help users write CVs and prepare for interviews."    |

---

## Personality Mode–Aware Inference

CircleOS Personality modes give users context-based device profiles (Daily, Work, Trader,
Developer, Secure, etc.).  Adapting your inference system prompts to the active mode
produces significantly more relevant responses.

### Reading the active mode

```java
import android.os.IBinder;
import android.os.ServiceManager;
import za.co.circleos.personality.ICirclePersonalityManager;
import za.co.circleos.personality.PersonalityMode;

IBinder binder = ServiceManager.getService("circle.personality");
ICirclePersonalityManager pm = ICirclePersonalityManager.Stub.asInterface(binder);

String modeId = pm.getActiveModeId();          // e.g. "work", "trader", "daily"
PersonalityMode mode = pm.getActiveMode();     // full object with name, description, tier
```

### Adapting system prompts per mode

```java
private static String systemPromptForMode(String modeId) {
    switch (modeId) {
        case "work":      return "You are a professional work assistant. Be concise and factual.";
        case "trader":    return "You are a South African financial and trading assistant. Prioritise accuracy.";
        case "developer": return "You are a senior software developer assistant. Prefer code examples.";
        case "secure":    return "You are a privacy-focused assistant. Never suggest cloud services.";
        default:          return "You are a helpful assistant.";
    }
}

// Usage
String modeId = pm.getActiveModeId();
InferenceRequest req = new InferenceRequest();
req.prompt       = userInput;
req.systemPrompt = systemPromptForMode(modeId);
req.maxTokens    = 512;
InferenceResponse resp = service.generate(req);
```

### Reacting to mode changes

Register a callback to update your system prompt whenever the user switches mode:

```java
pm.registerCallback(new IPersonalityCallback.Stub() {
    @Override
    public void onModeChanged(String fromModeId, String toModeId,
                              PersonalityMode toMode) {
        // Update your cached system prompt
        mCurrentSystemPrompt = systemPromptForMode(toModeId);
    }
    // other callback methods ...
});
```

Remember to call `pm.unregisterCallback(callback)` in `onDestroy()`.

### Mode IDs reference

| ID          | Name      | Suggested inference persona                              |
|-------------|-----------|----------------------------------------------------------|
| `daily`     | Daily     | Friendly general-purpose assistant                       |
| `work`      | Work      | Professional, concise, task-focused                      |
| `trader`    | Trader    | Financial analysis, JSE/SARS aware, precise              |
| `developer` | Developer | Code-first, technical depth, prefers code blocks         |
| `secure`    | Secure    | Privacy-first, no cloud suggestions, minimal data output |
| `family`    | Family    | Safe, inclusive, simple language                         |
| `fitness`   | Fitness   | Health, exercise, nutrition focused                      |

Custom user-created modes (`mode.isCustom == true`) should fall back to the `daily` persona
unless you inspect `mode.name` or `mode.description` for hints.

---

## Model Store

Users can download larger models via CircleSettings → AI → Model Store.
Downloaded models are placed in `/data/circle/models/` and picked up
automatically by the service on next load.

To trigger a download programmatically (requires MANAGE_PRIVACY permission):

```java
service.downloadModel("qwen-7b-q4", callback);
```
