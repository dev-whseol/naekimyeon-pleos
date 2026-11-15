# 0.3.0-preview 검증

실행일: **2026-11-16 KST**. 공모전 제출 후 개발 브랜치의 결과이며 제출본 0.2의 결과와 분리한다.

## 빌드와 자동 시험

| 확인 | 결과 |
| --- | --- |
| release JVM | **60개 통과**, 실패·오류 0 |
| Pleos 기기 계측 | **45개 통과**, 166.413초 |
| release APK / 기기 시험 APK | 빌드·설치 성공 |
| lintDebug | 성공, **오류 0 / 경고 13** |
| 서명 | APK Signature Scheme v3 검증 성공, 기존 제출본과 같은 인증서 |
| 제출본 보존 | 0.2 APK SHA-256 일치, 기존 파일 변경 없음 |

JVM 범위: 추천 계산 12, 마음·활동 12, 실제 장소·거리·위치 정책 15, 요청 세대·권한 정책 7, 내비 결과 확인 14.

기기 범위: Gleo 입력·응답 30, Android 위치 값 변환 8, 위치 설정 보관 1, 실제 Activity의 위치 제공자→추천 흐름 1, 창 크기 1, 주행 패널 1, 실제 장소 UI와 오류 복구 2, 명시적 단일 추천 1.

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease :app:assembleReleaseAndroidTest :app:lintDebug --max-workers=2
adb -s <Pleos-emulator-serial> install -r app/build/outputs/apk/release/app-release.apk
adb -s <Pleos-emulator-serial> install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb -s <Pleos-emulator-serial> shell am instrument -w -r com.naekimyeon.pleos.test/androidx.test.runner.AndroidJUnitRunner
```

첫 병렬 개발 중 실행한 lint에는 소스 변경 중 행 위치 불일치로 들여쓰기 오류 4건이 발생했다. 소스를 고정한 뒤 재실행하여 오류 0을 확인했으며 suppress나 baseline을 추가하지 않았다. 경고는 목표 API·기존 코드 권고와 신규 API 사용 방식 권고이며 해소됐다고 주장하지 않는다.

## 실제 환경

- Pleos Connect 공식 system image **2.1.0**, Android 14 / API 34, x86_64
- Android Emulator 37.2.13, WHPX, SwiftShader, 독립 개발 AVD
- 전체 화면 **2560 × 1440 px**, 160 dpi
- 실제 앱 영역 1682 × 1210 px, 방향 변경 시 1198 × 1210 px
- JDK 21, Gradle 8.13, AGP 8.13.1, Kotlin 2.2.21

다른 연결 장치는 사용하지 않았다. 기기 시험은 emulator serial을 명시했다. 실제 차량·Devbox·실차 GPS·차량별 권한 정책은 미검증이다.

## 위치 종단 시험

`LocationJourneyInstrumentedTest`는 실제 MainActivity와 Android LocationManager를 사용한다. 에뮬레이터에서만 임시 GPS test provider를 등록하고 서울숲의 공개 좌표를 주입한다. 미리 권한을 부여한 상태에서 앱의 현재 위치 사용 버튼을 눌러 실제 장소 추천, 자동 시작 선호의 Activity 재생성 유지, 대표 위치 확인, 샘플 전환 시 수집·설정 해제를 시험했다. 생산 코드의 특별 테스트 Intent나 대체 UI는 사용하지 않았다.

이 시험은 **시스템 권한 대화상자를 사용자가 직접 승인하는 전체 과정**, OS 프로세스 사망, 실차 센서 정확도를 증명하지 않는다. 기존 권한·app-op·선호를 시험 종료 후 복원하며, runtime 권한 회수는 runner가 종료된 뒤 host에서 수행한다.

`NearbyFlowInstrumentedTest`는 통제한 UI 상태를 이용해 위치 변경·만료 때 열린 확인창을 닫고, 부산 위치에서 서울 공원을 자동 대체하지 않으며, D에서 설정 버튼을 보이지 않는지 시험한다. 이와 실제 CarUxRestrictions 시험을 구분한다.

## 실제 에뮬레이터 D 전환과 갱신

기본 45개 시험 이후 같은 Journey 시험을 `manualDriving=true`로 추가 실행했다. 테스트가 차량 정책을 덮어쓰지 않고, 공식 Virtual Console에서 D/P를 바꿔 실제 CarUxRestrictions와 화면을 함께 확인했다.

- 전체 추가 시나리오 **191.198초**, 성공. 기존 45개와 중복된 시험이므로 별도의 46번째 고유 시험으로 세지 않는다.
- D에서 실제 Activity를 재생성해도 새 권한창 없이 `서울숲 안내` 후보가 복원됐다.
- **125.003초**, 후보 관찰 **102회**, 새로운 시험 위치 주입 **5회**. 120초 위치 수명을 넘어 전경 갱신이 유지됐다. 후보 발견 대기 허용 범위는 관찰당 2초다.
- P로 복귀하면 상세 화면을 되찾았고, 샘플 전환 시 위치 사용 설정이 꺼졌다.
- 속도 0 km/h의 D 상태 에뮬레이터 시험이다. 실제 도로 주행, OS 프로세스 사망 뒤 콜드 스타트, 실제 GNSS 측정은 시험하지 않았다.
- 임시 GPS 제공자·mock app-op·설정을 정리하고, 기기 사용자 10의 임시 위치 권한을 runner 종료 후 원래대로 회수했다.

```powershell
adb -s <Pleos-emulator-serial> shell am instrument -w -r -e class com.naekimyeon.pleos.platform.LocationJourneyInstrumentedTest -e manualDriving true com.naekimyeon.pleos.test/androidx.test.runner.AndroidJUnitRunner
```

출력의 `awaitingVehicleGear=D`와 `awaitingVehicleGear=P`에 맞춰 Virtual Console을 조작한다. 자동화된 일반 시험 실행에서는 이 옵션을 넣지 않는다.

[위치 추천 화면](../demo/screenshots/preview-0.3/nearby-results.png) · [대표 위치 확인](../demo/screenshots/preview-0.3/representative-confirmation.png) · [D 125초 뒤](../demo/screenshots/preview-0.3/driving-after-125s.png) · [P 복귀](../demo/screenshots/preview-0.3/parked-after-driving.png)

## APK 식별

- 파일: `apk/preview/naekimyeon-0.3.0-preview-release.apk`
- versionCode 3 / versionName 0.3.0-preview
- 22,453,683 bytes
- SHA-256: `6ddadc21dd2248d7827cc7079947fbaef79aeb6cf2d069baf5b3edc7e1e57c64`
- 서명 인증서 SHA-256: `b639fbe340b1a8a45267ceee9db441fa7fd1f0a52a3b6a9c1454e2e8d801f00e`

Gleo 공식 스키마 승인·실제 음성 호출, Pleos FusedLocation SDK, 실시간 ETA·주차, 운영 장소의 차량 진입점, 시장 효과와 App Market 심사는 완료 범위에 포함하지 않는다.
