/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package za.co.circleos.inferencebridge;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import za.co.circleos.inference.DeviceCapabilities;
import za.co.circleos.inference.ICircleInference;
import za.co.circleos.inference.IInferenceCallback;
import za.co.circleos.inference.InferenceError;
import za.co.circleos.inference.InferenceRequest;
import za.co.circleos.inference.InferenceResponse;
import za.co.circleos.inference.ModelInfo;
import za.co.circleos.inference.ResourceMetrics;
import za.co.circleos.inference.Token;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Minimal HTTP/1.1 server exposing the CircleOS Inference Service on
 * 127.0.0.1:11434 with an Ollama-compatible REST+SSE API.
 *
 * Endpoints:
 *   GET  /api/status          — service health + loaded model
 *   GET  /api/tags            — list available models (Ollama compat)
 *   GET  /api/capabilities    — device hardware capabilities
 *   POST /api/generate        — sync or streaming inference
 *   POST /api/chat            — OpenAI-style chat (single user turn)
 *
 * Auth: Authorization: Bearer <token>  (token from ContentProvider)
 */
public class InferenceHttpServer {

    private static final String TAG  = "InferenceBridge.HTTP";
    static final int PORT = 11434;

    private final ICircleInference   mService;
    private final SessionTokenManager mTokenMgr;
    private final ExecutorService    mExecutor = Executors.newCachedThreadPool();

    private ServerSocket mServerSocket;
    private volatile boolean mRunning = false;

    public InferenceHttpServer(ICircleInference service, SessionTokenManager tokenMgr) {
        mService  = service;
        mTokenMgr = tokenMgr;
    }

    public void start() throws Exception {
        mServerSocket = new ServerSocket();
        mServerSocket.setReuseAddress(true);
        mServerSocket.bind(new java.net.InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), PORT));
        mRunning = true;
        Log.i(TAG, "Listening on 127.0.0.1:" + PORT);

        mExecutor.submit(() -> {
            while (mRunning) {
                try {
                    Socket client = mServerSocket.accept();
                    mExecutor.submit(() -> handleClient(client));
                } catch (Exception e) {
                    if (mRunning) Log.e(TAG, "Accept error", e);
                }
            }
        });
    }

    public void stop() {
        mRunning = false;
        try { if (mServerSocket != null) mServerSocket.close(); } catch (Exception ignored) {}
        mExecutor.shutdownNow();
    }

    // ── Request handling ──────────────────────────────────────────────────────

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(30_000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            // Parse request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) { socket.close(); return; }
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) { socket.close(); return; }
            String method = parts[0];
            String path   = parts[1].split("\\?")[0]; // strip query string

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String val = line.substring(colon + 1).trim();
                    headers.put(key, val);
                    if (key.equals("content-length")) {
                        try { contentLength = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Read body
            JSONObject body = null;
            if (contentLength > 0) {
                char[] cbuf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = reader.read(cbuf, read, contentLength - read);
                    if (r < 0) break;
                    read += r;
                }
                try { body = new JSONObject(new String(cbuf, 0, read)); }
                catch (Exception e) { Log.w(TAG, "JSON parse error: " + e.getMessage()); }
            }

            // Auth check (skip for status endpoint)
            if (!path.equals("/api/status")) {
                String auth = headers.getOrDefault("authorization", "");
                String token = auth.startsWith("Bearer ") ? auth.substring(7).trim() : "";
                if (!mTokenMgr.validate(token)) {
                    writeJson(out, 401, "{\"error\":\"Unauthorized\"}");
                    socket.close(); return;
                }
            }

            // Route
            route(method, path, body, out, socket);

        } catch (Exception e) {
            Log.e(TAG, "Client handler error", e);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void route(String method, String path, JSONObject body,
                       OutputStream out, Socket socket) throws Exception {
        switch (method + " " + path) {
            case "GET /api/status":        handleStatus(out);            break;
            case "GET /api/tags":          handleTags(out);              break;
            case "GET /api/capabilities":  handleCapabilities(out);      break;
            case "POST /api/generate":     handleGenerate(body, out, socket); break;
            case "POST /api/chat":         handleChat(body, out, socket);     break;
            default:
                writeJson(out, 404, "{\"error\":\"Not found: " + path + "\"}");
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleStatus(OutputStream out) throws Exception {
        String modelId = null;
        int version = 0;
        try {
            modelId = mService.getLoadedModelId();
            version = mService.getServiceVersion();
        } catch (Exception e) { Log.w(TAG, "status error", e); }

        JSONObject j = new JSONObject();
        j.put("status", "ok");
        j.put("version", version);
        j.put("modelLoaded", modelId != null);
        j.put("loadedModelId", modelId != null ? modelId : JSONObject.NULL);
        j.put("port", PORT);
        writeJson(out, 200, j.toString());
    }

    private void handleTags(OutputStream out) throws Exception {
        JSONArray models = new JSONArray();
        try {
            List<ModelInfo> list = mService.listModels();
            if (list != null) {
                for (ModelInfo m : list) {
                    JSONObject o = new JSONObject();
                    o.put("name",             m.id);
                    o.put("model",            m.id);
                    o.put("displayName",      m.name);
                    o.put("size",             m.sizeBytes);
                    o.put("parameterCount",   m.parameterCount);
                    o.put("recommendedTier",  m.recommendedTier);
                    o.put("isBundled",        m.isBundled);
                    o.put("isDownloaded",     m.isDownloaded);
                    o.put("backend",          m.backend);
                    models.put(o);
                }
            }
        } catch (Exception e) { Log.w(TAG, "listModels error", e); }

        writeJson(out, 200, new JSONObject().put("models", models).toString());
    }

    private void handleCapabilities(OutputStream out) throws Exception {
        try {
            DeviceCapabilities caps = mService.getDeviceCapabilities();
            if (caps == null) { writeJson(out, 503, "{\"error\":\"Service unavailable\"}"); return; }
            JSONObject j = new JSONObject();
            j.put("totalRamMb",       caps.totalRamMb);
            j.put("availableRamMb",   caps.availableRamMb);
            j.put("cpuCores",         caps.cpuCores);
            j.put("gpuAvailable",     caps.gpuAvailable);
            j.put("gpuType",          caps.gpuType != null ? caps.gpuType : "");
            j.put("recommendedTier",  caps.recommendedTier);
            JSONArray features = new JSONArray();
            if (caps.cpuFeatures != null) for (String f : caps.cpuFeatures) features.put(f);
            j.put("cpuFeatures", features);
            JSONArray backends = new JSONArray();
            if (caps.availableBackends != null) for (String b : caps.availableBackends) backends.put(b);
            j.put("availableBackends", backends);
            writeJson(out, 200, j.toString());
        } catch (Exception e) {
            writeJson(out, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleGenerate(JSONObject body, OutputStream out, Socket socket) throws Exception {
        if (body == null) { writeJson(out, 400, "{\"error\":\"Missing body\"}"); return; }

        InferenceRequest req = parseRequest(body);
        boolean stream = body.optBoolean("stream", false);

        if (stream) {
            streamGenerate(req, body.optString("model", ""), out, socket);
        } else {
            try {
                long start = System.currentTimeMillis();
                InferenceResponse resp = mService.generate(req);
                JSONObject j = buildResponseJson(resp, body.optString("model", ""), start);
                writeJson(out, 200, j.toString());
            } catch (Exception e) {
                writeJson(out, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    private void handleChat(JSONObject body, OutputStream out, Socket socket) throws Exception {
        if (body == null) { writeJson(out, 400, "{\"error\":\"Missing body\"}"); return; }

        // Extract last user message from messages array (OpenAI chat format)
        String prompt = "";
        String systemPrompt = null;
        JSONArray messages = body.optJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.optJSONObject(i);
                if (msg == null) continue;
                String role    = msg.optString("role", "");
                String content = msg.optString("content", "");
                if ("system".equals(role))    systemPrompt = content;
                else if ("user".equals(role)) prompt = content;
            }
        }

        InferenceRequest req = new InferenceRequest();
        req.prompt       = prompt;
        req.systemPrompt = systemPrompt;
        req.maxTokens    = body.optInt("max_tokens", 512);
        req.temperature  = (float) body.optDouble("temperature", 0.7);
        req.topP         = (float) body.optDouble("top_p", 0.9);

        boolean stream = body.optBoolean("stream", false);
        if (stream) {
            streamGenerate(req, body.optString("model", ""), out, socket);
        } else {
            try {
                long start = System.currentTimeMillis();
                InferenceResponse resp = mService.generate(req);
                // OpenAI-style response
                JSONObject choice = new JSONObject();
                choice.put("index", 0);
                JSONObject message = new JSONObject();
                message.put("role", "assistant");
                message.put("content", resp != null ? resp.text : "");
                choice.put("message", message);
                choice.put("finish_reason", (resp != null && resp.truncated) ? "length" : "stop");
                JSONArray choices = new JSONArray();
                choices.put(choice);
                JSONObject j = new JSONObject();
                j.put("object", "chat.completion");
                j.put("choices", choices);
                j.put("model", body.optString("model", "circle-inference"));
                writeJson(out, 200, j.toString());
            } catch (Exception e) {
                writeJson(out, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    // ── SSE streaming ─────────────────────────────────────────────────────────

    private void streamGenerate(InferenceRequest req, String modelName,
                                 OutputStream out, Socket socket) {
        try {
            // Write SSE headers
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/event-stream\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Connection: keep-alive\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.flush();

            LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(512);

            mService.generateStream(req, new IInferenceCallback.Stub() {
                @Override public void onToken(Token token) {
                    try {
                        JSONObject j = new JSONObject();
                        j.put("model",    modelName);
                        j.put("response", token.text);
                        j.put("done",     token.isFinal);
                        queue.offer("data: " + j + "\n\n");
                    } catch (Exception e) { Log.w(TAG, "SSE token encode", e); }
                }

                @Override public void onComplete(InferenceResponse resp) {
                    try {
                        JSONObject j = new JSONObject();
                        j.put("model",                modelName);
                        j.put("response",             "");
                        j.put("done",                 true);
                        j.put("prompt_eval_count",    resp.promptTokens);
                        j.put("eval_count",           resp.completionTokens);
                        j.put("total_duration",       resp.latencyMs * 1_000_000L);
                        queue.offer("data: " + j + "\n\n");
                    } catch (Exception e) { Log.w(TAG, "SSE complete encode", e); }
                    queue.offer("[DONE]");
                }

                @Override public void onError(InferenceError error) {
                    queue.offer("[ERROR:" + error.message + "]");
                }

                @Override public void onModelLoaded(String id) {}
            });

            // Drain queue and write to socket
            socket.setSoTimeout(120_000);
            while (true) {
                String event = queue.poll(60, TimeUnit.SECONDS);
                if (event == null || event.equals("[DONE]") || event.startsWith("[ERROR")) break;
                out.write(event.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

        } catch (Exception e) {
            Log.e(TAG, "Stream error", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InferenceRequest parseRequest(JSONObject body) {
        InferenceRequest req = new InferenceRequest();
        req.prompt       = body.optString("prompt", body.optString("input", ""));
        req.systemPrompt = body.optString("system", null);
        if (req.systemPrompt != null && req.systemPrompt.isEmpty()) req.systemPrompt = null;
        req.maxTokens    = body.optInt("num_predict", body.optInt("max_tokens", 512));
        req.temperature  = (float) body.optDouble("temperature", 0.7);
        req.topP         = (float) body.optDouble("top_p", 0.9);
        return req;
    }

    private JSONObject buildResponseJson(InferenceResponse resp, String model, long startMs)
            throws Exception {
        JSONObject j = new JSONObject();
        j.put("model",             model);
        j.put("response",          resp != null ? resp.text : "");
        j.put("done",              true);
        j.put("prompt_eval_count", resp != null ? resp.promptTokens    : 0);
        j.put("eval_count",        resp != null ? resp.completionTokens : 0);
        j.put("total_duration",    (System.currentTimeMillis() - startMs) * 1_000_000L);
        return j;
    }

    private void writeJson(OutputStream out, int status, String body) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + status + " " + statusText(status) + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    private String statusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            case 503: return "Service Unavailable";
            default:  return "Unknown";
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
