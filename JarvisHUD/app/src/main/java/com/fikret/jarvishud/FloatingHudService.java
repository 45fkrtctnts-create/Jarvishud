package com.fikret.jarvishud;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FloatingHudService extends Service {
    private static final String CH = "jarvis_hud";
    private static final String TAG = "FloatingHudService";
    private static final String ACTION_START_VOICE = "com.fikret.jarvishud.START_FLOATING_VOICE";
    private static final String ACTION_REQUEST_VOICE_PERMISSION = "com.fikret.jarvishud.FLOATING_VOICE";
    private static final String UTTERANCE_ID = "floating-jarvis-response";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View root;
    private WindowManager.LayoutParams lp;
    private volatile boolean overlayActive;
    private JarvisCoreView core;
    private Runnable clockTick;
    private Runnable errorReset;
    private Runnable wakeWordRestart;
    private SpeechRecognizer speechRecognizer;
    private WakeWordManager wakeWordManager;
    private TextToSpeech textToSpeech;
    private TextView voiceStatus;
    private TextView voiceCommand;
    private WindowManager ambientHudWindowManager;
    private JarvisAmbientOverlayView ambientHudView;
    private WindowManager.LayoutParams ambientHudLayoutParams;
    private boolean recognitionActive;
    private String lastPartialCommand = "";
    private boolean ttsReady;
    private boolean ttsInitializationComplete;
    private String pendingSpeech;
    private boolean wakeWordEnabled;
    private long utteranceSequence;
    private String activeUtteranceId;
    private volatile boolean serviceActive = true;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission missing; microphone foreground service not started");
            serviceActive = false;
            stopSelf();
            return;
        }
        wakeWordEnabled = getVoicePreferences().getBoolean(
                VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, false);
        startForeground(11, notification());
        // J+ V2: legacy floating panel removed.
        // Ambient HUD appears only during a voice session.
        initializeTextToSpeech();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!serviceActive) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();
        if (VoiceRecognitionCoordinator.ACTION_WAKE_WORD_START.equals(action)) {
            setWakeWordEnabled(true);
            startWakeWord();
        } else if (VoiceRecognitionCoordinator.ACTION_WAKE_WORD_STOP.equals(action)) {
            setWakeWordEnabled(false);
            stopWakeWord();
            if (overlayActive) {
                startForeground(11, notification());
                setVoiceState(JarvisCoreView.Mode.IDLE, "Sesli aktivasyon kapalı");
            } else {
                stopSelf();
            }
        } else if (VoiceRecognitionCoordinator.ACTION_COMMAND_LISTENING.equals(action)
                || ACTION_START_VOICE.equals(action)) {
            startCommandRecognition();
        } else if (action == null) {
            // Yüzen panel açılırken native wake-word motorunu otomatik başlatma.
            // Wake-word yalnızca ACTION_WAKE_WORD_START ile açıkça başlatılır.
            if (overlayActive) {
                setVoiceState(
                        JarvisCoreView.Mode.IDLE,
                        wakeWordEnabled
                                ? "Yüzen panel hazır • Sesli aktivasyon beklemede"
                                : "Yüzen panel aktif"
                );
            } else {
                stopSelf();
            }
        }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void showOverlay() {
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        root = LayoutInflater.from(this).inflate(R.layout.overlay_hud,null,false);
        core = root.findViewById(R.id.jarvisCore);
        if (core == null) {
            root = null;
            throw new IllegalStateException("overlay_hud must contain R.id.jarvisCore");
        }
        lp = new WindowManager.LayoutParams(dp(390), dp(240),
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = 24; lp.y = 120;
        wm.addView(root,lp);
        overlayActive = true;
        bindUi();
    }

    private void bindUi() {
        TextView time=root.findViewById(R.id.txtTime), date=root.findViewById(R.id.txtDate), cal=root.findViewById(R.id.txtCalendar), sched=root.findViewById(R.id.txtSchedule);
        clockTick = new Runnable(){ public void run(){
            if (!overlayActive || root == null) return;
            Calendar now=Calendar.getInstance();
            time.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now.getTime()));
            date.setText(new SimpleDateFormat("dd MMMM yyyy",new Locale("tr","TR")).format(now.getTime()));
            cal.setText(new SimpleDateFormat("MMMM yyyy",new Locale("tr","TR")).format(now.getTime())+"\nPzt  Sal  Çar  Per  Cum  Cmt  Paz\nBugün: "+now.get(Calendar.DAY_OF_MONTH));
            mainHandler.postDelayed(this,15000);
        }};
        mainHandler.post(clockTick);
        List<String> agenda=ScheduleReader.today(this); StringBuilder sb=new StringBuilder(); for(String s:agenda) sb.append("• ").append(s).append('\n'); sched.setText(sb.toString().trim());
        fillNews(NewsFetcher.cached(this));
        NewsFetcher.fetch(this,titles -> mainHandler.post(() -> {
            if (overlayActive && root != null) fillNews(titles);
        }));
        root.findViewById(R.id.btnClose).setOnClickListener(v -> stopSelf());
        voiceStatus = root.findViewById(R.id.txtVoiceStatus);
        voiceCommand = root.findViewById(R.id.txtVoiceCommand);
        View coreTapTarget = root.findViewById(R.id.coreTapTarget);
        core.setOnClickListener(v -> startVoiceRecognition());
        coreTapTarget.setOnClickListener(v -> startVoiceRecognition());
        View drag=root.findViewById(R.id.dragBar); final int[] start={0,0}; final float[] touch={0,0};
        drag.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){ start[0]=lp.x; start[1]=lp.y; touch[0]=e.getRawX(); touch[1]=e.getRawY(); return true; }
            if(e.getAction()==MotionEvent.ACTION_MOVE){ lp.x=start[0]+(int)(e.getRawX()-touch[0]); lp.y=start[1]+(int)(e.getRawY()-touch[1]); if(wm!=null&&root!=null)wm.updateViewLayout(root,lp); return true; }
            return false;
        });
    }

    private void fillNews(List<String> n){ TextView[] v={root.findViewById(R.id.txtNews1),root.findViewById(R.id.txtNews2),root.findViewById(R.id.txtNews3)}; for(int i=0;i<3;i++)v[i].setText(i<n.size()?n.get(i):""); }

    private void startVoiceRecognition() {
        startCommandRecognition();
    }

    private void startCommandRecognition() {
        if (!serviceActive) return;

        if (wakeWordRestart != null) {
            mainHandler.removeCallbacks(wakeWordRestart);
            wakeWordRestart = null;
        }

        if (wakeWordManager != null && wakeWordManager.isListening()) {
            stopWakeWord();
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            showVoiceError("Mikrofon izni gerekli.", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            return;
        }

        if (recognitionActive) return;

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showVoiceError("Bu cihazda konuşma tanıma kullanılamıyor.", SpeechRecognizer.ERROR_CLIENT);
            restartWakeWordAfterCommand();
            return;
        }

        if (speechRecognizer == null) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        setVoiceState(JarvisCoreView.Mode.LISTENING, "Sizi dinliyorum...");
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        setVoiceState(JarvisCoreView.Mode.LISTENING, "Konuşmanızı dinliyorum...");
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                        float amplitude = Math.max(
                                0f,
                                Math.min(1f, (rmsdB + 2f) / 12f)
                        );

                        if (core != null) {
                            core.setAmplitude(amplitude);
                        }

                        if (ambientHudView != null) {
                            ambientHudView.setAmplitude(amplitude);
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        String partial = bestResult(partialResults);
                        if (!partial.isEmpty()) {
                            lastPartialCommand = partial;
                            if (voiceCommand != null) {
                                voiceCommand.setText(partial);
                            }
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        String command = bestResult(results);

                        if (command.isEmpty()) {
                            command = lastPartialCommand == null
                                    ? ""
                                    : lastPartialCommand.trim();
                        }

                        finishRecognition();

                        if (!isVoiceServiceActive()) return;

                        if (command.isEmpty()) {
                            handleCommandError(SpeechRecognizer.ERROR_NO_MATCH);
                            return;
                        }

                        Log.i(TAG, "COMMAND_RESULT: " + command);

                        if (voiceCommand != null) {
                            voiceCommand.setText(command);
                        }

                        broadcastVoiceCommand(command);
                        lastPartialCommand = "";
                        sendToJarvis(command);
                    }

                    @Override
                    public void onEndOfSpeech() {
                        setVoiceState(
                                JarvisCoreView.Mode.PROCESSING,
                                "Komutunuz işleniyor..."
                        );
                    }

                    @Override
                    public void onError(int error) {
                        String partial = lastPartialCommand == null
                                ? ""
                                : lastPartialCommand.trim();

                        finishRecognition();

                        if ((error == SpeechRecognizer.ERROR_NO_MATCH
                                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                                && !partial.isEmpty()) {
                            Log.i(TAG, "COMMAND_PARTIAL_FALLBACK: " + partial);

                            if (voiceCommand != null) {
                                voiceCommand.setText(partial);
                            }

                            broadcastVoiceCommand(partial);
                            lastPartialCommand = "";
                            sendToJarvis(partial);
                            return;
                        }

                        lastPartialCommand = "";
                        handleCommandError(error);
                    }

                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEvent(int eventType, Bundle params) { }
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to create SpeechRecognizer", exception);
                showVoiceError(
                        "Konuşma tanıma başlatılamadı.",
                        SpeechRecognizer.ERROR_CLIENT
                );
                restartWakeWordAfterCommand();
                return;
            }
        }

        if (!VoiceRecognitionCoordinator.tryAcquire()) {
            showVoiceError(
                    "Mikrofon başka bir ses oturumu tarafından kullanılıyor.",
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            );
            restartWakeWordAfterCommand();
            return;
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            activeUtteranceId = null;
        }

        if (errorReset != null) {
            mainHandler.removeCallbacks(errorReset);
        }

        lastPartialCommand = "";

        if (voiceCommand != null) {
            voiceCommand.setText("Dinleme başlatılıyor...");
        }

        Log.i(TAG, "COMMAND_LISTENING");
        setVoiceState(
                JarvisCoreView.Mode.LISTENING,
                "Dinlemeye hazırlanıyor..."
        );

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L);

        recognitionActive = true;

        try {
            speechRecognizer.startListening(intent);
        } catch (RuntimeException exception) {
            finishRecognition();
            showVoiceError(
                    "Dinleme başlatılamadı.",
                    SpeechRecognizer.ERROR_CLIENT
            );
            restartWakeWordAfterCommand();
        }
    }

    private void finishRecognition() {
        if (!recognitionActive) return;
        recognitionActive = false;
        VoiceRecognitionCoordinator.release();
    }

    private void handleCommandError(int error) {
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            Log.i(TAG, "COMMAND_TIMEOUT");
            setVoiceState(JarvisCoreView.Mode.IDLE, "Komut zaman aşımına uğradı.");
        } else {
            Log.e(TAG, "SpeechRecognizer failed; errorCode=" + error);
            setVoiceState(JarvisCoreView.Mode.ERROR, "Konuşma tanıma başarısız oldu.");
        }
        restartWakeWordAfterCommand();
    }

    private void restartWakeWordAfterCommand() {
        if (!isVoiceServiceActive()) return;
        if (!wakeWordEnabled) {
            if (!overlayActive) stopSelf();
            return;
        }
        if (wakeWordRestart != null) mainHandler.removeCallbacks(wakeWordRestart);
        wakeWordRestart = () -> {
            if (wakeWordEnabled && isVoiceServiceActive() && startWakeWord()) {
                Log.i(TAG, "WAKE_WORD_RESTARTED");
            }
        };
        mainHandler.postDelayed(wakeWordRestart, 700);
    }

    private boolean isVoiceServiceActive() {
        return serviceActive;
    }

    private boolean startWakeWord() {
        if (!wakeWordEnabled || !serviceActive) return false;

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            showVoiceError(
                    "Mikrofon izni gerekli.",
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            );
            return false;
        }

        if (wakeWordManager == null) {
            wakeWordManager = new WakeWordManager(
                    this,
                    new WakeWordManager.Listener() {
                        @Override
                        public void onWakeWordDetected() {
                            Log.d(TAG, "WAKE_WORD_DETECTED");

                            mainHandler.post(() -> {
                                if (!wakeWordEnabled || !serviceActive) return;

                                stopWakeWord();

                                setVoiceState(
                                        JarvisCoreView.Mode.LISTENING,
                                        "Jarvis algılandı. Dinliyorum..."
                                );

                                mainHandler.postDelayed(() -> {
                                    if (wakeWordEnabled && serviceActive) {
                                        startCommandRecognition();
                                    }
                                }, 650);
                            });
                        }

                        @Override
                        public void onWakeWordError(Exception error) {
                            mainHandler.post(() -> {
                                Log.e(
                                        TAG,
                                        "Wake-word engine stopped unexpectedly",
                                        error
                                );

                                stopWakeWord();

                                showVoiceError(
                                        "Pasif ses algılama geçici olarak durdu.",
                                        SpeechRecognizer.ERROR_CLIENT
                                );

                                if (wakeWordEnabled && serviceActive) {
                                    mainHandler.postDelayed(
                                            () -> {
                                                if (wakeWordEnabled && serviceActive) {
                                                    startWakeWord();
                                                }
                                            },
                                            1800
                                    );
                                }
                            });
                        }
                    }
            );
        }

        if (!wakeWordManager.start()) {
            if (VoiceRecognitionCoordinator.isInUse()) {
                setVoiceState(
                        JarvisCoreView.Mode.ERROR,
                        "Mikrofon başka bir ses oturumu tarafından kullanılıyor."
                );
                return false;
            }

            setVoiceState(
                    JarvisCoreView.Mode.ERROR,
                    "Pasif ses algılama başlatılamadı."
            );
            return false;
        }

        setVoiceState(
                JarvisCoreView.Mode.IDLE,
                "WAKE • Jarvis / Hey Jarvis / Selam Jarvis / 3 alkış"
        );

        if (core != null) {
            core.setMode(JarvisCoreView.Mode.IDLE);
        }

        return true;
    }

    private void stopWakeWord() {
        if (wakeWordRestart != null) {
            mainHandler.removeCallbacks(wakeWordRestart);
            wakeWordRestart = null;
        }
        if (wakeWordManager != null && wakeWordManager.isListening()) wakeWordManager.stop();
    }

    private void setWakeWordEnabled(boolean enabled) {
        wakeWordEnabled = enabled;
        getVoicePreferences().edit()
                .putBoolean(VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, enabled)
                .apply();
        if (overlayActive || enabled) startForeground(11, notification());
    }

    private android.content.SharedPreferences getVoicePreferences() {
        return getSharedPreferences(VoiceRecognitionCoordinator.PREFS_NAME, MODE_PRIVATE);
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            ttsInitializationComplete = true;
            if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
                Log.e(TAG, "TextToSpeech initialization failed: " + status);
                if (pendingSpeech != null) {
                    showVoiceError("Türkçe sesli yanıt başlatılamadı.", status);
                    restartWakeWordAfterCommand();
                }
                pendingSpeech = null;
                return;
            }
            int languageStatus = textToSpeech.setLanguage(new Locale("tr", "TR"));
            ttsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                    && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!ttsReady) {
                Log.e(TAG, "Turkish TextToSpeech language is unavailable: " + languageStatus);
                if (pendingSpeech != null) {
                    showVoiceError("Türkçe ses verisi kullanılamıyor.", languageStatus);
                    restartWakeWordAfterCommand();
                }
                pendingSpeech = null;
                return;
            }
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    mainHandler.post(() -> {
                        if (activeUtteranceId == null || !activeUtteranceId.equals(utteranceId)) return;
                        stopWakeWord();
                        Log.i(TAG, "TTS_STARTED");
                        setVoiceState(JarvisCoreView.Mode.SPEAKING, "JARVIS yanıtlıyor...");
                    });
                }

                @Override public void onDone(String utteranceId) {
                    mainHandler.post(() -> {
                        if (activeUtteranceId == null || !activeUtteranceId.equals(utteranceId)) return;
                        activeUtteranceId = null;
                        Log.i(TAG, "TTS_FINISHED");
                        if (core != null && core.getMode() == JarvisCoreView.Mode.SPEAKING) {
                            setVoiceState(JarvisCoreView.Mode.IDLE, "Hazır");
                        }
                        restartWakeWordAfterCommand();
                    });
                }

                @Override public void onError(String utteranceId) {
                    mainHandler.post(() -> {
                        if (activeUtteranceId == null || !activeUtteranceId.equals(utteranceId)) return;
                        activeUtteranceId = null;
                        if (core != null && core.getMode() == JarvisCoreView.Mode.SPEAKING) {
                            showVoiceError("Yanıt seslendirilemedi.", TextToSpeech.ERROR);
                        }
                        restartWakeWordAfterCommand();
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

    private void sendToJarvis(String command) {
        setVoiceState(JarvisCoreView.Mode.PROCESSING, "Yanıt hazırlanıyor...");
        String normalized = command.toLowerCase(new Locale("tr", "TR"));
        boolean greeting = normalized.contains("merhaba")
                || normalized.contains("selam")
                || normalized.contains("hey jarvis");
        String response = greeting
                ? "Merhaba. Sizi dinliyorum."
                : "Komutunuzu aldım: " + command;
        speak(response);
    }

    private void speak(String text) {
        stopWakeWord();
        if (textToSpeech == null || !ttsReady) {
            if (!ttsInitializationComplete) {
                pendingSpeech = text;
                return;
            }
            showVoiceError("Türkçe sesli yanıt kullanılamıyor.", TextToSpeech.ERROR);
            restartWakeWordAfterCommand();
            return;
        }
        activeUtteranceId = UTTERANCE_ID + "-" + (++utteranceSequence);
        if (textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, activeUtteranceId) == TextToSpeech.ERROR) {
            activeUtteranceId = null;
            showVoiceError("Yanıt seslendirilemedi.", TextToSpeech.ERROR);
            restartWakeWordAfterCommand();
        }
    }

    private String bestResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return results != null && !results.isEmpty() && results.get(0) != null ? results.get(0).trim() : "";
    }

    private void setVoiceState(JarvisCoreView.Mode mode, String message) {
        Intent i=new Intent(VoiceRecognitionCoordinator.ACTION_VOICE_STATUS).setPackage(getPackageName())
                .putExtra("mode",mode.name()).putExtra("message",message);
        sendBroadcast(i); updateAmbientHud(mode,message);
        if(core!=null) core.setMode(mode);
        if(voiceStatus!=null) voiceStatus.setText(mode.name()+" • "+message);
    }

    private void updateAmbientHud(JarvisCoreView.Mode mode,String message){
        if(mode==JarvisCoreView.Mode.IDLE){ hideAmbientHud(); return; }
        showAmbientHud(); if(ambientHudView!=null) ambientHudView.setState(mode,message);
    }

    private void showAmbientHud(){
        if(ambientHudView!=null || !Settings.canDrawOverlays(this)) return;
        ambientHudWindowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
        ambientHudView=new JarvisAmbientOverlayView(this);
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        ambientHudLayoutParams=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,PixelFormat.TRANSLUCENT);
        ambientHudLayoutParams.gravity=Gravity.TOP|Gravity.START;
        try{ ambientHudWindowManager.addView(ambientHudView,ambientHudLayoutParams); }
        catch(RuntimeException e){ Log.e(TAG,"J+ Ambient HUD açılamadı",e); ambientHudView=null; }
    }

    private void hideAmbientHud(){
        if(ambientHudWindowManager!=null && ambientHudView!=null){
            try{ ambientHudWindowManager.removeView(ambientHudView); }catch(RuntimeException ignored){}
        }
        ambientHudView=null; ambientHudLayoutParams=null;
    }

    private void broadcastVoiceCommand(String command) {
        Intent commandIntent = new Intent(VoiceRecognitionCoordinator.ACTION_VOICE_COMMAND)
                .setPackage(getPackageName())
                .putExtra("command", command);
        sendBroadcast(commandIntent);
    }

    private void showVoiceError(String message,int errorCode){
        Log.e(TAG,"Voice operation failed; errorCode="+errorCode+", message="+message);
        setVoiceState(JarvisCoreView.Mode.ERROR,message);
        if(errorReset!=null) mainHandler.removeCallbacks(errorReset);
        errorReset=()->{ if(serviceActive) setVoiceState(JarvisCoreView.Mode.IDLE,"Hazır"); };
        mainHandler.postDelayed(errorReset,1800);
    }

    private Notification notification() {
        Intent i=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);
        String text = wakeWordEnabled ? "JARVIS hands-free aktif" : "JARVIS hazır";
        return b.setContentTitle("JARVIS").setContentText(text).setSmallIcon(R.drawable.ic_jarvis_plus_small).setContentIntent(pi).setOngoing(true).build();
    }
    private void createChannel(){ if(Build.VERSION.SDK_INT>=26){ NotificationManager nm=getSystemService(NotificationManager.class); nm.createNotificationChannel(new NotificationChannel(CH,getString(R.string.notif_channel),NotificationManager.IMPORTANCE_LOW)); } }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() {
        setVoiceState(JarvisCoreView.Mode.IDLE, "Hazır");
        serviceActive=false;
        overlayActive=false;
        mainHandler.removeCallbacksAndMessages(null);
        finishRecognition();
        if (wakeWordManager != null) {
            wakeWordManager.destroy();
            wakeWordManager = null;
        }
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (RuntimeException exception) { Log.w(TAG, "SpeechRecognizer cancellation failed", exception); }
            speechRecognizer.destroy();
            speechRecognizer=null;
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech=null;
        }
        activeUtteranceId = null;
        pendingSpeech=null;
        // Kullanıcının Sesli Aktivasyon tercihini servis kapanırken değiştirme.
        wakeWordEnabled = getVoicePreferences().getBoolean(
                VoiceRecognitionCoordinator.PREF_WAKE_WORD_ENABLED, false);
        try { if(wm!=null && root!=null) wm.removeView(root); } catch(Exception ignored) {}
        root=null; core=null; voiceStatus=null; voiceCommand=null;
        Log.i(TAG, "VOICE_SERVICE_STOPPED");
        super.onDestroy();
    }
}
