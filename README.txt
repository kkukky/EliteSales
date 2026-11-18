ELITE 루틴 — 안드로이드 앱 프로젝트 (파일럿 v0.1)

[APK 받는 가장 쉬운 법: GitHub Actions]
GitHub 저장소의 Actions 탭 > Build APK > 완료된 실행 > Artifacts 에서 ELITE-Routine-debug-apk 를 내려받습니다.

[APK 만드는 법: Android Studio]
1) Android Studio(최신)를 설치하고 File > Open 으로 이 폴더(ELITE-Routine-Android)를 엽니다.
2) 처음 열면 Gradle 동기화가 끝날 때까지 기다립니다(인터넷 필요).
3) 메뉴 Build > Build Bundle(s) / APK(s) > Build APK(s).
4) 완료 알림의 "locate"를 누르면 app/build/outputs/apk/debug/app-debug.apk 가 있습니다.
5) 그 파일을 휴대폰으로 보내 설치합니다(설정에서 '출처를 알 수 없는 앱 설치' 허용).

[앱 구성]
- 첫 화면: 검정 배경 + ELITE Sales Group 로고 스플래시(약 2초) 후 루틴 화면
- 루틴 화면과 데이터는 app/src/main/assets/index.html 에 들어 있습니다.
- 기록은 휴대폰 앱 안에만 저장되고, 설정 탭의 '엑셀로 내보내기'는 '다운로드' 폴더에 저장됩니다.
