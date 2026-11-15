# 내키면

**계획 없어도, 마음 가는 곳으로.** 현재 위치와 지금 하고 싶은 일에서 출발해 갈 곳과 해 볼 활동을 고르고, 확인한 목적지를 Pleos 내비로 전달하는 차량용 즉흥 외출 앱입니다.

`com.naekimyeon.pleos` · Kotlin · Jetpack Compose · Android Automotive / Pleos Connect

이 브랜치는 제출 후 개선하는 **0.3.0-preview**입니다. 제출 기준은 [`f722a0b`](https://github.com/dev-whseol/naekimyeon-pleos/tree/f722a0b8dd7d0e3d373289ccdc61fbb764a779c9)와 태그 `submission-2026-11-15`로 보존하며 기존 제출 APK는 변경하지 않습니다.

## 이번 개선

- **실제 주변 찾기**: 명시적인 위치 사용 선택 후, 등록된 서울숲·월드컵공원·서울대공원 중 직선 반경 50 km 안의 장소를 찾습니다. 전국 실시간 검색은 아닙니다.
- **샘플과 분리**: 위치 거절·지연·미지원 지역은 이유와 재시도를 제공합니다. `샘플 둘러보기`를 직접 골랐을 때만 기존 예시를 사용합니다.
- **정보 근거 표시**: 실제 장소의 주소·출처·확인일·직선거리와 미확인 주차 정보를 구분합니다. 대표 좌표이므로 차량 진입로는 내비에서 확인해야 합니다.
- **시간 조건 유지**: 미정·제한 없음·정해진 시간을 구별합니다. 이동 시간을 모르면 체류 시간과 왕복 가능 여부를 만들어 내지 않습니다.
- **주행 흐름 유지**: 알려진 D에서는 한 곳 요약과 명시적 안내를 제공합니다. 권한 팝업·글자 입력·상세 비교는 정차 중에 사용합니다.

현재 위치와 마음 원문은 메모리에서만 사용합니다. 백그라운드 위치 권한, 이동 이력 저장, 계정, 광고·분석 SDK, 서버 API 키는 없습니다. [데이터 출처와 제한](docs/DATA_SOURCES.md), [보안](docs/SECURITY.md)

## 실행 방법

Android Studio에서 열고 JDK 21·Android SDK Platform 36을 준비합니다. SDK 경로는 로컬 `local.properties`의 `sdk.dir` 또는 Android Studio 설정으로 지정합니다. 최초 의존성 다운로드에는 인터넷이 필요합니다.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testReleaseUnitTest :app:lintDebug
adb -s <에뮬레이터-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <에뮬레이터-serial> shell am start -n com.naekimyeon.pleos/.MainActivity
```

기존 앱과 서명이 다르면 별도 테스트 AVD를 사용합니다. 모든 기기 명령에는 실제 테스트 에뮬레이터의 serial을 지정합니다. release 서명은 [빌드 안내](docs/BUILD_RELEASE.md)를 따릅니다. 서명 키와 비밀번호는 저장소에 포함하지 않습니다.

## 테스트 환경과 필요한 설정

| 항목 | 기준 |
| --- | --- |
| Pleos Connect 에뮬레이터 | 공식 system image **2.1.0**, Android 14 / API 34, x86_64 |
| 화면 규격 | 전체 **2560 × 1440 px**, 160 dpi. 작은 앱 창·주행 화면 별도 시험 |
| 빌드 | JDK 21 / Gradle 8.13 / AGP 8.13.1 / Kotlin 2.2.21 |
| SDK | compile 36 / target 34 / min 28, JVM target 17 |
| 내비 | NaviHelper **3.0.5**, 사용자 확인 후 경로 요청·확인·실패 구분 |
| 위치 | Android 전경 위치. 대략적 위치·신선도·오차·취소·권한 거절 처리 |
| 외부 API 키 | 현재 버전에 불필요. 검토한 장소 자료를 앱에 포함 |
| Gleo | Intent·입력 검증·콜백 구현. 공식 승인·실제 음성 서비스 호출은 미검증 |
| 실차 / App Market | 미검증 / 미승인 |

[0.3 검증 기록](docs/UPGRADE_TESTS.md)에 새 빌드 결과를 기록합니다. 0.2의 통과 수를 새 버전의 결과로 재사용하지 않습니다. Android 위치 제공자의 동작을 실차 Pleos FusedLocation SDK 지원 보장으로 해석하지 않습니다.

이번 버전에서 **JVM 60개와 Pleos 기기 45개** 시험이 통과했습니다. release 빌드와 lintDebug도 통과했습니다. 에뮬레이터의 주입 위치 시험이며 실차 GPS 정확도 검증은 아닙니다.

## APK와 시연

- 새 개발 APK: [`apk/preview/naekimyeon-0.3.0-preview-release.apk`](apk/preview/naekimyeon-0.3.0-preview-release.apk), **0.3.0-preview**, 22,453,683 bytes
- 개발 APK SHA-256: `6ddadc21dd2248d7827cc7079947fbaef79aeb6cf2d069baf5b3edc7e1e57c64`
- 제출 APK: [`apk/설우현_내키면_release.apk`](apk/설우현_내키면_release.apk), **0.2.0-demo**
- 제출 APK SHA-256: `3d96e44af2b297713e7cd15a1c42a038c9c206c9819ffca147e3423e231935a6`
- 제출 당시 [정차 영상](demo/naekimyeon-demo.mp4), [주행 영상](demo/naekimyeon-driving.mp4)

위 영상은 제출 당시 샘플 흐름이며 새 위치 기능의 시연이 아닙니다. [제출 기준 기록](docs/SUBMISSION_2026_11_15.md)

0.3: [실제 장소 추천 화면](demo/screenshots/preview-0.3/nearby-results.png) · [D 상태 125초 유지 확인](demo/screenshots/preview-0.3/driving-after-125s.png) · [D 화면 기록 15초](demo/naekimyeon-0.3-driving-state.mp4). 에뮬레이터 시험 위치이며 영상은 주행 화면 유지 기록입니다.

## 설계와 다음 작업

[제품 원안](docs/PRODUCT.md) · [개선 아고라 검토](docs/IMPROVEMENT_REVIEW.md) · [개발 순서](docs/ROADMAP.md) · [구조](docs/ARCHITECTURE.md) · [Pleos 연동](docs/PLEOS_INTEGRATION.md) · [데이터](docs/DATA_SOURCES.md) · [자산 출처](docs/ASSETS.md)

개인 신청서·연락처·서명·비밀키·내부 회의 원문은 공개 저장소에 포함하지 않습니다. 장소 범위 확장, 실제 차량 진입점·이동 시간·영업·주차·실내 후보는 후속 구현이 필요합니다.
