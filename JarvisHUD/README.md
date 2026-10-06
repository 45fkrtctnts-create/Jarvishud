# JARVIS HUD — Android MVP

Bu proje, konuşma sesine tepki veren **yüzen JARVIS HUD** ve ana ekrana eklenebilen **dikdörtgen Android widget** için hazırlanmış ilk çalışan sürüm taslağıdır.

## İçerik

- Yüzen overlay panel
- Sürükleyerek taşıma
- Mikrofon ses seviyesine göre pulse yapan JARVIS çekirdeği
- Saat ve tarih
- Google News Türkçe RSS üzerinden 3 son dakika başlığı
- Android Takvim'den bugünkü etkinlikler
- Ana ekran widget'ı: saat + tarih + JARVIS + haber + takvim + günlük program
- Widget'a dokununca kontrol uygulamasını açma
- Offline "Jarvis" wake-word aktivasyonu (sherpa-onnx Keyword Spotting)

## APK'ya dönüştürme — Android Studio olmadan

Projeyi GitHub'a yükleyip **Actions → Build Android APK → Run workflow** çalıştırabilirsin. Oluşan `JarvisHUD-debug-apk` artifact'ının içinde `app-debug.apk` bulunur.

**Offline Sesli Aktivasyon:** Sherpa-onnx Keyword Spotting model dosyaları `app/src/main/assets/kws/gigaspeech/` klasöründe gömülüdür. Hiçbir API anahtarı veya dış servis gerekmez. Build sırasında model dosyaları otomatik olarak APK'ye dahil edilir.

APK'yı açtıktan sonra **Sesli Aktivasyon** anahtarını açık konuma getir. Mikrofon izni verilmeden servis başlamaz. Anahtar kapatıldığında wake-word dinleme durur. Wake-word kapalıyken ana ekrandaki veya floating HUD'daki JARVIS simgesi dokunarak konuşma başlatmaya devam eder.

## Android Studio ile

Projeyi aç → Gradle sync → Run veya `assembleDebug`.

## İzinler

- `RECORD_AUDIO`: dokunarak konuşma ve isteğe bağlı wake-word dinleme
- `READ_CALENDAR`: günlük program
- `SYSTEM_ALERT_WINDOW`: diğer uygulamaların üzerinde yüzen HUD
- `INTERNET`: haber RSS'i
- Bildirim/foreground service izinleri: yüzen HUD servisinin Android kurallarına uygun çalışması

## Not

Ana ekran widget'ları Android kısıtları nedeniyle gerçek zamanlı 60 FPS animasyon için uygun değildir. Bu nedenle widget statik/periodik güncellenir; gerçek zamanlı animasyon **Floating HUD** içinde çalışır.
