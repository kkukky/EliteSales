#!/usr/bin/env python3
"""src/admin.html (기존 단일 HTML) -> dist/ (PWA). 수정은 항상 src 원본에 하고 이 스크립트로 다시 만든다."""
import os, re, sys, json

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, 'src', 'admin.html')
DIST = os.path.join(ROOT, 'dist')
SB_URL = 'https://ggesrslhgqtiwdxuyvnh.supabase.co'
SB_KEY = 'sb_publishable_Xtpyq_Xp_8caJgit_jaE-w_i76ZdhKg'
VERSION = '1.0.0'

h = open(SRC, encoding='utf-8').read()


def rep(old, new, count=1):
    global h
    n = h.count(old)
    assert n == count, 'expected %d got %d for: %s' % (count, n, old[:70])
    h = h.replace(old, new)


def cut(start, end, new):
    """start 로 시작해 end 직전까지를 new 로 교체"""
    global h
    i = h.index(start)
    j = h.index(end, i)
    h = h[:i] + new + h[j:]


# ---------- 1. head: PWA 메타 ----------
rep('<meta name="color-scheme" content="light dark">',
    '<meta name="color-scheme" content="light dark">\n'
    '<meta name="theme-color" content="#0E0E10">\n'
    '<link rel="manifest" href="manifest.webmanifest">\n'
    '<link rel="icon" href="icons/icon-192.png">\n'
    '<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">\n'
    '<meta name="mobile-web-app-capable" content="yes">\n'
    '<meta name="apple-mobile-web-app-capable" content="yes">\n'
    '<meta name="apple-mobile-web-app-title" content="ELITE 루틴">\n'
    '<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">')

# ---------- 2. 안드로이드/폴더 기능 끄기 ----------
cut('function fsOK() {', '\nfunction idb(', 'function fsOK() { return false; }')

# ---------- 3. 탭바: 관리자 탭은 관리자만 ----------
rep("var t = [['home', '오늘', 'today'], ['log', '기록', 'doc'], ['admin', '관리자', 'bars'], ['settings', '설정', 'gear']];",
    "var t = [['home', '오늘', 'today'], ['log', '기록', 'doc']].concat(isAdmin() ? [['admin', '관리자', 'bars']] : [], [['settings', '설정', 'gear']]);")

# ---------- 4. 관리자 화면: 실제 팀 데이터 ----------
cut('VIEWS.admin = function () {', '\n\n/* ---------- 설정: 저장 위치 확인과 엑셀 내보내기', r'''VIEWS.admin = function () {
  if (!isAdmin()) { location.hash = '#home'; return ''; }
  var T = SYNC.team, all = T ? T.list : [];
  if (!T && !SYNC.teamBusy) loadTeam();
  var present = all.filter(function (p) { return p.t; }).length;
  var late = all.filter(function (p) { return p.t && p.t > pad(WORK_START_H) + ':00'; }).length;
  var avg = all.length ? all.reduce(function (a, p) { return a + p.c.reduce(function (x, y) { return x + y; }, 0); }, 0) / all.length : 0;
  var rows = all.map(function (p) {
    var l = p.t && p.t > pad(WORK_START_H) + ':00';
    var chip = !p.t ? '<span class="chip">미출근</span>' : (l ? '<span class="chip bad">지각</span>' : '<span class="chip ok">정상</span>');
    return '<div class="prow"><div class="pl"><b>' + esc(p.n) + '</b><span>' + (p.t ? '<span class="muted">' + p.t + '</span> ' : '') + chip +
      ' <span class="muted">7일 평균 ' + p.w + '개</span></span></div>' + cells(p.c) + '</div>';
  }).join('');
  return '<div class="screen">' + brandRow() + '<h2 class="sub">관리자 현황</h2>' +
    '<div class="stats"><div class="stat"><span class="h">' + present + '/' + all.length + '</span><span>출근</span></div><div class="stat"><span class="h">' + late + '</span><span>지각</span></div><div class="stat"><span class="h">' + (Math.round(avg * 10) / 10) + '</span><span>평균 완료</span></div></div>' +
    '<div class="card"><div class="ct">사람별 현황 · 오늘</div>' + (SYNC.teamErr ? '<p class="note">' + esc(SYNC.teamErr) + '</p>' : (!T ? '<p class="note">불러오는 중이에요…</p>' : (rows || '<p class="note">아직 등록된 설계사가 없어요.</p>'))) +
    '<div style="margin-top:12px"><button class="btn ghost" data-act="team-reload"><span>새로 고침</span></button></div></div>' +
    '<p class="note">설계사가 앱을 열고 기록을 바꾸면 몇 초 뒤 여기에 반영돼요. 오늘 기록이 아직 없는 설계사는 미출근으로 보여요.</p></div>' + tabbar('admin');
};''')

# ---------- 5. 설정 화면 다시 쓰기 ----------
cut('VIEWS.settings = function () {', '\n\n/* ---------- 멘탈 관리 ---------- */', r'''VIEWS.settings = function () {
  var kv = function (k, v) { return '<div class="kv"><span>' + k + '</span><b>' + v + '</b></div>'; };
  var recs = logRecords(), real = recs.filter(function (r) { return !r.sample; }).length;
  var perm = ('Notification' in window) ? Notification.permission : 'unsupported';
  var inst = isStandalone() ? '<span class="ok">설치됨</span>' : '<span class="late">브라우저로 보는 중</span>';
  return '<div class="screen">' + brandRow() + '<h2 class="sub">설정</h2>' +
    accountCard() +
    '<div class="card"><div class="ct">앱 설치</div>' + kv('상태', inst) +
    (isStandalone() ? '' : '<p class="note" style="margin:10px 0 0">' + (DEFER ? '아래 버튼으로 홈 화면에 설치해요.' : '브라우저 메뉴(⋮ 또는 공유)에서 \'홈 화면에 추가\'를 누르면 앱처럼 쓸 수 있어요.') + '</p>') +
    (DEFER && !isStandalone() ? '<div style="margin-top:12px"><button class="btn" data-act="pwa-install"><span>홈 화면에 설치</span></button></div>' : '') + '</div>' +
    '<div class="card"><div class="ct">알림</div>' + kv('오늘의 루틴 알림', S.notifOn === false ? '꺼짐' : (perm === 'granted' ? '<span class="ok">켜짐</span>' : '<span class="late">권한 필요</span>')) +
    '<p class="note" style="margin-top:10px">앱이 열려 있거나 백그라운드에 있을 때 알림 창에 오늘의 진행 상황(예: 3/8)을 보여줘요. 정해진 시각에 먼저 알려 주는 푸시 알림은 서버 설정이 더 필요해 아직 준비 중이에요.</p>' +
    '<div style="margin-top:12px;display:grid;grid-template-columns:1fr 1fr;gap:8px">' +
    (perm === 'granted' ? '' : '<button class="btn" data-act="notif-perm"><span>알림 허용</span></button>') +
    '<button class="btn ghost" data-act="notif-toggle"><span>' + (S.notifOn === false ? '알림 켜기' : '알림 끄기') + '</span></button></div>' +
    (perm === 'denied' ? '<p class="note" style="margin-top:10px">브라우저에서 알림이 막혀 있어요. 사이트 설정에서 알림을 허용으로 바꿔 주세요.</p>' : '') + '</div>' +
    '<div class="card"><div class="ct">루틴 기록 내려받기</div>' + kv('파일 형식', '엑셀 (.xlsx)') + kv('저장할 행', recs.length + '일 (실제 ' + real + '일)') + kv('마지막 내려받기', S.lastExport ? fmtDT(S.lastExport) : '아직 없음') +
    '<div style="margin-top:12px"><button class="btn" data-act="export-xlsx"><span>엑셀로 내려받기</span></button></div>' +
    (exportMsg ? '<p class="note" style="margin-top:10px">' + exportMsg + '</p>' : '') +
    (exportFail ? '<p class="note" style="margin-top:10px;color:var(--brand-text)">파일을 만들지 못했어요. 다시 시도해 주세요.</p>' : '') +
    '<p class="note" style="margin-top:10px">내려받은 파일은 휴대폰의 다운로드 폴더에 저장돼요. 폴더 자동 저장은 웹앱(PWA)에서는 쓸 수 없어, 서버 저장으로 대신해요.</p></div>' +
    '<p class="note" style="text-align:center">버전 ''' + VERSION + r'''</p></div>' + tabbar('settings');
};''')

# ---------- 6. 알림: 웹 방식 ----------
cut('function syncNotif() {', '\nwindow.permBack', r'''function syncNotif() {
  try {
    if (!('Notification' in window) || Notification.permission !== 'granted' || S.notifOn === false) return;
    var n = doneCount(), total = ITEMS.length, key = S.date + ':' + n;
    if (syncNotif.last === key) return; syncNotif.last = key;
    var next = ITEMS.filter(function (i) { return !S.done[i.id]; })[0];
    var opt = { body: n === total ? '오늘 루틴을 모두 마쳤어요' : '다음 · ' + next.title, tag: 'elite-progress', renotify: false, silent: true,
      icon: 'icons/icon-192.png', badge: 'icons/icon-192.png' };
    var title = 'ELITE 루틴 ' + n + '/' + total;
    if (navigator.serviceWorker && navigator.serviceWorker.ready) navigator.serviceWorker.ready.then(function (reg) { reg.showNotification(title, opt); });
  } catch (e) { }
}''')

# ---------- 7. save() 에서 서버 동기화 호출 ----------
rep("if (!skipAuto && S.autoSave && typeof schedAuto === 'function') schedAuto(); }",
    "if (typeof SYNC !== 'undefined' && SYNC.dirty) SYNC.dirty(); }")

# ---------- 8. 라우팅: 로그인 게이트 ----------
rep("function route() { var h = (location.hash || '#home').slice(1); return VIEWS[h] ? h : 'home'; }",
    "function route() { if (needLogin()) return 'login'; var h = (location.hash || '#home').slice(1); return VIEWS[h] ? h : 'home'; }")

# ---------- 9. 클릭/제출 핸들러 추가 ----------
rep("  if (act === 'lock-toggle') {", r'''  if (act === 'auth-mode') { AUTHUI.mode = el.getAttribute('data-v'); AUTHUI.msg = ''; render(); return; }
  if (act === 'auth-local') { try { localStorage.setItem(LOCAL_KEY, '1'); } catch (er) { } render(); return; }
  if (act === 'auth-login') { try { localStorage.removeItem(LOCAL_KEY); } catch (er) { } location.hash = '#home'; render(); return; }
  if (act === 'auth-logout') { signOut(); return; }
  if (act === 'sync-now') { SYNC.flush(true); return; }
  if (act === 'team-reload') { loadTeam(); return; }
  if (act === 'name-save') { var ni = app.querySelector('#nameIn'); if (ni && ni.value.trim()) saveName(ni.value.trim().slice(0, 20)); return; }
  if (act === 'pwa-install') { if (DEFER) { DEFER.prompt(); DEFER.userChoice.then(function () { DEFER = null; render(); }); } return; }
  if (act === 'notif-perm') { if ('Notification' in window) Notification.requestPermission().then(function () { syncNotif.last = ''; render(); }); return; }
  if (act === 'lock-toggle') {''')
rep("app.addEventListener('submit', function (e) {\n  var f = e.target.closest('[data-form]'); if (!f) return; e.preventDefault();",
    "app.addEventListener('submit', function (e) {\n  var f = e.target.closest('[data-form]'); if (!f) return; e.preventDefault();\n  if (f.getAttribute('data-form') === 'auth') { doAuth(f); return; }")

# ---------- 10. 동기화·인증 모듈 삽입 ----------
module = open(os.path.join(ROOT, 'src', 'sync.js'), encoding='utf-8').read()
module = module.replace('__SB_URL__', SB_URL).replace('__SB_KEY__', SB_KEY)
rep('/* ---------- 시작 ---------- */', module + '\n/* ---------- 시작 ---------- */')

# ---------- 11. 서비스워커 등록 ----------
rep('render();\n})();\n</script>', r'''render();
SYNC.start();
if ('serviceWorker' in navigator) { window.addEventListener('load', function () { navigator.serviceWorker.register('sw.js').catch(function () { }); }); }
})();
</script>''')

os.makedirs(DIST, exist_ok=True)
open(os.path.join(DIST, 'index.html'), 'w', encoding='utf-8').write(h)
print('index.html', len(h))
