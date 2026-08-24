package com.hampus.dslraicoach;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.ImagePart;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AiCoach {
    public interface StatusCallback { void onStatus(String text, boolean ready); }
    public interface AnalysisCallback { void onResult(String text); }
    private final GenerativeModelFutures model = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = command -> main.post(command);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean ready;

    public void prepare(StatusCallback callback) {
        callback.onStatus("Checking on-device AI…", false);
        ListenableFuture<Integer> future = model.checkStatus();
        future.addListener(() -> {
            try {
                int status = future.get();
                if (status == FeatureStatus.AVAILABLE) {
                    ready = true;
                    callback.onStatus("Gemini Nano ready — analysis stays on this phone", true);
                } else if (status == FeatureStatus.DOWNLOADABLE) {
                    callback.onStatus("Preparing Gemini Nano…", false);
                    model.download(new DownloadCallback() {
                        @Override public void onDownloadStarted(long bytesToDownload) { main.post(() -> callback.onStatus("Downloading on-device AI…", false)); }
                        @Override public void onDownloadProgress(long bytes) { main.post(() -> callback.onStatus("Downloading AI: " + (bytes / 1_000_000) + " MB", false)); }
                        @Override public void onDownloadCompleted() { ready = true; main.post(() -> callback.onStatus("Gemini Nano ready — analysis stays on this phone", true)); }
                        @Override public void onDownloadFailed(GenAiException e) { main.post(() -> callback.onStatus("AI download failed: " + e.getMessage(), false)); }
                    });
                } else if (status == FeatureStatus.DOWNLOADING) callback.onStatus("Gemini Nano is downloading in AICore", false);
                else callback.onStatus("Gemini Nano unavailable. Technical coaching still works.", false);
            } catch (Exception e) { callback.onStatus("AI status error: " + e.getMessage(), false); }
        }, mainExecutor);
    }

    public void analyze(Bitmap bitmap, String cameraData, String metrics, String preferenceMemory, AnalysisCallback callback) {
        if (!ready) { callback.onResult("On-device AI is not ready yet.\n\n" + preferenceMemory); return; }
        Bitmap input = downscale(bitmap, 1280);
        String prompt = "You are a demanding but practical professional photography coach. Analyze this DSLR photograph. " +
                "Use measured data when relevant. Return concise plain text with headings exactly: SCORE, WHAT WORKS, FIX NEXT SHOT, CAMERA SETTINGS, COMPOSITION. " +
                "SCORE must be /10. Under FIX NEXT SHOT give the three highest-value concrete actions. Do not invent exact measurements not supplied.\n\n" +
                cameraData + "\n" + metrics + "\n" + preferenceMemory;
        worker.execute(() -> {
            try {
                GenerateContentRequest.Builder requestBuilder =
                        new GenerateContentRequest.Builder(new ImagePart(input), new TextPart(prompt));
                requestBuilder.setTemperature(0.25f);
                requestBuilder.setMaxOutputTokens(700);
                GenerateContentRequest request = requestBuilder.build();
                GenerateContentResponse response = model.generateContent(request).get();
                String text = response.getCandidates().isEmpty() ? "The local AI returned no critique." : response.getCandidates().get(0).getText();
                main.post(() -> callback.onResult(text));
            } catch (Exception e) { main.post(() -> callback.onResult("Local AI analysis failed: " + e.getMessage())); }
            finally { if (input != bitmap && !input.isRecycled()) input.recycle(); }
        });
    }

    private static Bitmap downscale(Bitmap source, int maxSide) {
        int w = source.getWidth(), h = source.getHeight(), longest = Math.max(w, h);
        if (longest <= maxSide) return source;
        float scale = maxSide / (float) longest;
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
    }

    public void close() { worker.shutdownNow(); }
}
