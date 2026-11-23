/* ---------- PWA: 로그인 + 서버 동기화 (Supabase REST, 외부 라이브러리 없음) ---------- */
var SB_URL = '__SB_URL__', SB_KEY = '__SB_KEY__';
var AUTH_KEY = 'elite-auth-v1', LOCAL_KEY = 'elite-local-mode';
var AUTHUI = { mode: 'login', msg: '', busy: false };
var DEFER = null;
window.addEventListener('beforeinstallprompt', function (e) { e.preventDefault(); DEFER = e; if (route() === 'settings') render(); });
window.addEventListener('appinstalled', function () { DEFER = null; render(); });
function isStandalone() { try { return window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true; } catch (e) { return false; } }

var AUTH = (function () { try { var o = JSON.parse(localStorage.getItem(AUTH_KEY) || 'null'); return o && o.access_token ? o : null; } catch (e) { return null; } })();
function putAuth(a) { AUTH = a; try { if (a) localStorage.setItem(AUTH_KEY, JSON.stringify(a)); else localStorage.removeItem(AUTH_KEY); } catch (e) { } }
function localMode() { try { return localStorage.getItem(LOCAL_KEY) === '1'; } catch (e) { return false; } }
function needLogin() { return !AUTH && !localMode(); }
function isAdmin() { return !!(AUTH && SYNC.profile && SYNC.profile.role === 'admin'); }

function sbFetch(path, opt, useUser) {
  opt = opt || {};
  var h = { apikey: SB_KEY, 'Content-Type': 'application/json' };
  if (useUser && AUTH) h.Authorization = 'Bearer ' + AUTH.access_token; else h.Authorization = 'Bearer ' + SB_KEY;
  if (opt.prefer) h.Prefer = opt.prefer;
  return fetch(SB_URL + path, { method: opt.method || 'GET', headers: h, body: opt.body ? JSON.stringify(opt.body) : undefined }).then(function (r) {
    return r.text().then(function (t) {
      var j = null; try { j = t ? JSON.parse(t) : null; } catch (e) { }
      if (!r.ok) { var er = new Error((j && (j.msg || j.message || j.error_description || j.error)) || ('HTTP ' + r.status)); er.status = r.status; throw er; }
      return j;
    });
  });
}
function refreshAuth() {
  if (!AUTH || !AUTH.refresh_token) return Promise.reject(new Error('no session'));
  return sbFetch('/auth/v1/token?grant_type=refresh_token', { method: 'POST', body: { refresh_token: AUTH.refresh_token } }).then(takeSession);
}
function takeSession(j) {
  if (!j || !j.access_token) throw new Error('세션을 받지 못했어요');
  putAuth({ access_token: j.access_token, refresh_token: j.refresh_token, expires_at: Math.floor(Date.now() / 1000) + (j.expires_in || 3600) - 60, uid: j.user.id, email: j.user.email });
  return AUTH;
}
/* 로그인한 상태로 호출. 만료 임박 또는 401이면 한 번 갱신 후 재시도 */
function api(path, opt) {
  var go = function () { return sbFetch(path, opt, true); };
  var pre = (AUTH && AUTH.expires_at && AUTH.expires_at < Date.now() / 1000) ? refreshAuth() : Promise.resolve();
  return pre.then(go).catch(function (er) {
    if (er && er.status === 401 && AUTH) return refreshAuth().then(go);
    throw er;
  });
}

function doAuth(f) {
  if (AUTHUI.busy) return;
  var email = f.elements.email.value.trim(), pw = f.elements.pw.value, name = f.elements.name ? f.elements.name.value.trim() : '';
  if (!email || !pw) { AUTHUI.msg = '이메일과 비밀번호를 입력해 주세요.'; render(); return; }
  if (AUTHUI.mode === 'signup' && pw.length < 6) { AUTHUI.msg = '비밀번호는 6자 이상이어야 해요.'; render(); return; }
  AUTHUI.busy = true; AUTHUI.msg = '';
  var p = AUTHUI.mode === 'signup'
    ? sbFetch('/auth/v1/signup', { method: 'POST', body: { email: email, password: pw, data: { name: name || email.split('@')[0] } } })
    : sbFetch('/auth/v1/token?grant_type=password', { method: 'POST', body: { email: email, password: pw } });
  p.then(function (j) {
    AUTHUI.busy = false;
    if (!j || !j.access_token) { AUTHUI.mode = 'login'; AUTHUI.msg = '가입 확인 메일을 보냈어요. 메일의 링크를 누른 뒤 로그인해 주세요.'; render(); return; }
    takeSession(j); try { localStorage.removeItem(LOCAL_KEY); } catch (e) { }
    location.hash = '#home'; SYNC.start(); render();
  }).catch(function (er) {
    AUTHUI.busy = false;
    var m = String(er.message || '');
    AUTHUI.msg = /invalid login/i.test(m) ? '이메일 또는 비밀번호가 맞지 않아요.' : (/already registered|already been registered/i.test(m) ? '이미 가입된 이메일이에요. 로그인해 주세요.' : (/Email not confirmed/i.test(m) ? '메일 확인이 아직 안 됐어요. 받은 메일의 링크를 눌러 주세요.' : (/Failed to fetch|NetworkError/i.test(m) ? '인터넷에 연결되어 있지 않아요.' : '실패했어요: ' + m)));
    render();
  });
}
function signOut() {
  if (!confirm('로그아웃할까요? 이 기기의 기록은 그대로 남아요.')) return;
  var done = function () { putAuth(null); SYNC.profile = null; SYNC.team = null; try { localStorage.removeItem(LOCAL_KEY); } catch (e) { } render(); };
  if (AUTH) sbFetch('/auth/v1/logout', { method: 'POST' }, true).then(done, done); else done();
}
function saveName(n) {
  if (!AUTH) return;
  api('/rest/v1/elite_profiles?id=eq.' + AUTH.uid, { method: 'PATCH', body: { name: n }, prefer: 'return=minimal' }).then(function () { SYNC.profile.name = n; render(); }, function (er) { SYNC.err = '이름 저장 실패: ' + er.message; render(); });
}

VIEWS.login = function () {
  var su = AUTHUI.mode === 'signup', inp = 'width:100%;min-height:46px;font-size:16px;border:1px solid var(--line);border-radius:4px;background:var(--bg);color:var(--text);padding:0 12px;margin-top:10px';
  return '<div class="screen">' + brandRow() + '<h2 class="sub">' + (su ? '가입하기' : '로그인') + '</h2>' +
    '<form class="card" data-form="auth" autocomplete="on">' +
    (su ? '<input name="name" type="text" maxlength="20" placeholder="이름 (관리자에게 보여요)" autocomplete="name" style="' + inp + ';margin-top:0">' : '') +
    '<input name="email" type="email" inputmode="email" placeholder="이메일" autocomplete="username" required style="' + inp + (su ? '' : ';margin-top:0') + '">' +
    '<input name="pw" type="password" placeholder="비밀번호 (6자 이상)" autocomplete="' + (su ? 'new-password' : 'current-password') + '" required style="' + inp + '">' +
    (AUTHUI.msg ? '<p class="note" role="alert" style="color:var(--brand-text)">' + esc(AUTHUI.msg) + '</p>' : '') +
    '<div style="margin-top:14px"><button class="btn" type="submit"' + (AUTHUI.busy ? ' disabled' : '') + '><span>' + (AUTHUI.busy ? '잠시만요…' : (su ? '가입하기' : '로그인')) + '</span></button></div>' +
    '<div style="margin-top:8px"><button class="btn ghost" type="button" data-act="auth-mode" data-v="' + (su ? 'login' : 'signup') + '"><span>' + (su ? '이미 계정이 있어요' : '처음이에요 · 가입하기') + '</span></button></div></form>' +
    '<p class="note">로그인하면 기록이 서버에 저장돼 폰을 바꿔도 이어지고, 관리자가 팀 현황을 볼 수 있어요.</p>' +
    '<div style="margin-top:12px"><button class="btn ghost" data-act="auth-local"><span>로그인 없이 이 기기에서만 사용</span></button></div></div>';
};
function accountCard() {
  var kv = function (k, v) { return '<div class="kv"><span>' + k + '</span><b>' + v + '</b></div>'; };
  if (!AUTH) return '<div class="card"><div class="ct">계정</div>' + kv('상태', '<span class="late">로그인 안 함</span>') +
    '<p class="note" style="margin:10px 0 0">지금은 이 기기에만 저장돼요. 로그인하면 서버에 저장하고 다른 기기에서도 이어서 볼 수 있어요.</p>' +
    '<div style="margin-top:12px"><button class="btn" data-act="auth-login"><span>로그인하기</span></button></div></div>';
  var P = SYNC.profile, st = SYNC.err ? '<span class="late">' + esc(SYNC.err) + '</span>' : (SYNC.pending ? '저장 대기 중' : (SYNC.last ? '<span class="ok">' + hm(SYNC.last) + '</span>' : '아직 없음'));
  return '<div class="card"><div class="ct">계정</div>' + kv('이메일', esc(AUTH.email)) + kv('역할', P ? (P.role === 'admin' ? '관리자' : '설계사') : '확인 중') +
    '<div style="margin-top:8px;display:grid;grid-template-columns:1fr auto;gap:8px"><input id="nameIn" type="text" maxlength="20" value="' + esc(P ? P.name : '') + '" placeholder="이름" aria-label="이름" style="min-height:44px;font-size:16px;border:1px solid var(--line);border-radius:4px;background:var(--bg);color:var(--text);padding:0 12px;width:100%"><button class="btn ghost sm" data-act="name-save"><span>이름 저장</span></button></div>' +
    kv('마지막 서버 저장', st) +
    '<div style="margin-top:12px;display:grid;grid-template-columns:1fr 1fr;gap:8px"><button class="btn ghost" data-act="sync-now"><span>지금 동기화</span></button><button class="btn ghost" data-act="auth-logout"><span>로그아웃</span></button></div></div>';
}

var SYNC = {
  profile: null, team: null, teamBusy: false, teamErr: '', err: '', last: 0, pending: false, timer: null, flying: false, started: false,
  dirty: function () { if (!AUTH) return; SYNC.pending = true; clearTimeout(SYNC.timer); SYNC.timer = setTimeout(function () { SYNC.flush(false); }, 3000); },
  rows: function (all) {
    var out = [], mk = function (d, at, cells, n) { return { user_id: AUTH.uid, d: d, attend_at: at ? new Date(at).toISOString() : null, cells: cells, done_count: n, updated_at: new Date().toISOString() }; };
    out.push(mk(S.date, S.done.attend ? S.done.attend.at : null, ITEMS.map(function (i) { return S.done[i.id] ? 1 : 0; }), doneCount()));
    if (all) Object.keys(S.history).forEach(function (k) { var x = S.history[k]; out.push(mk(k, x.at, x.cells || [], x.n || 0)); });
    return out;
  },
  flush: function (manual) {
    if (!AUTH || SYNC.flying) return Promise.resolve();
    if (!navigator.onLine) { SYNC.err = manual ? '인터넷에 연결되어 있지 않아요' : ''; if (manual) render(); return Promise.resolve(); }
    SYNC.flying = true; var sa = S.savedAt || 0, first = !SYNC.pushedAll;
    return api('/rest/v1/elite_state?on_conflict=user_id', { method: 'POST', prefer: 'resolution=merge-duplicates,return=minimal',
      body: { user_id: AUTH.uid, data: S, saved_at: sa, updated_at: new Date().toISOString() } })
      .then(function () { return api('/rest/v1/elite_daily?on_conflict=user_id,d', { method: 'POST', prefer: 'resolution=merge-duplicates,return=minimal', body: SYNC.rows(first) }); })
      .then(function () { SYNC.pushedAll = true; SYNC.last = Date.now(); SYNC.err = ''; SYNC.flying = false; SYNC.pending = (S.savedAt || 0) !== sa; if (SYNC.pending) SYNC.dirty(); if (route() === 'settings') render(); })
      .catch(function (er) { SYNC.flying = false; SYNC.err = '서버 저장 실패'; if (er && er.status === 401) SYNC.err = '다시 로그인이 필요해요'; if (route() === 'settings') render(); });
  },
  pull: function () {
    return api('/rest/v1/elite_state?user_id=eq.' + AUTH.uid + '&select=data,saved_at').then(function (rows) {
      var r = rows && rows[0];
      if (r && r.data && r.saved_at > (S.savedAt || 0)) {
        try { localStorage.setItem(KEY, JSON.stringify(r.data)); } catch (e) { return; }
        location.reload(); return 'reload';
      }
      return SYNC.flush(false);
    });
  },
  start: function () {
    if (!AUTH || SYNC.started) return; SYNC.started = true;
    api('/rest/v1/elite_profiles?id=eq.' + AUTH.uid + '&select=name,role').then(function (rows) {
      SYNC.profile = (rows && rows[0]) || { name: '', role: 'planner' }; render();
    }).catch(function () { SYNC.profile = { name: '', role: 'planner' }; });
    SYNC.pull().catch(function () { SYNC.err = '서버에 연결하지 못했어요'; });
    window.addEventListener('online', function () { if (SYNC.pending) SYNC.flush(false); });
    document.addEventListener('visibilitychange', function () { if (document.hidden && SYNC.pending) SYNC.flush(false); });
  }
};
function loadTeam() {
  if (!isAdmin() || SYNC.teamBusy) return;
  SYNC.teamBusy = true; SYNC.teamErr = ''; var from = dstr(now() - 7 * 86400000), today = dstr(now());
  Promise.all([api('/rest/v1/elite_profiles?select=id,name,role&order=created_at'), api('/rest/v1/elite_daily?d=gte.' + from + '&select=user_id,d,attend_at,cells,done_count')]).then(function (res) {
    var by = {}; res[1].forEach(function (r) { (by[r.user_id] = by[r.user_id] || []).push(r); });
    SYNC.team = { list: res[0].map(function (p) {
      var rs = by[p.id] || [], t = rs.filter(function (r) { return r.d === today; })[0], c = ITEMS.map(function (x, i) { return t && t.cells && t.cells[i] ? 1 : 0; });
      return { n: p.name || '(이름 없음)', t: t && t.attend_at ? hm(new Date(t.attend_at).getTime()) : null, c: c, w: rs.length ? Math.round(rs.reduce(function (a, r) { return a + r.done_count; }, 0) / rs.length * 10) / 10 : 0 };
    }) };
    SYNC.teamBusy = false; if (route() === 'admin') render();
  }).catch(function (er) { SYNC.teamBusy = false; SYNC.teamErr = '불러오지 못했어요: ' + er.message; SYNC.team = SYNC.team || { list: [] }; if (route() === 'admin') render(); });
}
