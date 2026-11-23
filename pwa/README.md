# ELITE 루틴 체크 — PWA

- 원본: `pwa/src/admin.html` (기존 단일 HTML), 로그인·동기화 모듈: `pwa/src/sync.js`
- `python3 pwa/build.py` → `pwa/dist/index.html` 생성 (manifest, sw.js, icons는 dist에 고정 파일)
- 배포: main에 push하면 `.github/workflows/pwa-pages.yml` 이 GitHub Pages에 올림
- 서버: Supabase 프로젝트 ggesrslhgqtiwdxuyvnh, 테이블 elite_profiles / elite_state / elite_daily (RLS 적용)
- 관리자 지정: Supabase SQL에서 `update public.elite_profiles set role='admin' where id = (select id from auth.users where email='...');`
