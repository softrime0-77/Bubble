# Bubble 냥햄대전 🐱🫧🐹

고양이와 햄스터가 비눗방울 속에서 겨루는 2인 턴제 안드로이드 게임입니다.
앱을 켜면 대기방(로비)이 나오고, **랜덤 매칭**으로 익명의 상대와 1:1 대결을 합니다. 이기면 점수가 올라가요.

| 로그인 | 로비 | 게임 |
|---|---|---|
| <img src="docs/login.png" width="240"> | <img src="docs/lobby.png" width="240"> | <img src="docs/game.png" width="240"> |

## 게임 규칙

- 고양이와 햄스터가 각자 방울(세트) 2개에 1마리씩 들고 시작합니다. 고양이가 먼저 둡니다.
- 내 차례에는 살아있는 내 방울 하나를 고르고, 그 방울의 마리 수만큼
  - ⚔️ **공격**: 상대의 살아있는 방울에 더하거나
  - 💪 **강화**: 내 다른 살아있는 방울에 더합니다.
- 방울이 **5마리 이상**이 되면 💥 펑 터져서 0마리가 됩니다.
- 0마리 방울은 공격도 강화도 할 수 없습니다. 살리는 방법은 **분할**뿐입니다.
  - ✂️ 한 방울이 짝수(2·4마리)이고 다른 방울이 0일 때 반씩 나눕니다 (4·0 → 2·2, 2·0 → 1·1).
- 내 방울 2개가 모두 0이 되면 집니다.
- 온라인 대전은 한 턴 30초. 시간을 넘기거나 나가면 패배합니다.
- 점수: 처음 1000점, 승리 +25점, 패배 -15점.

## 기능

- 구글 로그인: 점수·전적이 구글 계정에 저장 (상대에게는 닉네임만 보임)
- 로비: 닉네임(자동 생성, 변경 가능), 점수·전적, 랭킹 TOP 30, 로그아웃
- 랜덤 매칭: Firestore 대기열로 익명 상대와 자동 연결
- 온라인 대전: 실시간 동기화, 턴 타이머, 기권/시간초과 처리
- AI 연습: 쉬움 / 보통 / 어려움 (미니맥스 AI, 오프라인)
- 애니메이션: 숨쉬는 방울, 깜빡이는 캐릭터, 4마리 위험 떨림, 펑 터지는 파티클

## 기술

Kotlin · Jetpack Compose · Firebase (Google 로그인 + Cloud Firestore) · Credential Manager

```
app/src/main/java/com/bubble/game/
  game/GameLogic.kt        규칙 엔진 (순수 Kotlin, 단위 테스트 있음)
  game/Ai.kt               AI 상대
  data/OnlineRepository.kt 로그인 · 매칭 · 대전 · 점수 (Firestore)
  ui/                      화면, 캐릭터 그림, 방울 애니메이션
firestore.rules            Firestore 보안 규칙
```

## 온라인 대전 켜기 (Firebase 설정)

`google-services.json`이 없어도 앱은 빌드되고 AI 연습 모드로 동작합니다. 온라인 대전을 켜려면:

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트를 만듭니다.
2. **Android 앱 추가** → 패키지 이름 `com.bubble.game` → `google-services.json`을 내려받아 `app/` 폴더에 넣습니다.
3. **Authentication → 로그인 방법 → Google** 을 사용 설정합니다.
   그리고 프로젝트 설정 → 내 앱 → **디지털 지문 추가**에 서명 키의 SHA-1을 등록한 뒤, `google-services.json`을 다시 내려받습니다.
4. **Firestore Database** 를 만들고, **규칙** 탭에 이 저장소의 `firestore.rules` 내용을 붙여넣고 게시합니다.
5. 다시 빌드합니다.

## 서명 키 (구글 로그인에 필요)

구글 로그인은 Firebase에 등록한 SHA-1과 같은 키로 서명한 APK에서만 동작합니다.
`keystore/bubble.jks`가 있고 비밀번호가 `local.properties`의 `bubbleKeystorePassword`(또는 환경변수 `BUBBLE_KEYSTORE_PASSWORD`)에 있으면 그 키로 서명합니다.
키와 비밀번호는 저장소에 올리지 않습니다. 잃어버리면 같은 키로 다시 서명할 수 없으니 안전한 곳에 보관하세요.

## APK 받기 / 빌드

- **GitHub Actions**: `main`에 푸시할 때마다 APK가 빌드됩니다. 저장소의 Actions 탭 → 최근 실행 → `bubble-debug-apk`를 내려받아 폰에 설치하세요.
  온라인 대전이 켜진 APK를 원하면 저장소 Settings → Secrets and variables → Actions에 다음을 추가하세요.
  - `GOOGLE_SERVICES_JSON`: 파일 내용 그대로
  - `BUBBLE_KEYSTORE_BASE64`: `keystore/bubble.jks`를 base64로 인코딩한 값
  - `BUBBLE_KEYSTORE_PASSWORD`: 서명 키 비밀번호
- **직접 빌드**: Android Studio로 열어서 실행하거나 `./gradlew assembleDebug`.
