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
        instructions:"Sen JARVIS adlı Türkçe kişisel asistansın. Kısa, doğal ve doğrudan cevap ver. Sesli okunacağı için gereksiz biçimlendirme kullanma.",
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
