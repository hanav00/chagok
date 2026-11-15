# 차곡 (Chagok)

업무용 차량 운행기록부를 차가 직접 쓰는 Pleos Connect 앱.

시동을 걸고 달리고 주차하면, 앱 화면을 열지 않아도 국세청 운행기록부 서식(국세청고시 별지 서식) 한 줄이 채워진다.
출발·도착은 기어로 나누고, 주행 전·후 계기판 값은 차에서 읽는다. 폰도, 따로 다는 장치도 필요 없다.
운전자가 할 일은 업무가 아닌 운행만 "개인"으로 바꾸는 것이다. 화면 1탭이나 말 한마디("이번 운행 개인 용무로 기록해줘")면 된다.

- **Pleos Connect SDK 4종:** Vehicle(누적거리·연료·배터리·동승석·차대번호), NaviHelper(목적지·현재 위치·즐겨찾기), TextToSpeech, SpeechToText
- **AAOS 표준 Car API:** 기어(`GEAR_SELECTION`). Pleos 문서가 기어 같은 표준 차량 값은 SDK 가 아니라 표준 API 로 읽게 한다
- **Gleo 음성 명령:** Pleos App Integration(`assets/schema.json`)으로 주행 중에도 말로 기록을 고친다
- **앱:** Kotlin, Jetpack Compose, Room, DataStore, 포그라운드 서비스. 단위 테스트 657개(가짜 차로 앱 전체를 돌리는 시나리오 테스트 포함)

## 차량 데이터로 자동화한 것

| 자동으로 하는 일 | 쓰는 차량 신호·API | 코드 |
| --- | --- | --- |
| 시동이 걸리면 화면 없이 기록을 시작한다 | 부팅 신호 → 포그라운드 서비스가 차 신호를 구독 | [BootReceiver](app/src/main/java/kr/chagok/recorder/BootReceiver.kt), [RecorderService](app/src/main/java/kr/chagok/recorder/RecorderService.kt) |
| 운행을 나눈다: P → D 출발, D → P 주차 | AAOS `CarPropertyManager` `GEAR_SELECTION` | [AaosGearSource](app/src/main/java/kr/chagok/car/AaosGearSource.kt), [TripRecorder](app/src/main/java/kr/chagok/recorder/TripRecorder.kt) |
| 서식 ⑤⑥ 주행 전·후 계기판을 채운다 | `Vehicle.getOdometer()` `registerDistanceDriven` · `getCurrentDistanceDriven` | [TripRecorder](app/src/main/java/kr/chagok/recorder/TripRecorder.kt) |
| 앱이 꺼진 사이 달린 거리를 "기록 안 된 주행 ○km"로 드러낸다 | 앞 운행 도착 계기판 ≠ 다음 운행 출발 계기판 | [TripRules](app/src/main/java/kr/chagok/domain/TripRules.kt) |
| 도착지와 비고(방문지)를 채운다 | `NaviHelper` `onRouteStarted` → `getDestinationInfo`, `onDestinationArrived`, 주차한 자리는 `getCurrentLocationInfo` | [PleosNaviSource](app/src/main/java/kr/chagok/car/PleosNaviSource.kt) |
| 집 ↔ 회사 운행을 출퇴근으로 나눈다 | `NaviHelper.getBookmarkInfo` (`HOME` · `OFFICE`) | [PlaceMatcher](app/src/main/java/kr/chagok/domain/PlaceMatcher.kt) |
| 주유·충전을 알아채 남긴다 (달리며 오른 배터리는 회생 제동으로 거른다) | `Vehicle.getFuel()` `registerFuelPercent`, `getEvBattery()` `registerEvBatteryStateOfCharge` | [RefuelRules](app/src/main/java/kr/chagok/domain/RefuelRules.kt), [RefuelWatcher](app/src/main/java/kr/chagok/recorder/RefuelWatcher.kt) |
| 다시 가는 곳의 지난 메모를 혼자 탔을 때만 읽는다 | `Vehicle.getSeat()` `registerSeatOccupancy` | [TripRecorder](app/src/main/java/kr/chagok/recorder/TripRecorder.kt), [VisitHistory](app/src/main/java/kr/chagok/domain/VisitHistory.kt) |
| 시동·출퇴근·도착·주유 때 어떻게 기록되는지 한 문장 말한다 | `TextToSpeech` (온디바이스) | [TripVoice](app/src/main/java/kr/chagok/recorder/TripVoice.kt) |
| 방문 목적·분류·사람·주유 금액을 말로 받는다 | `SpeechToText` (온디바이스) | [TripDictation](app/src/main/java/kr/chagok/recorder/TripDictation.kt), [RefuelDictation](app/src/main/java/kr/chagok/recorder/RefuelDictation.kt) |
| 주행 중 Gleo 에게 한 말로 분류·방문 목적·사람을 고친다 | Gleo App Integration: 함수 3개 → 브로드캐스트 → 6초 안에 회신 | [gleo/](app/src/main/java/kr/chagok/gleo) |
| 내보내기·백업 묶음이 어느 차 것인지 가린다 | `Vehicle.getCarInfo().getVINInfo` (해시만 저장) | [RecordBundle](app/src/main/java/kr/chagok/domain/RecordBundle.kt) |

FusedLocation 은 쓰지 않는다. 위험 권한을 받지 않고 NaviHelper 현재 위치(주소·좌표)로 충분해서다. 개인 운행은 위치를 읽지 않는다.

## 화면과 기능

- **기록부:** 국세청 서식 칸 그대로 월별 표, 사업연도 합계와 업무사용비율(⑪⑫⑬), 절세 계산
- **분류:** 모든 운행이 기본 분류(업무·개인)로 분류된 채 쌓여 "미선택"이 생기지 않는다.
  바꾸는 방법은 셋이다. 출발 전에 다음 출발을 고르거나, 주차 후 한 번 누르거나, 말한다
- **공용차:** 기록 단위가 차라서 몇 명이 타도 기록부 한 권이다. 누가 탔는지는 1탭이나 "박정훈이 탔어"로 고른다.
  관리자 PIN 으로 지난 기록을 고치면 바꾸기 전·후가 수정 기록으로 남는다
- **주행 중 화면:** P단이 아닐 때는 이번 운행 분류 한 줄만 보인다. 누를 것도, 바뀌는 숫자나 움직임도 없고 입력은 음성으로만 받는다
- **다시 가는 곳:** 방문 목적을 남긴 곳을 내비로 다시 찍으면 "○○상사, 3주 만이에요. 지난번 메모, 신제품 견적 요청."
- **주유·충전:** 잔량이 오르면 저절로 남기고, 금액은 홈에서 말로 받는다("6만 3천 원"). 모르면 "모름"으로 넘긴다
- **운행 블록:** 운행 하나를 분류 색 블록 하나로 쌓아 보인다. 주차하면 방금 운행 블록이 오늘 탑 위로 떨어지고,
  개인으로 바꾸면 뒤집히고, 기록 안 된 주행은 점선 빈칸으로 남는다. 움직임은 모두 주차 중 화면에서만 나온다
- **홈 (넓은 창):** 스크롤 없는 한 화면. 왼쪽(운전석 쪽)에 다음 출발과 할 일, 오른쪽에 올해 업무사용비율과 이번 달 블록 탑

## 개발·심사 환경

- **에뮬레이터:** Pleos Connect 에뮬레이터 v2.1.0 (Android 14 · API 34)
  - 시스템 이미지 `system-images;android-34;pleos_car_emulator;arm64-v8a`, 기기 정의 `Pleos_Connect_v2_0`
  - 화면 규격 2560×1440, 160dpi
  - 실제 차량 없이 에뮬레이터만으로 개발·시험했다. 차량용 안드로이드라 앱은 사용자 10번으로 띄운다
- **빌드:** Android API 28 이상, JDK 17, Gradle 8.14 (Wrapper 포함)
- **Pleos Connect SDK:** Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech·SpeechToText 3.3.1
- **권한:** `CAR_DRIVE`(누적거리), `CAR_INFO`(차대번호), `CAR_ENERGY`(연료·배터리), `CAR_SEATS`(동승석 착석), `NAVI_ROUTE`, TTS·STT, AAOS `CAR_POWERTRAIN`(기어). 모두 normal 이고 signature·privileged 권한은 쓰지 않는다
- **비밀값:** 저장소에 넣지 않는다. 음성은 온디바이스 모드라 키 없이 돈다. 릴리스 서명 값(`release.*`)만 `local.properties` 에서 읽고,
  이 파일은 git 에 올리지 않는다. 없으면 서명 없는 릴리스 APK 가 나온다

## 심사 환경에서 바로 해 보기

Pleos Connect 에뮬레이터를 켠 상태에서 저장소 맨 위 폴더(이 README 가 있는 곳)에서 실행한다.
빌드에는 Android SDK 가 필요하다(`ANDROID_HOME` 또는 `local.properties` 의 `sdk.dir`). Pleos SDK 는 공개 Maven 저장소에서 받는다.

1. 빌드하고 설치한 뒤 한 번 연다. 기록 서비스가 뜨고, 그다음부터는 부팅(시동) 신호로 화면 없이 뜬다.

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb shell am start --user 10 -n kr.chagok/.ui.MainActivity
```

2. 계기판을 샘플 끝에 맞추고, 올해 쓰던 공용차 기록을 시연 샘플로 넣는다.

```bash
tools/demo-drive.sh setup
```

```bash
adb shell am broadcast --user 10 -a kr.chagok.demo.SEED -n kr.chagok/.demo.DemoSeedReceiver --ez solo true
```

3. 12km 를 달리고 주차한다. 홈에 방금 운행이 뜨고, 기록부 탭에 서식 한 줄이 늘어난다.

```bash
tools/demo-drive.sh trip 12
```

빌드하지 않고 `apk/` 폴더의 릴리스 APK 를 바로 설치해도 된다. 다만 시연 샘플은 디버그 빌드에만 있어서, 릴리스 APK 는 첫 실행 화면부터 시작한다.
디버그 빌드와 서명이 달라서, 디버그 빌드를 이미 설치했다면 먼저 지운다(`adb uninstall kr.chagok`).

```bash
adb install -r -g apk/KimMinyoung_Chagok_release.apk
```

### 에뮬레이터에서 주행 흉내 내기

- `tools/demo-drive.sh` 는 Virtual Console HTTP API(포트 8088)로 기어·누적거리·배터리·동승석을 바꾼다 (`adb forward` 는 스크립트가 한다).
  `bash`·`curl`·`python3` 를 쓴다.
  스크립트 없이 Virtual Console 화면에서 기어 P → D(출발) → 누적거리 올리기 → P(주차) 로 해도 같다.
- 기어를 바꿔도 앱이 반응하지 않으면 에뮬레이터를 콜드 부팅한다.
- 충전은 Virtual Console 의 Battery SOC 를 5%p 이상 올리면 된다 (에뮬레이터는 전기차라 연료 잔량은 없다).
- 동승석 착석은 에뮬레이터에서 거꾸로 보인다. SDK 는 원시 값 1 을 "앉음"으로 바꾸는데 Virtual Console 은 "비었음"을 1 로 보낸다.
  지난 방문 메모를 끝까지 들으려면 Virtual Console 에서 동승석 셋을 "Occupied"로 둔다 (`setup` 이 그렇게 맞춘다).

## 시연 샘플 (디버그 빌드만)

올해 쓰던 공용차 상태를 넣는다: 사용자 두 명, 관리자 PIN `2580`(잠김). 1~9월은 달마다 합계만, 10월은 운행 하나하나다.
11월부터 넣는 날 전날까지는 요일로 채운다(평일 출퇴근·거래처 왕복, 토요일 개인 왕복). 언제 넣어도 홈의 이번 달 탑이 차 있다.
앱을 설치하고 한 번 연 뒤, 기록이 없을 때만 들어간다. 홈 시작 가이드는 다 한 것으로 들어간다. Virtual Console 누적거리를 23,418km 이상으로 맞춰 두면 샘플 끝과 이어진다.
다시 넣으려면 앱 데이터를 지운다(`adb shell pm clear --user 10 kr.chagok`).

```bash
adb shell am broadcast --user 10 -a kr.chagok.demo.SEED -n kr.chagok/.demo.DemoSeedReceiver
```

`--ez solo true` 를 붙이면 박정훈 혼자 타는 차로 넣는다(사용자 한 명, PIN 없음). 요일과 상관없이 지금 시각 전에 끝난 오늘 운행 세 건(출근·거래처 왕복)도 넣어
홈 "오늘"이 차 있다. 집은 에뮬레이터 차가 서 있는 자리, 회사는 내비 즐겨찾기 회사로 맞춰(없으면 지어낸 곳) 시연 운행도
출퇴근으로 잡힌다. 첫 실행을 마친 뒤에 넣어도 되고, 설정은 샘플 값으로 덮어쓴다. 시연 영상은 이쪽으로 찍는다.

```bash
adb shell am broadcast --user 10 -a kr.chagok.demo.SEED -n kr.chagok/.demo.DemoSeedReceiver --ez solo true
```

시동 안내("업무로 기록됩니다")는 부팅 때만 나와서 시연 중에는 `ignition` 으로 흉내 낸다(`kr.chagok.demo.IGNITION`).

```bash
tools/demo-drive.sh ignition
```

저장 실패 알림("저장하지 못했어요 · 다시 하기")을 보려면 운행 고치기 저장을 일부러 막는다. 끄기 전까지 계속 실패한다.

```bash
adb shell am broadcast --user 10 -a kr.chagok.demo.FAIL_WRITES --ez on true -n kr.chagok/.demo.DemoFailWritesReceiver
```

```bash
adb shell am broadcast --user 10 -a kr.chagok.demo.FAIL_WRITES --ez on false -n kr.chagok/.demo.DemoFailWritesReceiver
```

샘플 코드는 `app/src/debug/` 에만 있어 릴리스 APK 에는 들어가지 않는다.

## Gleo 음성 명령

앱을 설치하면 Gleo 가 `assets/schema.json` 을 읽어 함수 3개(분류·방문 목적·사람)를 등록한다.
에뮬레이터에서는 마이크 대신 시험 신호로 문장을 넣는다.

```bash
adb shell am broadcast --user 10 -a ai.umos.intent.action.ASR --es ASR "'차곡에 이번 운행 개인 용무로 기록해줘'"
```

```bash
adb logcat | grep -E 'AGENTCLASSIFIER|ACTIONPLANNER \| OUTPUT|GleoAction|doSpeak'
```

- `AGENTCLASSIFIER` 는 Gleo 가 먼저 고른 갈래(`on-device_call_app_action_agent` 여야 앱으로 온다), `ACTIONPLANNER | OUTPUT` 은 Gleo 가 고른 함수, `GleoActionReceiver` 는 앱이 받고 회신한 것, `doSpeak` 는 Gleo 가 한 말이다.
  앱 로그에는 함수 이름과 인자 이름만 남긴다 (방문 목적·사람 이름은 남기지 않는다).
- 앞에 "차곡에"를 붙여 말한다. 없으면 Gleo 를 막 켠 첫 마디("이번 운행 개인 용무로 기록해줘")가 리마인더·잡담으로 빠진다.
  한 번 차곡 함수가 불린 뒤에는 앱 이름 없이 말해도 이어서 알아듣는다. 시험할 때 Gleo 를 새로 띄우려면
  `adb shell am force-stop --user 10 ai.umos.ambientai`.
- 주행 중이면 이번 운행을 고친다(`tools/demo-drive.sh d` 로 달리는 상태를 만든 뒤 말한다). 주차 중이면 시동 뒤 아직 안 달렸을 때는 다음 출발을,
  달린 적이 있으면 "방금 끝난 운행인지, 다음 운행인지"를 되묻는다.
- 음성으로는 운전자 권한만 쓴다. 브로드캐스트를 보낸 앱을 확인할 방법이 없어서다.

## 테스트

에뮬레이터 없이 JVM 에서 돈다 (Room·리소스는 Robolectric).

```bash
./gradlew :app:testDebugUnitTest
```

결과는 `app/build/reports/tests/testDebugUnitTest/index.html`. 테스트 이름이 "상황 → 결과" 한국어 문장이라 보고서가 곧 동작 목록이다.
시연 흐름만 보려면 `--tests 'kr.chagok.scenario.*'`.

```
app/src/test/java/kr/chagok/
  testing/      TestApp(가짜 차·내비·음성으로 앱 전체를 돌린다, 메모리 DB, 껐다 켜기), ViewModelTestRule,
                앞으로 돌리는 시계, 운행 견본, 시연 샘플 운행
  scenario/     사용자가 하는 순서 그대로: 가짜 차로 출발·주차하며 앱 전체를 돈다. 흐름 하나에 클래스 하나
  domain/       규칙: 분류, 빠진 거리, 합계·업무사용비율, 절세 계산, 첫 실행 선택, 지난 방문, 주유 알아채기, 금액 읽기
  data/         저장소: 분류 바꾸기·되돌리기, 관리자 수정 기록, 진행 중 운행, 사람, 설정, PIN·복구키, 마이그레이션
  recorder/     기록기 가장자리 경우(늦은 계기판, 기어 UNKNOWN, 내비 목적지), 도착 음성 문장
  gleo/         Gleo 약속: 액션 이름·인자·회신 JSON
  ui/           화면마다 ViewModel 이 보여 주는 값, 표시 형식(km·만 원·날짜)
```

- 실제 앱과 테스트가 같은 `AppContainer` 를 쓴다. 다른 것은 바깥(`AppEnvironment`: DB·DataStore·차(Pleos SDK·AAOS Car API)·시계)뿐이다.
- 화면 테스트는 `ViewModelTestRule` 을 쓴다. ViewModel 은 실제 앱처럼 메인 스레드 하나에서 돌고, 누르는 것은 `rule.onMain { … }` 으로 부른다.
  시간이 걸리는 동작(방금 운행 "다시 보기" 6초)은 가상 시간이라 `rule.advanceTimeBy` 로 앞으로 돌린다.

## 구조

계층이 한 방향으로만 의존한다: `ui → recorder → data · car → domain`. `domain` 은 안드로이드를 모른다.

```
 Pleos SDK ─┐
 AAOS 기어 ─┴▶ car/ ─▶ recorder/TripRecorder ─▶ data/ (Room · DataStore) ◀─ ui/*ViewModel ◀─ ui/* (Compose)
                         (포그라운드 서비스)          domain/ 규칙은 모두가 쓴다
```

```
./
  apk/                          릴리스 APK (빌드 없이 설치)
  tools/demo-drive.sh           에뮬레이터 주행 흉내 (기어·누적거리·배터리·동승석)
  gradle/libs.versions.toml     의존성 버전
  app/schemas/                  Room 스키마 (마이그레이션 비교용)
  app/src/main/assets/          schema.json(Gleo 에 알려 주는 함수 3개), 글꼴 라이선스
  app/src/main/res/font/        Asta Sans (Pleos 시스템 글꼴)
  app/src/debug/.../demo/       디버그 전용: 시연 샘플 넣기, 저장 실패 흉내
  app/src/main/java/kr/chagok/
    ChagokApplication.kt        앱 시작: AppContainer 를 만든다
    AppContainer.kt             하나씩만 있는 것들을 만들고 잇는다 (수동 의존성 주입)
    AppEnvironment.kt           바깥과 닿는 것: DB·DataStore·차(Pleos SDK·AAOS Car API)·시계 (테스트는 가짜로 바꾼다)
    Today.kt                    화면이 쓰는 오늘 날짜. 자정이 지나면 바뀐다
    common/                     runSuspendCatching (코루틴 취소는 잡지 않는 runCatching)

    domain/                     규칙 (안드로이드 없음)
      model/                    운행·분류·분류 근거·사람·다음 출발·진행 중 운행·관리자 상태·합계·설정·주유
      TripRules.kt              도착 때 분류, 빠진 거리, 합계·비율, 하루 요약, 처음 간 곳
      PlaceMatcher.kt           같은 장소인지 (poiId, 없으면 반경 300m), 집↔회사 운행인지
      PersonMatcher.kt          말한 이름으로 등록된 사람 찾기 (Gleo·마이크 같이 씀)
      SpokenEdit.kt             마이크 한마디 나누기: 정해 둔 짧은 말은 분류·사람, 나머지는 방문 목적
      VisitHistory.kt           다시 가는 곳의 마지막 방문 메모와 마지막으로 간 날
      RefuelRules.kt            잔량 값 하나로 주유·충전 알아채기 (기준점, 회생 제동 거르기)
      SpokenAmount.kt           말한 금액 읽기 ("오만 사천 원" → 54,000)
      FiscalYear.kt             사업연도
      FirstRunChoice.kt         첫 실행에서 고른 값 → 적용될 설정
      VehicleRules.kt           서식 머리 값: 빠진 칸, 사업자등록번호 형식, 저장 전 다듬기
      RecordBundle.kt           내보내기·백업 묶음 머리: 형식 버전·차대번호 해시·설치 식별값·기간·건수
      ChagokConfig.kt           규칙 숫자 (빠진 거리 기준, 안내 기간, PIN 자리 수 …)
      tax/                      절세 계산과 세율·한도

    data/                       저장
      db/                       Room: 운행·진행 중 운행·사람·관리자 수정 기록·주유·잔량 기준점, 마이그레이션
      TripRepository.kt         운행 저장, 분류 바꾸기·되돌리기·확정, 사람 고르기, 관리자 수정 기록
      RefuelRepository.kt       주유·충전 남기기(같은 자리면 한 건으로), 금액·모름, 잔량 기준점
      PeopleRepository.kt       이 차를 쓰는 사람: 더하기·고치기·순서·지우기(지난 운행에 나오면 숨기기)
      SettingsRepository.kt     기본 분류·누가 쓰는 차·음성 안내·집·회사·차량 정보 (DataStore)
      NextDepartureStore.kt     다음 출발 분류·누가 타나요 (메모리, 운행마다 비운다)
      StoreFailures.kt          쓰기 실패는 로그 + 전파, 읽기 실패는 로그 + 기본값
      admin/                    관리자 PIN·복구키 (솔트 해시만 저장), 틀린 횟수, 잠금

    car/                        차 신호 감싸기 (인터페이스 + Pleos SDK·AAOS Car API 구현)
      VehicleSource.kt          누적거리·기어·동승석 착석·연료·배터리 잔량·차대번호
      AaosGearSource.kt         기어만 AAOS 표준 Car API(GEAR_SELECTION)로 받는다
      NaviSource.kt             목적지·현재 위치·즐겨찾기 집·회사
      Voice.kt                  음성 안내(TTS)·한마디 듣기(STT)

    recorder/                   백그라운드 기록
      TripRecorder.kt           출발·주차, 계기판 저장, 빠진 주행, 다시 켜졌을 때 복구, 주행 중 음성으로 고치기
      TripVoice.kt              음성 안내 문장: 시동·출퇴근 경로·지난 방문 메모·도착·주유·충전
      TripDictation.kt          마이크 받아쓰기: 방문 목적 남기기, 말로 분류·사람 고치기
      RefuelWatcher.kt          잔량이 오르면 주유·충전으로 남기고 한 번 말하기
      RefuelDictation.kt        주유·충전 금액 받아쓰기
      RecorderService.kt        포그라운드 서비스
      BootReceiver.kt           부팅(시동) 때 서비스 시작과 시동 안내

    gleo/                       Gleo 음성 명령 (Gleo 가 없는 차에서도 앱은 그대로 동작)
      GleoProtocol.kt           함수·인자 이름, 회신 JSON (schema.json·AndroidManifest.xml 과 맞춘다)
      GleoCommands.kt           분류·방문 목적·사람 바꾸기, 어느 운행을 고칠지 정하기
      GleoActionReceiver.kt     브로드캐스트 받기 → 처리 → 6초 안에 회신

    ui/
      MainActivity.kt
      ViewModelSupport.kt       ViewModel 팩토리, 알림 큐, 저장 실패 시 다시 하기
      theme/                    색·글자 크기·모서리·간격 토큰, 화면 배율, 글꼴
      components/               공통 부품 (섹션·버튼·토글·이름표+값 칸·얼굴 원·시트·입력 칸·알림 줄·운행 블록,
                                넓은 창 두 줄 배치 WideGrid, 한 자리에서 다른 자리로 날아가기 Flight,
                                한 자씩 흩어지는 글자 ScatterText …)
      format/                   km·금액·퍼센트·날짜 표시
      shell/                    앱 껍데기: 헤더·탭(넓은 창은 헤더 안)·주행 중 화면·시트 띄우기
      home/                     홈: 방금 운행·분류 대기·미지정·주유 금액·다음 출발·오늘 요약·업무사용비율·이번 달 블록,
                                방금 운행의 계기판 줄·서식 한 줄(RecentForm, 계기판 숫자가 ⑤⑥ 칸으로 옮겨 간다),
                                넓은 창 오른쪽 비율·이번 달 탑(HomeStage), 자세히 시트(SummarySheet)
      table/                    기록부 서식 표, 넓은 창 첫 화면(TableOverview), 운행 상세 시트
      export/                   내보내기 (이 달 기록부 / 올해 합계), 탑 → 기록부 → QR 그림
      simulator/                절세 계산, 넓은 창 화면(SimulatorWide: 두 막대의 차이가 떨어져 나와 큰 숫자로)과
                                계산 자세히 시트
      settings/                 설정. 넓은 창(SettingsWide)은 항목마다 한 줄, 누르면 그 항목만 시트로
      people/                   사람 시트 (성명·부서, 지우기·숨기기)
      vehicle/                  차량·사업자 시트 (서식 머리 값)
      admin/                    관리자 PIN 시트
      onboarding/               첫 실행: 시작 → 누가 쓰나요·사람·관리자 → 기본 분류 → 집·회사
                                → 이렇게 기록돼요 → 말해 보기 → 정리. 단계 묶음마다 파일 하나
      guide/                    말해 보기 연습(ChangePractice), 홈 시작 가이드, 이렇게 기록돼요
                                (견본 운행 하나를 장면별로 넘기는 무대 HowStage. 설정 "튜토리얼 다시 보기"도 같이 쓴다)
```

## 설계 결정

- **화면 배율:** 디자인 기준 캔버스(1920×1080)의 앱 창 620·1252px 이 차 화면(2560×1440, 160dpi) 앱 창 853·1682dp 와
  정확히 4/3 배다. 화면 코드는 기준 숫자를 그대로 쓰고 `ui/theme/Theme.kt` 의 `UI_SCALE` 하나로 키운다.
- **간격:** 여백은 `Spacing` 척도(4·8·12·16·20·24·32·48)만 쓴다. 척도 밖 값은 둘뿐이다:
  묶음 상자 안쪽 6(모서리 동심원)과 알림 줄 여백 18(Pleos UI 가이드 24dp ÷ 4/3).
- **글꼴:** Pleos 시스템 글꼴 Asta Sans 를 앱에 넣었다. `TextStyle(...)` 을 새로 만들면 테마 글꼴이 빠지므로
  `LocalTextStyle.current.copy(...)` 를 쓰거나 `fontFamily = AppFontFamily` 를 넣는다.
- **넓게 ↔ 분할:** 앱이 창 크기를 바꾸지 않는다. 창이 넓으면 기록부가 서식 전체 칸을 보여 주고,
  분할 화면에서는 "서식 전체 보기"로 가로 스크롤한다.
- **위치:** 위험 권한인 FusedLocation 대신 NaviHelper `getCurrentLocationInfo`(주소·좌표)만 쓴다. 개인 운행은 위치를 읽지 않는다.
  - 경로가 없을 때 `getDestinationInfo` 가 주는 빈 목적지(좌표 0,0)는 목적지가 없는 것으로 친다.
  - 출발 때 위치 응답이 늦으면 마지막으로 받은 위치를 쓴다 (차가 그 자리에 서 있었다).
    주차한 자리는 이번 요청의 답만 쓰고, 4초 안에 안 오면 도착지를 비운다 (마지막 위치는 대개 출발 자리다).
  - 주차하면 운행부터 저장하고 주소는 뒤에 채운다. 기다리는 사이 꺼져도 운행은 남는다.
  - 주차 저장이 실패해도 그 운행은 메모리에서 내리고, 다음 차 이벤트 때 먼저 다시 옮긴다. 다음 운행이 앞 운행에 이어 붙지 않는다.
- **오늘 날짜:** 화면은 날짜를 `Today` 에서 받는다. 화면을 띄운 채 자정을 넘겨도 오늘 요약·배지·사업연도가 바뀐다.
- **다음 출발:** 운행이 끝나면 기본값·미지정으로 돌아간다. 고른 값이 엉뚱한 다음 운행에 남지 않게 하려는 것이다.
- **시트:** 대화상자 창 대신 앱 화면 안에 그린다. 차량용 화면에서 다른 앱 위로 창을 띄우지 않기 위해서다.
- **이름 없는 사람:** 빈 이름으로 저장한다. 화면에는 "나"·"이름 없음 2"로 보이고, 기록부 성명 칸에는
  "이름 입력 필요"가 나간다. 화면용 이름표가 서식에 찍히면 안 되기 때문이다.
- **사람 순서·지우기:** 차 안이라 끌어 옮기기 대신 "위로" 버튼을 쓴다. 맨 위가 기본 사용자다.
  지난 운행·수정 기록에 나오는 사람은 지우지 않고 숨긴다. 기록부 성명 칸이 비면 안 된다.
- **관리자 수정:** 관리자 PIN 을 푼 상태에서 고친 분류·사람은 바꾸기 전·후를 운행과 같은 트랜잭션에 남긴다.
  다시 하기는 처음 누른 때의 관리자 상태로 저장한다.
- **기록 식별값:** 운행·사람·수정 기록은 차 안 `id` 말고 전역 `uid`(UUID)도 갖는다. 백업 복원·여러 차 합치기에서
  같은 기록을 가리고, 가져오기는 이미 있는 `uid` 를 건너뛴다. 묶음 머리는 차대번호 해시 + 설치 식별값으로 차를 가린다.

## 아직 안 된 것

- 내보내기의 실제 QR·엑셀 생성 (지금은 자리 그림이고 화면에도 그렇게 밝힌다). 주유·충전 금액도 엑셀 유류비에 넣는다
- 주유·충전 금액을 숫자판으로 넣기 (지금은 말로만). 에뮬레이터에는 마이크가 없어 금액 말하기는 단위 테스트로만 확인했다
- 홈 탭 배지에 주유·충전 금액 할 일 수 더하기 (지금은 분류 대기·미지정만 센다)
- 사업연도 시작 월 고르기, 백업·복원
- 차종을 차대번호로 추정하기 (지금은 직접 넣는다)
- Gleo 로 주행 중에 말한 방문 목적을 주차할 때 붙이기 (지금은 주차한 뒤에만)
- 실차 확인: 시동 OFF 가 재부팅인지 대기인지, 시동마다 부팅 신호가 오는지, 동승석 착석 원시 값이 표준(비었음 1·앉음 2)인지
