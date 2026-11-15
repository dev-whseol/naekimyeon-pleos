# 내키면

**계획 없어도, 마음 가는 곳으로.**

지금 기분과 하고 싶은 일에 맞춰 **갈 만한 장소와 도착해서 해볼 활동**을 제안하는 차량용 앱입니다. 후보를 직접 살펴보거나 ‘하나만 바로 추천받기’로 조건에 맞는 장소 하나를 확인할 수 있습니다. 시간이 정해져 있어도, 아직 몰라도, 제한이 없어도 시작할 수 있습니다.

`com.naekimyeon.pleos` · Kotlin · Jetpack Compose · Android Automotive / Pleos Connect 대상

Pleos Connect 2.1.0 에뮬레이터에서 서명된 release APK의 실행, 실제 내비 목적지 인계, 주행 제한 시 조작 차단을 확인한 공모전용 데모입니다. **추천은 고정 예시를 이용하는 오프라인 시연이며, 내비게이션 인계는 실제 Pleos NaviHelper SDK를 사용합니다.** 표시한 장소, 이동·체류·복귀 시간, 영업·주차 정보는 실제 현장 상태를 보장하지 않습니다. 확인 결과와 시험 범위는 [검증 기록](docs/TEST_PLAN.md)에 남깁니다.

시간은 `아직 정하지 않음`, `제한 없음`, `정해진 시간`을 구분합니다. 복귀 기준이 정해지지 않았으면 돌아올 수 있다고 계산하지 않으며, 주차 시설 예시가 있어도 빈자리 정보는 미확인으로 남깁니다. 실제 현재 위치를 읽지 않고 서울 일대의 예시 좌표를 사용합니다.

## 시연 자료

- [실제 Pleos 실행 시연 58초](demo/naekimyeon-demo.mp4)
- [세로 소개 영상 41초](demo/naekimyeon-shortform.mp4)
- [로컬에서 영상·화면 모아 보기](demo/index.html)
- [이미지·영상 출처와 편집 범위](docs/ASSETS.md)

영상은 실제 에뮬레이터 녹화를 편집했습니다. 세로 영상의 한국어 음성은 소개용 TTS이며 Gleo 음성 호출 시연이 아닙니다. 화면의 장소와 시간은 샘플입니다. 화면 문구와 추천 제목은 Argon의 제품 카피 검토를 반영했습니다.

![실제 Pleos 에뮬레이터 첫 화면](demo/screenshots/home.png)

## 이렇게 둘러보세요

1. 기분이나 하고 싶은 일을 120자 이내로 적거나 네 가지 예시 문구를 고릅니다. 입력은 선택이며, 지원하지 않는 표현은 가까운 항목을 직접 고르게 안내합니다.
2. ‘추천 장소 보기’를 누르면 입력에 따라 순서·활동·추천 이유가 달라진 후보를 볼 수 있습니다. ‘머리 좀 비우고 싶어’에는 벤치에서 하늘 보기, ‘둘이 가볍게 다녀오고 싶어’에는 서로 마음에 드는 나무 소개하기처럼 할 일을 제안합니다.
3. 고르기 어렵다면 ‘하나만 바로 추천받기’를 사용합니다. 시간·활동 조건을 통과한 장소 중 하나를 보여줍니다. ‘다른 곳 보기’로 다른 후보를 확인할 수 있으며 자동으로 출발하지 않습니다.
4. ‘자세히 보기’에서 장소의 샘플 정보와 목적지를 확인하고 승인하면 실제 Pleos 내비로 전달합니다. 주행 제한 중에는 추천 선택과 상세 조작을 차단합니다.

마음 해석은 준비된 키워드를 연결하는 로컬 규칙입니다. 입력 원문은 저장·로그·콜백 응답에 넣지 않습니다. 감성 키워드 자체의 독창성이나 재방문 효과를 입증했다고 주장하지 않습니다. [제품 가치와 비교 시험](docs/PRODUCT.md), [아고라 검토 결과](docs/AGORA_DECISION.md)

## 실행 방법

준비된 release APK를 바로 시험하려면 Pleos Connect Emulator 2.1.0을 부팅하고 다음 명령을 사용합니다. 추가 API 키나 로그인은 필요하지 않습니다.

```powershell
adb install -r "apk/설우현_내키면_release.apk"
adb shell am start -n com.naekimyeon.pleos/.MainActivity
```

이미 다른 서명의 개발 APK를 설치한 경우 별도 테스트 AVD를 사용합니다. 소스에서 직접 빌드하는 방법은 다음과 같으며, release 서명은 [별도 빌드 안내](docs/BUILD_RELEASE.md)를 따릅니다.

1. 이 저장소를 Android Studio에서 엽니다.
2. JDK 21과 Android SDK Platform 36을 준비하고 Gradle 동기화를 실행합니다. 최초 의존성 다운로드에는 인터넷이 필요합니다.
3. Android Studio의 SDK 위치 또는 로컬 `local.properties`에 `sdk.dir`을 설정합니다. 개인 PC 경로가 들어 있는 이 파일은 저장소에 올리지 않습니다.
4. Windows에서 프로젝트 루트의 명령을 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

5. 에뮬레이터를 완전히 부팅한 뒤 Android Studio에서 `app`을 실행하거나 빌드한 APK를 설치합니다.

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.naekimyeon.pleos/.MainActivity
```

여러 장치가 연결되어 있으면 모든 `adb` 명령에 `-s <실제 장치 serial>`을 지정합니다. 디버그 APK 설치는 개발용입니다. 제출용 release APK의 파일과 검증 결과는 아래 항목을 따릅니다.

## 테스트 환경과 필요한 설정

| 항목 | 실제 확인 결과 |
| --- | --- |
| 앱 버전 | `0.1.0-demo` (`versionCode 1`) |
| 빌드 도구 | JDK 21 / Gradle 8.13 / Android Gradle Plugin 8.13.1 / Kotlin 2.2.21 · release 빌드 성공 |
| compileSdk / targetSdk / minSdk | 36 / 34 / 28 · JVM target 17 |
| **사용한 Pleos Connect 에뮬레이터 버전** | **공식 system image 2.1.0 · Android 14 / API 34 · x86_64** |
| **사용한 화면 규격** | **전체 디스플레이 2560 × 1440 px, 160 dpi**. 실제 앱 창 1682 × 1210 px; 방향 변경 시험의 좁은 창 1198 × 1210 px |
| Android Emulator / 호스트 가속 | 37.2.13 / Windows Hypervisor Platform / host GPU (NVIDIA GeForce GTX 1660 Ti) |
| Pleos 실행·설치 | 서명된 release APK 설치, 후보·상세·시간 계산, 내비 인계, D/P 제한 전환 확인 |
| 자동 테스트 | **67개 통과**: release JVM 38개 + 실제 기기 instrumentation 29개(Gleo 27 + 창 전환 1 + 단일 추천 1) |
| Android lint | `lintDebug` 성공, 오류 0 / 경고 11 |
| 실제 차량 | 미검증 |
| 외부 API 키 / 계정 | 추천 시연에 불필요. 로그인·분석 SDK·영속적인 이동 이력 없음 |
| 실제 위치 | 런타임 위치 권한 요청 없음. 서울 일대 고정 예시 |
| 차량 상태 | 실제 `CarUxRestrictions` 사용. 공식 Virtual Console의 D 전환 시 조작 화면 제한, P 복귀 시 기존 화면 복원 확인 |
| 내비 인계 | NaviHelper `3.0.5`의 실제 경로 적용과 앱의 `CONFIRMED` 상태 확인. 서울 일대 샘플 좌표 사용 |
| Gleo | Android Intent·JSON·콜백 payload 테스트 27개 통과. **공식 스키마 승인·실제 음성 호출은 미검증** |

일반 Android 에뮬레이터에서 실행되더라도 이를 Pleos Connect 검증으로 기록하지 않습니다. 차량 안전 상태를 읽지 못하면 상세 조작을 제한하므로 일반 Android 장치는 전체 차량 시연 환경을 대신하지 않습니다. Pleos SDK 문서의 예시 버전이나 권장 디스플레이 값을 실제 사용값으로 대신 적지 않습니다. [Pleos 공식 설치 안내](https://document.pleos.ai/docs/pleos-only/getting-started)를 참고해 환경을 준비할 수 있습니다.

창 크기 시험은 Pleos에서 Android의 화면 방향 변경을 요청한 결과이며, 두 개의 네이티브 앱 창을 나란히 배치한 전체 시험은 아닙니다. 테스트 수는 자동 시험 건수이며 사용자 조사 인원이나 실차 검증 건수가 아닙니다.

## APK

서명된 release APK: [`apk/설우현_내키면_release.apk`](apk/설우현_내키면_release.apk) · **22,306,087 bytes(약 21.3 MiB)**. Pleos 에뮬레이터에서 설치·실행했습니다. 서명 키와 비밀번호는 저장소에 포함하지 않습니다.

SHA-256: `a96732a899c78df565c2e594a7f1e719f087cfe0b89719b0495ef3e348816610`

## 구조와 검증 문서

- [제품 경험·가치 가설·비교 시험](docs/PRODUCT.md)
- [아고라의 모델별 입장·반론·이번 범위](docs/AGORA_DECISION.md)
- [아키텍처와 연동 범위](docs/ARCHITECTURE.md)
- [Pleos SDK·Gleo 계약 및 검증 범위](docs/PLEOS_INTEGRATION.md)
- [보안·개인정보·공개 범위](docs/SECURITY.md)
- [테스트 계획과 실행 결과](docs/TEST_PLAN.md)
- [제출 체크리스트](docs/RELEASE_CHECKLIST.md)

## 공모전 제출 기준

공식 안내 기준으로 저장소 주소와 최종 커밋 해시를 제출하고, APK는 `apk/` 또는 GitHub Release에 둡니다. 개발 산출물 가산점을 위한 저장소 공개 기간은 **2026-11-16까지 공개 전환, 2026-11-23까지 공개 유지**입니다. 공개가 어려우면 **소스 코드와 APK를 묶은 ZIP의 다운로드 링크를 접수 마감 전에 제출**하는 대안이 안내되어 있습니다. API 키를 공개하거나 자체 서버 소스를 제출할 필요는 없습니다. [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission), [추가 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000007)

저장소의 공개 상태는 오픈소스 라이선스 부여와 별개입니다. 별도 라이선스를 명시하지 않은 자체 코드에 MIT 등의 재사용 허가가 자동으로 생기지 않습니다. GitHub 공개 저장소의 열람·포크는 GitHub 이용약관의 적용을 받으며, 공개 후 만들어진 사본은 비공개 전환으로 회수되지 않습니다. 의존 라이브러리에는 각각의 라이선스가 적용됩니다. [GitHub 라이선스 안내](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
