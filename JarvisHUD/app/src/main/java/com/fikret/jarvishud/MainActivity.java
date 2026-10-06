package com.fikret.jarvishud;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String ACTION_FLOATING_VOICE = "com.fikret.jarvishud.FLOATING_VOICE";
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_AUDIO_PERMISSION = 11;
    private static final String UTTERANCE_ID = "jarvis-response";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private JarvisCoreView core;
    private TextView statusText;
    private TextView commandText;
    private Switch voiceActivationSwitch;
    private Switch heyJarvisSwitch;
    private Switch jarvisKeywordSwitch;
    private Switch selamJarvisSwitch;
    private Switch jarvisMentionSwitch;
    private Switch tripleClapSwitch;
    private SeekBar wakeSensitivitySeekBar;
    private SeekBar clapSensitivitySeekBar;
    private boolean ttsReady;
    private boolean ttsInitializationComplete;
    private boolean recognitionActive;
    private boolean pendingFloatingVoiceStart;
    private boolean pendingWakeActivation;
    private boolean pendingOverlayStart;
    private boolean updatingVoiceActivationSwitch;
    private boolean generalPermissionRequestInFlight;
    private boolean voiceReceiverRegistered;
    private String pendingSpeech;
    private final BroadcastReceiver voiceStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (VoiceRecognitionCoordinator.ACTION_VOICE_STATUS.equals(intent.getAction())) {
                String modeName = intent.getStringExtra("mode");
                String message = intent.getStringExtra("message");
                try {
                    setAssistantState(JarvisCoreView.Mode.valueOf(modeName), message);
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    Log.w(TAG, "Ignoring invalid voice status update");
                }
            } else if (VoiceRecognitionCoordinator.ACTION_VOICE_COMMAND.equals(intent.getAction())) {
                showRecognitionText(intent.getStringExtra("command"));
            }
        }
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener voicePreferenceListener =
            (preferences, key) -> {
                if (VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED.equals(key)
                        && voiceActivationSwitch != null) {
                    setVoiceActivationSwitch(preferences.getBoolean(key, false));
                }
            };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        core = findViewById(R.id.corePreview);
        core.setAmplitude(0.18f);
        statusText = findViewById(R.id.txtAssistantStatus);
        commandText = findViewById(R.id.txtAssistantCommand);
        voiceActivationSwitch = findViewById(R.id.switchVoiceActivation);
        voiceActivationSwitch.setChecked(getVoicePreferences().getBoolean(
                VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, false));
        voiceActivationSwitch.setOnCheckedChangeListener(this::onVoiceActivationChanged);
        configureTriggerControls();
        getVoicePreferences().registerOnSharedPreferenceChangeListener(voicePreferenceListener);
        core.setOnClickListener(null); core.setClickable(false);
        findViewById(R.id.btnVoice).setOnClickListener(v -> toggleListening());
        // JPLUS_HIDE_LEGACY
        findViewById(R.id.btnVoice).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnStartOverlay).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnStopOverlay).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnRefreshWidget).setVisibility(android.view.View.GONE);

        initializeSpeechRecognizer();
        initializeTextToSpeech();
        requestRuntimePermissions();
        handleIntent(getIntent());

        Button start=findViewById(R.id.btnStartOverlay), stop=findViewById(R.id.btnStopOverlay), refresh=findViewById(R.id.btnRefreshWidget);
        start.setOnClickListener(v -> requestOverlayStart());
        stop.setOnClickListener(v -> {
            setVoiceActivationPreference(false);
            setVoiceActivationSwitch(false);
            stopService(new Intent(this,FloatingHudService.class));
        });
        refresh.setOnClickListener(v -> { NewsFetcher.fetch(this, x -> JarvisWidgetProvider.updateAll(this)); JarvisWidgetProvider.updateAll(this); });
    }

    private void configureTriggerControls() {
        android.content.SharedPreferences prefs = getVoicePreferences();

        heyJarvisSwitch = findViewById(R.id.switchHeyJarvis);
        jarvisKeywordSwitch = findViewById(R.id.switchJarvisKeyword);
        selamJarvisSwitch = findViewById(R.id.switchSelamJarvis);
        jarvisMentionSwitch = findViewById(R.id.switchJarvisMention);
        tripleClapSwitch = findViewById(R.id.switchTripleClap);
        wakeSensitivitySeekBar = findViewById(R.id.seekWakeSensitivity);
        clapSensitivitySeekBar = findViewById(R.id.seekClapSensitivity);

        bindTriggerSwitch(heyJarvisSwitch, VoiceRecognitionCoordinator.PREF_HEY_JARVIS_ENABLED, true);
        bindTriggerSwitch(jarvisKeywordSwitch, VoiceRecognitionCoordinator.PREF_JARVIS_KEYWORD_ENABLED, true);
        bindTriggerSwitch(selamJarvisSwitch, VoiceRecognitionCoordinator.PREF_SELAM_JARVIS_ENABLED, true);
        bindTriggerSwitch(jarvisMentionSwitch, VoiceRecognitionCoordinator.PREF_JARVIS_MENTION_ENABLED, true);
        bindTriggerSwitch(tripleClapSwitch, VoiceRecognitionCoordinator.PREF_TRIPLE_CLAP_ENABLED, true);

        wakeSensitivitySeekBar.setProgress(Math.round(100f * prefs.getFloat(
                VoiceRecognitionCoordinator.PREF_WAKE_SENSITIVITY, 0.50f)));
        wakeSensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.edit().putFloat(
                        VoiceRecognitionCoordinator.PREF_WAKE_SENSITIVITY,
                        Math.max(0f, Math.min(1f, progress / 100f))).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        clapSensitivitySeekBar.setProgress(Math.round(100f * prefs.getFloat(
                VoiceRecognitionCoordinator.PREF_CLAP_SENSITIVITY, 0.55f)));
        clapSensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.edit().putFloat(
                        VoiceRecognitionCoordinator.PREF_CLAP_SENSITIVITY,
                        Math.max(0f, Math.min(1f, progress / 100f))).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void bindTriggerSwitch(Switch target, String key, boolean defaultValue) {
        android.content.SharedPreferences prefs = getVoicePreferences();
        target.setChecked(prefs.getBoolean(key, defaultValue));
        target.setOnCheckedChangeListener((button, enabled) ->
                prefs.edit().putBoolean(key, enabled).apply());
    }

    private void initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showError("Bu cihazda konuşma tanıma kullanılamıyor.");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                setAssistantState(JarvisCoreView.Mode.LISTENING, "Sizi dinliyorum...");
            }

            @Override public void onBeginningOfSpeech() {
                setAssistantState(JarvisCoreView.Mode.LISTENING, "Konuşmanızı dinliyorum...");
            }

            @Override public void onRmsChanged(float rmsdB) {
                float level = Math.max(0f, Math.min(0.35f, (rmsdB + 2f) / 14f * 0.35f));
                if (core != null && recognitionActive) core.setAmplitude(level);
            }

            @Override public void onPartialResults(Bundle partialResults) {
                showRecognitionText(bestResult(partialResults));
            }

            @Override public void onResults(Bundle results) {
                finishRecognition();
                String command = bestResult(results);
                if (command.isEmpty()) {
                    handleRecognitionError(SpeechRecognizer.ERROR_NO_MATCH);
                    return;
                }
                showRecognitionText(command);
                sendToJarvis(command);
            }

            @Override public void onEndOfSpeech() {
                setAssistantState(JarvisCoreView.Mode.PROCESSING, "Komutunuz işleniyor...");
            }

            @Override public void onError(int error) {
                finishRecognition();
                Log.e(TAG, "SpeechRecognizer failed; errorCode=" + error);
                handleRecognitionError(error);
            }

            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            ttsInitializationComplete = true;
            if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
                failPendingSpeech("Türkçe sesli yanıt başlatılamadı.");
                return;
            }
            int languageStatus = textToSpeech.setLanguage(new Locale("tr", "TR"));
            ttsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                    && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!ttsReady) {
                failPendingSpeech("Türkçe ses verisi kullanılamıyor.");
                return;
            }

            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    runOnUiThread(() -> setAssistantState(JarvisCoreView.Mode.SPEAKING, "JARVIS yanıtlıyor..."));
                }

                @Override public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        if (core != null && core.getMode() == JarvisCoreView.Mode.SPEAKING) {
                            setAssistantState(JarvisCoreView.Mode.IDLE, "Hazır");
                        }
                    });
                }

                @Override public void onError(String utteranceId) {
                    runOnUiThread(() -> {
                        if (core != null && core.getMode() == JarvisCoreView.Mode.SPEAKING) {
                            showError("Yanıt seslendirilemedi.");
                        }
                    });
                }
            });

            if (pendingSpeech != null) {
                String text = pendingSpeech;
                pendingSpeech = null;
                speak(text);
            }
        });
    }

    private void toggleListening() {
        if (getVoicePreferences().getBoolean(VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, false)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                pendingFloatingVoiceStart = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
                return;
            }
            startFloatingCommandRecognition();
            return;
        }
        if (core != null && core.getMode() == JarvisCoreView.Mode.LISTENING) {
            stopListening();
            return;
        }
        if (core != null && core.getMode() == JarvisCoreView.Mode.SPEAKING && textToSpeech != null) {
            textToSpeech.stop();
        }
        startListening();
    }

    private void startListening() {
        if (recognitionActive) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        if (speechRecognizer == null) {
            initializeSpeechRecognizer();
            if (speechRecognizer == null) return;
        }
        if (!VoiceRecognitionCoordinator.tryAcquire()) {
            showError("Başka bir JARVIS oturumu şu anda dinliyor.");
            return;
        }

        if (textToSpeech != null) textToSpeech.stop();
        recognitionActive = true;
        showRecognitionText("Dinleme başlatılıyor...");
        setAssistantState(JarvisCoreView.Mode.LISTENING, "Dinlemeye hazırlanıyor...");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            speechRecognizer.startListening(intent);
        } catch (RuntimeException exception) {
            finishRecognition();
            showError("Dinleme başlatılamadı.");
        }
    }

    private void onVoiceActivationChanged(CompoundButton button, boolean enabled) {
        if (updatingVoiceActivationSwitch) return;
        if (enabled) {
            if (recognitionActive || (core != null
                    && (core.getMode() == JarvisCoreView.Mode.SPEAKING
                    || core.getMode() == JarvisCoreView.Mode.PROCESSING))) {
                setVoiceActivationSwitch(false);
                showError("Sesli aktivasyonu açmadan önce mevcut sesli işlemin bitmesini bekleyin.");
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                pendingWakeActivation = true;
                setVoiceActivationSwitch(false);
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
                return;
            }
            enableVoiceActivation();
        } else {
            pendingWakeActivation = false;
            setVoiceActivationPreference(false);
            Intent serviceIntent = new Intent(this, FloatingHudService.class);
            serviceIntent.setAction(VoiceRecognitionCoordinator.ACTION_WAKE_WORD_STOP);
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startService(serviceIntent);
            } else {
                stopService(new Intent(this, FloatingHudService.class));
            }
        }
    }

    private void enableVoiceActivation() {
        pendingWakeActivation = false;
        setVoiceActivationPreference(true);
        Intent serviceIntent = new Intent(this, FloatingHudService.class);
        serviceIntent.setAction(VoiceRecognitionCoordinator.ACTION_WAKE_WORD_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private void requestOverlayStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingOverlayStart = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        Intent serviceIntent = new Intent(this, FloatingHudService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private void startFloatingCommandRecognition() {
        Intent serviceIntent = new Intent(this, FloatingHudService.class);
        serviceIntent.setAction(VoiceRecognitionCoordinator.ACTION_COMMAND_LISTENING);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private android.content.SharedPreferences getVoicePreferences() {
        return getSharedPreferences(VoiceRecognitionCoordinator.PREFS_NAME, MODE_PRIVATE);
    }

    private void setVoiceActivationPreference(boolean enabled) {
        getVoicePreferences().edit()
                .putBoolean(VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, enabled)
                .apply();
    }

    private void setVoiceActivationSwitch(boolean enabled) {
        updatingVoiceActivationSwitch = true;
        voiceActivationSwitch.setChecked(enabled);
        updatingVoiceActivationSwitch = false;
    }

    private void finishRecognition() {
        if (!recognitionActive) return;
        recognitionActive = false;
        VoiceRecognitionCoordinator.release();
    }

    private void stopListening() {
        if (speechRecognizer == null) return;
        try {
            speechRecognizer.stopListening();
            setAssistantState(JarvisCoreView.Mode.PROCESSING, "Komutunuz işleniyor...");
        } catch (RuntimeException exception) {
            handleRecognitionError(SpeechRecognizer.ERROR_CLIENT);
        }
    }

    private void sendToJarvis(String command) {
        setAssistantState(JarvisCoreView.Mode.PROCESSING, "Yanıt hazırlanıyor...");
        String normalizedCommand = command.toLowerCase(new Locale("tr", "TR"));
        boolean greeting = normalizedCommand.contains("merhaba")
                || normalizedCommand.contains("selam")
                || normalizedCommand.contains("hey jarvis");
        String response = greeting
                ? "Merhaba. Sizi dinliyorum."
                : "Komutunuzu aldım: " + command;
        speak(response);
    }

    private void speak(String text) {
        if (textToSpeech == null || !ttsReady) {
            if (ttsInitializationComplete) {
                showError("Türkçe sesli yanıt kullanılamıyor.");
            } else {
                pendingSpeech = text;
            }
            return;
        }
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
        if (result == TextToSpeech.ERROR) showError("Yanıt seslendirilemedi.");
    }

    private void failPendingSpeech(String message) {
        pendingSpeech = null;
        showError(message);
    }

    private String bestResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return results != null && !results.isEmpty() && results.get(0) != null
                ? results.get(0).trim()
                : "";
    }

    private void showRecognitionText(String text) {
        if (commandText != null && !text.isEmpty()) commandText.setText(text);
    }

    private void setAssistantState(JarvisCoreView.Mode mode, String message) {
        if (core != null) core.setMode(mode);
        if (statusText != null) statusText.setText(mode.name() + " • " + message);
    }

    private void handleRecognitionError(int error) {
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "Konuşma anlaşılamadı.";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "Konuşma algılanmadı.";
                break;
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Mikrofon sesine erişilemedi.";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Konuşma tanıma ağına ulaşılamadı.";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Mikrofon izni gerekli.";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "Konuşma tanıma meşgul. Tekrar deneyin.";
                break;
            default:
                message = "Konuşma tanıma başarısız oldu.";
                break;
        }
        showError(message);
    }

    private void showError(String message) {
        setAssistantState(JarvisCoreView.Mode.ERROR, message);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> setAssistantState(JarvisCoreView.Mode.IDLE, "Hazır"), 2200);
    }

    private void requestRuntimePermissions(){
        if(Build.VERSION.SDK_INT<23)return;
        List<String> req=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.READ_CALENDAR);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!req.isEmpty()) {
            generalPermissionRequestInFlight = true;
            requestPermissions(req.toArray(new String[0]),REQUEST_PERMISSIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingWakeActivation) {
                    enableVoiceActivation();
                    setVoiceActivationSwitch(true);
                } else if (pendingOverlayStart) {
                    pendingOverlayStart = false;
                    requestOverlayStart();
                } else if (pendingFloatingVoiceStart) {
                    pendingFloatingVoiceStart = false;
                    startFloatingVoiceRecognition();
                } else {
                    startListening();
                }
            } else {
                boolean wasWakeWordEnabled = getVoicePreferences().getBoolean(
                        VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, false);
                pendingWakeActivation = false;
                pendingOverlayStart = false;
                pendingFloatingVoiceStart = false;
                setVoiceActivationPreference(false);
                setVoiceActivationSwitch(false);
                if (wasWakeWordEnabled) stopService(new Intent(this, FloatingHudService.class));
                showError("Mikrofon izni verilmedi.");
            }
        } else if (requestCode == REQUEST_PERMISSIONS) {
            generalPermissionRequestInFlight = false;
            if (pendingFloatingVoiceStart) requestFloatingVoicePermissionOrStart();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !ACTION_FLOATING_VOICE.equals(intent.getAction())) return;
        pendingFloatingVoiceStart = true;
        if (generalPermissionRequestInFlight) return;
        requestFloatingVoicePermissionOrStart();
    }

    private void requestFloatingVoicePermissionOrStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText.setText(JarvisCoreView.Mode.IDLE.name() + " • Mikrofon izni gerekli.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        pendingFloatingVoiceStart = false;
        startFloatingVoiceRecognition();
    }

    private void startFloatingVoiceRecognition() {
        Intent serviceIntent = new Intent(this, FloatingHudService.class);
        serviceIntent.setAction(VoiceRecognitionCoordinator.ACTION_COMMAND_LISTENING);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        getVoicePreferences().unregisterOnSharedPreferenceChangeListener(voicePreferenceListener);
        finishRecognition();
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (RuntimeException ignored) { }
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (voiceActivationSwitch != null) {
            setVoiceActivationSwitch(getVoicePreferences().getBoolean(
                    VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, false));
        }
    }

    @Override protected void onStart() {
            super.onStart();
            IntentFilter filter = new IntentFilter();
            filter.addAction(VoiceRecognitionCoordinator.ACTION_VOICE_STATUS);
            filter.addAction(VoiceRecognitionCoordinator.ACTION_VOICE_COMMAND);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(voiceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(voiceStateReceiver, filter);
            }
            voiceReceiverRegistered = true;
        }

    @Override protected void onStop() {
        if (voiceReceiverRegistered) {
            unregisterReceiver(voiceStateReceiver);
            voiceReceiverRegistered = false;
        }
        super.onStop();
    }
}
