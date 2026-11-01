// 시몬골프 피팅 화면 전용 서비스워커 (scope: /Prompoter/fitting/)
const C='sf-fit-v1';                      // 내용 바꿀 때 숫자 올릴 필요 없음(네트워크 우선)
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(['./'])));self.skipWaiting()});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k.startsWith('sf-fit-')&&k!==C).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{
  const r=e.request; if(r.method!=='GET'||new URL(r.url).origin!==location.origin) return;
  const isPage=r.mode==='navigate';
  e.respondWith((async()=>{
    try{
      const ctl=new AbortController(); const to=setTimeout(()=>ctl.abort(),isPage?4000:8000); // 느린 매장 와이파이에서 무한 대기 방지
      const res=await fetch(r,{cache:'no-cache',signal:ctl.signal}); clearTimeout(to);
      if(res.ok){ const c=await caches.open(C); c.put(isPage?'./':r,res.clone()) }
      return res;
    }catch(err){
      const c=await caches.open(C); return (await c.match(isPage?'./':r)) || Response.error();
    }
  })());
});
