/* ELITE 루틴 서비스워커: 앱 파일은 캐시(오프라인 실행), 서버 API는 항상 네트워크 */
var V = 'elite-routine-v1.0.0';
var CORE = ['./', './index.html', './manifest.webmanifest', './icons/icon-192.png', './icons/icon-512.png'];
self.addEventListener('install', function (e) { e.waitUntil(caches.open(V).then(function (c) { return c.addAll(CORE); }).then(function () { return self.skipWaiting(); })); });
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (ks) { return Promise.all(ks.filter(function (k) { return k !== V; }).map(function (k) { return caches.delete(k); })); }).then(function () { return self.clients.claim(); }));
});
self.addEventListener('fetch', function (e) {
  var r = e.request, u = new URL(r.url);
  if (r.method !== 'GET' || u.hostname.endsWith('supabase.co')) return;           // 서버 통신은 캐시하지 않음
  if (r.mode === 'navigate') {                                                    // 페이지: 네트워크 우선, 실패하면 캐시
    e.respondWith(fetch(r).then(function (res) { var cp = res.clone(); caches.open(V).then(function (c) { c.put('./index.html', cp); }); return res; }).catch(function () { return caches.match('./index.html'); }));
    return;
  }
  e.respondWith(caches.match(r).then(function (hit) {                             // 그 외(폰트·아이콘): 캐시 우선, 없으면 받아서 저장
    return hit || fetch(r).then(function (res) { if (res.ok || res.type === 'opaque') { var cp = res.clone(); caches.open(V).then(function (c) { c.put(r, cp); }); } return res; });
  }));
});
self.addEventListener('notificationclick', function (e) {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (cs) { return cs.length ? cs[0].focus() : self.clients.openWindow('./'); }));
});
