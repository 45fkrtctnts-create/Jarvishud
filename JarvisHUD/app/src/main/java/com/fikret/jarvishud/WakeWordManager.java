package com.fikret.jarvishud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.openwakeword.OpenWakeWord;

public final class WakeWordManager {
    private static final String TAG = "OpenWakeWordManager";

    public interface Listener {
        void onWakeWordDetected();
        void onWakeWordError(Exception error);
    }

    private final Context context;
    private final Listener listener;

    private OpenWakeWord detector;
    private volatile boolean listening;
    private volatile boolean destroyed;
    private boolean holdsMicSession;

    public WakeWordManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized boolean start() {
        if (destroyed) return false;
        if (listening) return true;

        if (!VoiceRecognitionCoordinator.tryAcquireWakeWord()) {
            Log.w(TAG, "Mikrofon başka bir ses oturumunda.");
            return false;
        }
        holdsMicSession = true;

        try {
            if (detector == null) {
                SharedPreferences prefs = context.getSharedPreferences(
                        VoiceRecognitionCoordinator.PREFS_NAME,
                        Context.MODE_PRIVATE
                );

                float sensitivity = prefs.getFloat("wake_sensitivity", 0.50f);
                sensitivity = Math.max(0f, Math.min(1f, sensitivity));

                float threshold = 0.70f - (0.35f * sensitivity);
                threshold = Math.max(0.30f, Math.min(0.70f, threshold));

                detector = new OpenWakeWord.Builder(context)
                        .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                        .setThreshold(threshold)
                        .setDebounceMs(2200L)
                        .build();

                Log.i(TAG, "OPENWAKEWORD_CREATED threshold=" + threshold);
            }

            listening = true;

            detector.start(score -> {
                if (!listening || destroyed) return;

                Log.i(TAG, "HEY_JARVIS_DETECTED score=" + score);

                stop();

                try {
                    listener.onWakeWordDetected();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Wake callback failed", e);
                    listener.onWakeWordError(e);
                }
            });

            Log.i(TAG, "OPENWAKEWORD_LISTENING");
            return true;

        } catch (Throwable t) {
            listening = false;
            releaseMicSession();

            Exception e = (t instanceof Exception)
                    ? (Exception) t
                    : new RuntimeException(t);

            Log.e(TAG, "openWakeWord başlatılamadı", e);
            listener.onWakeWordError(e);
            return false;
        }
    }

    public synchronized void stop() {
        if (!listening && !holdsMicSession) return;

        listening = false;

        try {
            if (detector != null) detector.stop();
        } catch (Throwable t) {
            Log.w(TAG, "openWakeWord durdurma hatası", t);
        } finally {
            releaseMicSession();
        }

        Log.i(TAG, "OPENWAKEWORD_STOPPED");
    }

    public synchronized void destroy() {
        if (destroyed) return;

        stop();

        try {
            if (detector != null) detector.release();
        } catch (Throwable t) {
            Log.w(TAG, "openWakeWord release hatası", t);
        }

        detector = null;
        destroyed = true;
        Log.i(TAG, "OPENWAKEWORD_RELEASED");
    }

    public boolean isListening() {
        return listening;
    }

    private void releaseMicSession() {
        if (holdsMicSession) {
            VoiceRecognitionCoordinator.release();
            holdsMicSession = false;
        }
    }
}
