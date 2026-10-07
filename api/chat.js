export default async function handler(req,res){
  if(req.method!=="POST") return res.status(405).json({error:"POST gerekli"});
  const command=(req.body?.command||"").trim();
  if(!command) return res.status(400).json({error:"Komut boş"});
  try{
    const r=await fetch("https://api.openai.com/v1/responses",{
      method:"POST",
      headers:{
        "Content-Type":"application/json",
        "Authorization":"Bearer "+process.env.OPENAI_API_KEY
      },
      body:JSON.stringify({
        model:"gpt-6-luna",
        instructions:`Sen JARVIS adlı Türkçe kişisel asistansın. Kısa, doğal ve doğrudan cevap ver. Sesli okunacağı için gereksiz biçimlendirme kullanma.

JARVIS PROJE HAFIZASI:
ÇALIŞAN SİSTEMLER:
- V4.5 stabil sürüm yedeklendi.
- openWakeWord ile "Hey Jarvis" çalışıyor.
- Türkçe SpeechRecognizer çalışıyor.
- Vercel + OpenAI üzerinden gerçek AI cevapları çalışıyor.
- Android medya kontrolleri çalışıyor: oynat/durdur/devam/sonraki/önceki ve Spotify açma.
- J+ tam ekran HUD çalışıyor.
- V5.0 HUD denendi ancak görünüm yeniden tasarlanacak.

AKTİF YAPILACAKLAR VE ÖNCELİK:
1. JARVIS proje hafızasını kurmak ve test etmek.
2. HUD'u daha gerçekçi ve istenen stile yeniden tasarlamak.
3. JARVIS konuşma sesini daha doğal, tok ve özgün hale getirmek.
4. Konuşma modunu geliştirip kısa süreli sohbet devamlılığı eklemek.
5. "Jarvis dur" komutuyla konuşmayı kesme.
6. Uygulama ve cihaz kontrollerini genişletmek.
7. Haberler ve canlı bilgi erişimi eklemek.
8. Teşhis ekranı eklemek: WAKE/STT/AI/TTS/MEDIA durumları.
9. Batarya ve arka plan optimizasyonu.
10. Jarvis ve Selam Jarvis için özel wake-word modelleri.

Kullanıcı proje hakkında "bugün ne yapacağız", "nerede kalmıştık", "sıradaki iş ne", "hangi özellikler tamamlandı" gibi bir şey sorarsa yukarıdaki proje hafızasına göre cevap ver. Bilmediğin proje durumunu uydurma.`,
        input:command,
        max_output_tokens:250
      })
    });
    const data=await r.json();
    if(!r.ok) return res.status(r.status).json({error:data?.error?.message||"OpenAI hatası"});
    const answer=(data.output||[]).flatMap(x=>x.content||[]).find(x=>x.type==="output_text")?.text||"Yanıt alınamadı.";
    res.status(200).json({answer});
  }catch(e){
    res.status(500).json({error:"Sunucu hatası"});
  }
}
