package com.fikret.jarvishud;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotter;
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WakeWordManager {
    private static final String TAG = "WakeWordManager";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final String MODEL_DIR = "kws/gigaspeech";
    private static final String[] MODEL_FILES = {
            "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "tokens.txt",
            "bpe.model",
            "keywords.txt"
    };

    public interface Listener {
        void onWakeWordDetected();
        void onWakeWordError(Exception error);
    }

    private final Context context;
    private final Listener listener;
    private final AtomicBoolean callbackPending = new AtomicBoolean();
    private KeywordSpotter kws;
    private OnlineStream stream;
    private AudioRecord audioRecord;
    private volatile boolean listening;
    private boolean destroyed;
    private boolean holdsMicrophoneSession;
    private Thread recordingThread;

    public WakeWordManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized boolean start() {
        if (destroyed || listening) return listening;

        if (!modelsExist()) {
            Log.e(TAG, "KWS_MODEL_MISSING: Required model files not found in assets");
            listener.onWakeWordError(new Exception("Model files missing in assets"));
            return false;
        }

        if (!VoiceRecognitionCoordinator.tryAcquireWakeWord()) {
            Log.w(TAG, "Microphone is currently used by another voice session");
            return false;
        }
        holdsMicrophoneSession = true;

        try {
            String modelDir = extractModelsIfNeeded();
            if (modelDir == null) {
                throw new Exception("Failed to extract model files");
            }

            initializeKeywordSpotter(modelDir);
            Log.i(TAG, "KWS_MANAGER_CREATED");

            if (!initAudioRecord()) {
                throw new Exception("Failed to initialize audio record");
            }

            callbackPending.set(false);
            audioRecord.startRecording();
            listening = true;
            Log.i(TAG, "WAKE_WORD_LISTENING");

            recordingThread = new Thread(this::processSamples, "KWS-Processing");
            recordingThread.start();

            return true;
        } catch (Exception error) {
            listening = false;
            cleanup();
            VoiceRecognitionCoordinator.release();
            holdsMicrophoneSession = false;
            Log.e(TAG, "Unable to start KWS", error);
            listener.onWakeWordError(error);
            return false;
        }
    }

    public synchronized void stop() {
        if (!listening || kws == null) return;
        try {
            listening = false;
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                } catch (RuntimeException e) {
                    Log.w(TAG, "AudioRecord stop failed", e);
                }
            }
            if (recordingThread != null) {
                recordingThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            callbackPending.set(false);
            if (holdsMicrophoneSession) {
                VoiceRecognitionCoordinator.release();
                holdsMicrophoneSession = false;
            }
            Log.i(TAG, "WAKE_WORD_STOPPED");
        }
    }

    public synchronized void destroy() {
        if (destroyed) return;
        stop();
        cleanup();
        destroyed = true;
    }

    public boolean isListening() {
        return listening;
    }

    private void cleanup() {
        if (stream != null) {
            try {
                stream.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "OnlineStream release failed", e);
            }
            stream = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "AudioRecord release failed", e);
            }
            audioRecord = null;
        }
        if (kws != null) {
            try {
                kws.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to release KWS resources", e);
            }
            kws = null;
        }
    }

    private boolean modelsExist() {
        try {
            for (String file : MODEL_FILES) {
                try (InputStream is = context.getAssets().open(MODEL_DIR + "/" + file)) {
                    // Just check if file opens successfully
                }
            }
            Log.i(TAG, "KWS_MODEL_LOADED");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String extractModelsIfNeeded() throws IOException {
        File cacheDir = new File(context.getCacheDir(), "kws_models");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Failed to create cache directory");
        }

        for (String file : MODEL_FILES) {
            File target = new File(cacheDir, file);
            if (!target.exists()) {
                try (InputStream in = context.getAssets().open(MODEL_DIR + "/" + file);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
        return cacheDir.getAbsolutePath();
    }

    private void initializeKeywordSpotter(String modelDir) throws Exception {
        String encoder = new File(modelDir, "encoder-epoch-12-avg-2-chunk-16-left-64.onnx").getAbsolutePath();
        String decoder = new File(modelDir, "decoder-epoch-12-avg-2-chunk-16-left-64.onnx").getAbsolutePath();
        String joiner = new File(modelDir, "joiner-epoch-12-avg-2-chunk-16-left-64.onnx").getAbsolutePath();
        String tokens = new File(modelDir, "tokens.txt").getAbsolutePath();
        String keywordsFile = new File(modelDir, "keywords.txt").getAbsolutePath();

        FeatureConfig featConfig = FeatureConfig.builder()
                .setSampleRate(SAMPLE_RATE)
                .setFeatureDim(80)
                .build();

        OnlineTransducerModelConfig transducer = OnlineTransducerModelConfig.builder()
                .setEncoder(encoder)
                .setDecoder(decoder)
                .setJoiner(joiner)
                .build();

        OnlineModelConfig modelConfig = OnlineModelConfig.builder()
                .setTransducer(transducer)
                .setTokens(tokens)
                .setNumThreads(1)
                .setDebug(false)
                .build();

        KeywordSpotterConfig config = KeywordSpotterConfig.builder()
                .setFeatureConfig(featConfig)
                .setOnlineModelConfig(modelConfig)
                .setKeywordsFile(keywordsFile)
                .setKeywordsThreshold(0.25f)
                .build();

        kws = new KeywordSpotter(config);
        stream = kws.createStream();
    }

    private boolean initAudioRecord() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING);
            if (bufferSize <= 0) {
                bufferSize = SAMPLE_RATE * 2;
            }
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNELS,
                    ENCODING,
                    bufferSize * 2
            );
            return audioRecord.getState() == AudioRecord.STATE_INITIALIZED;
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize AudioRecord", e);
            return false;
        }
    }

    private void processSamples() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING);
        if (bufferSize <= 0) bufferSize = SAMPLE_RATE * 2;

        short[] buffer = new short[bufferSize / 2];
        float[] samples = new float[buffer.length];

        try {
            while (listening && kws != null && stream != null) {
                int nRead = audioRecord.read(buffer, 0, buffer.length);
                if (nRead > 0) {
                    for (int i = 0; i < nRead; i++) {
                        samples[i] = buffer[i] / 32768.0f;
                    }
                    stream.acceptWaveform(samples, SAMPLE_RATE);
                    decodeResults();
                }
            }
        } catch (RuntimeException e) {
            if (listening) {
                Log.e(TAG, "Error in processSamples", e);
            }
        }
    }

    private void decodeResults() {
        if (kws == null || stream == null) return;
        try {
            while (kws.isReady(stream)) {
                kws.decode(stream);
                String keyword = kws.getResult(stream).getKeyword();
                if (keyword != null && !keyword.isEmpty()) {
                    Log.d(TAG, "WAKE_WORD_DETECTED: " + keyword);
                    if (callbackPending.compareAndSet(false, true)) {
                        listener.onWakeWordDetected();
                    }
                    kws.reset(stream);
                }
            }
        } catch (RuntimeException e) {
            if (listening) {
                Log.e(TAG, "Error in decodeResults", e);
            }
        }
    }
}
