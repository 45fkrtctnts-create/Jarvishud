package com.fikret.jarvishud;

import java.util.concurrent.atomic.AtomicBoolean;

final class VoiceRecognitionCoordinator {
    static final String ACTION_WAKE_WORD_START = "com.fikret.jarvishud.WAKE_WORD_START";
    static final String ACTION_WAKE_WORD_STOP = "com.fikret.jarvishud.WAKE_WORD_STOP";
    static final String ACTION_COMMAND_LISTENING = "com.fikret.jarvishud.COMMAND_LISTENING";
    static final String ACTION_VOICE_STATUS = "com.fikret.jarvishud.VOICE_STATUS";
    static final String ACTION_VOICE_COMMAND = "com.fikret.jarvishud.VOICE_COMMAND";
    static final String PREFS_NAME = "jarvis_voice";
    static final String PREF_WAKE_WORD_ENABLED = "wake_word_enabled";

    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private VoiceRecognitionCoordinator() { }

    static boolean tryAcquire() {
        return ACTIVE.compareAndSet(false, true);
    }

    static boolean tryAcquireWakeWord() {
        return ACTIVE.compareAndSet(false, true);
    }

    static void release() {
        ACTIVE.set(false);
    }

    static boolean isInUse() {
        return ACTIVE.get();
    }
}
