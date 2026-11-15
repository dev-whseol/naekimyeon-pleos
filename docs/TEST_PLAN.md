# 테스트 계획과 실행 기록

이 문서의 **계획**은 완료한 시험이 아닙니다. 실행 결과에는 명령, 대상 산출물, 환경, 실제 관찰을 기록하며 미검증 항목은 그대로 남깁니다. 일반 Android 실행 결과를 Pleos 실행 결과로 바꾸어 적지 않습니다.

**기능 검증:** 기분에 따른 장소·활동 제안과 조건을 통과한 후보 하나 추천을 포함해 release JVM 38개와 실제 기기 instrumentation 29개, 총 67개가 통과했습니다. 기존 내비·주행 제한의 실행 근거와 새 기능의 테스트 범위를 아래에 구분합니다.

최종 제품 문구 교정 후 release 빌드·JVM 38개·기기 instrumentation 29개·lint를 다시 실행해 모두 통과했습니다. ‘추천 장소 보기’, ‘하나만 바로 추천받기’, ‘다른 곳 보기’, ‘자세히 보기’ 문구를 사용하는 APK가 최종 산출물입니다.

## 실행 상태

| 검증 | 상태 | 근거 |
| --- | --- | --- |
| Kotlin/Compose 소스 빌드 | 통과 | `:app:assembleRelease` 성공 |
| release JVM 단위 테스트 | 통과 | 마음·장면 12개 + 추천 계산 12개 + 경로 확인 조건 14개 = 38개, 실패·오류·건너뜀 0; XML 결과 확인 |
| Gleo Android instrumentation | 통과 | 실제 Android Intent/Bundle/JSON과 응답 payload 27개. release를 대상으로 Pleos 에뮬레이터에서 실행 |
| 화면 크기·선택 유지 instrumentation | 통과 | 1개. 실제 창 크기 변경 및 취향·45분·같은 출발점 복귀 조건 유지 확인 |
| 단일 추천 instrumentation | 통과 | 1개. 실제 UI에서 조건에 맞는 후보 하나를 제안하고 상세 화면으로 이어지는 동작 확인 |
| Android lint | 통과 | `lintDebug` BUILD SUCCESSFUL. 오류 0, 경고 11(업데이트·스타일 등); XML 보고서 확인 |
| release APK 생성 | 통과 | 최종 `apk/설우현_내키면_release.apk` 22,306,087 bytes |
| release 서명 및 non-debuggable | 통과 | 최신 release는 APK Signature Scheme v3, 3072-bit RSA. badging에 `application-debuggable` 없음; 병합 manifest에도 debug 선언 없음 |
| release APK 설치와 핵심 흐름 | 통과 | Pleos 설치, 후보·상세·시간 계산·내비 인계 확인. 모든 장애 조합을 시험했다는 뜻은 아님 |
| Pleos Connect 실행 | 통과 | 공식 system image 2.1.0 / Android 14 / API 34에서 release 실행 |
| 화면 규격 및 좁은 창 대응 | 확인한 범위 통과 | 전체 화면 2560 × 1440 px, 160 dpi. 앱 창 1682 × 1210 → 1198 × 1210 → 1682 × 1210 px 전환 및 조건 유지 |
| 주행 제한 실제 연동 | 통과 | 공식 Virtual Console에서 D 전환 시 제한 UI, 외부 Gleo action으로 우회 불가, P 복귀 시 이전 화면 복원 |
| 실제 내비 목적지 인계 | 통과 | 실제 내비 목적지 반영과 앱 `CONFIRMED` 상태 확인. 새 경로 진행 후 목적지 응답 일치 확인 |
| 보안 정적 점검 | 소스·APK 확인 완료 | 문구 교정 후 공개 후보 61개의 파일 목록·텍스트 및 최종 APK 내부 36개 항목 검사. 실제 키·연락처·제외 파일 미발견. 미디어 내용은 별도 확인 |
| cold start / CPU / RAM | 부분 측정 | 시작 TotalTime 3,481 ms 1회; 대기 PSS 51,086 KB / RSS 140,780 KB 1회. CPU 미측정 |

자동 테스트 통과는 총 **67개(38 release JVM + 27 Gleo + 1 화면 + 1 단일 추천)**입니다. 실제 기기 instrumentation 29개도 전체 실행으로 통과했습니다. 이전의 49개는 별도의 이전 빌드 기록이며 합산하지 않습니다. 사용자 수·실차 수·독립적인 현장 검증 건수를 의미하지 않습니다.

화면 시험의 첫 실행은 테스트 코드가 선택 상태를 `selected` 대신 `checked`로 찾는 오류로 실패했습니다. 선택 상태 자체를 가정하는 검사를 실제 창 크기와 복원된 조건의 계산 결과 확인으로 바꾼 뒤 통과했습니다. 첫 실패를 통과 건수에 포함하지 않습니다.

## 실제 실행 환경과 최종 APK

2026-11-15 실행한 공식 Pleos Connect system image 버전은 **2.1.0**이며 Android 14 / API 34, x86_64입니다. Android Emulator 실행 파일은 **37.2.13**, 가속은 Windows Hypervisor Platform, GPU는 host / NVIDIA GeForce GTX 1660 Ti입니다. `wm size`는 **2560 × 1440**, `wm density`는 **160**을 반환했습니다. 앱 영역은 시스템의 driving view·상태 바·GNB를 제외한 창에 표시됩니다. 기본 앱 bounds는 `Rect(853, 90 - 2535, 1300)`으로 **1682 × 1210 px**입니다.

런타임 빌드 fingerprint:

```text
Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys
```

최종 APK SHA-256은 `a96732a899c78df565c2e594a7f1e719f087cfe0b89719b0495ef3e348816610`, 크기는 **22,306,087 bytes**입니다. 서명 인증서 SHA-256은 `b639fbe340b1a8a45267ceee9db441fa7fd1f0a52a3b6a9c1454e2e8d801f00e`입니다. 최종 APK를 직접 해시하여 확인했습니다.

아래의 단일 성능 표본은 초기 빌드에서 얻었습니다. 해당 초기 APK는 SHA-256 `d566305edabbb332ded6b1e5901cca7c78853fd5bcfd21452551fc81b4432c7b`, 22,218,467 bytes였습니다. 이 수치를 새 UI의 최종 성능 측정값으로 바꾸어 기록하지 않습니다.

ADB 기본 포트에서 다른 버전의 서버와 충돌하여 시험 호스트에서는 별도 포트 5039를 사용했습니다. 일반 설치에 필요한 앱 설정은 아니며, 같은 충돌이 있을 때만 `adb -P 5039 ...`와 명시적인 장치 선택을 사용합니다.

## 실제 연동 시험 결과

| 시험 | 실제 관찰 | 확인 범위 |
| --- | --- | --- |
| 내비 인계 | 사용자가 샘플 위치 전송을 승인한 뒤 내비 경로가 설정되고 앱에 경로 설정 확인 문구 표시 | 실제 NaviHelper 호출과 provider 응답. 실제 장소의 방문 적합성은 미검증 |
| 새 경로 확인 | `onRouteEnded` → 새 경로 이후의 목적지 일치 → `routeConfirmed` 순서 관찰 | 이전 경로 취소 이벤트가 새 요청을 즉시 실패시키지 않도록 수정 후 재검증 |
| 제한 진입 | 공식 Virtual Console의 D 상태에서 조작 화면 대신 안내 텍스트 2개만 표시 | 실제 `CarUxRestrictions` 상태에 따른 화면 차단 |
| 제한 우회 방지 | D 상태의 외부 `FIND_BREAK_PLACE`가 후보 화면을 열지 않음 | Gleo action이 제한 상태를 우회하지 않음 |
| 제한 해제 | P 복귀 시 이전 화면으로 돌아옴 | 현재 화면 유지. 모든 자동차·지역별 제한 정책의 인증은 아님 |
| 화면 크기 전환 | 1682 × 1210 (x853, y90) → 1198 × 1210 (x1095, y90) → 1682 × 1210 (x853, y90) | Android `requestedOrientation` 변경을 Pleos에서 실행. 네이티브 앱 창 2개를 나란히 배치한 841 px 창 시험은 아님 |
| 선택 유지 | 취향·45분·동일 출발점 복귀를 선택한 뒤 크기를 바꾸어도 조건 유지, 결과 체류 10분 확인 | 동작 결과를 통한 상태 유지 확인 |
| Gleo 파서·응답 | 27개 instrumentation 통과: 형식·범위·충돌·과대 payload·마음 입력 검증 및 샘플 응답 내용 확인 | 실제 Android 구현을 사용하되 테스트 context가 broadcast를 기록. 실제 Gleo 수신·발화·승인은 미검증 |
| 기분에 따른 장소·활동 | 12개 JVM 테스트 통과: 입력 해석·모호함 처리·순서·활동 변화·시간 조건 및 후보 선택 규칙 | 실제 추천 품질·감정 효과·재방문 성과를 측정한 것은 아님 |

내비 응답 기한은 30초이며 새 경로 시작/완료 뒤에 수신한 일치 목적지와 완료 또는 `ROUTE_DRIVE`를 확인합니다. 시험 provider의 `RouteStateInfo.isRouting`은 안내가 진행된 뒤에도 false일 수 있어 진단에만 사용합니다. 타임아웃은 목적지가 변경되지 않았다는 뜻이 아니라 결과를 확인하지 못했다는 뜻입니다.

## 기능 시나리오

| ID | 조작 | 기대 결과 |
| --- | --- | --- |
| F01 | 깨끗한 설치 후 오프라인 실행 | 계정·외부 키 없이 첫 화면 표시. 고정 예시임을 확인 가능 |
| F02 | 시간·기분 등 지원 조건 선택 | 선택 상태와 후보가 일관되게 변경. 잘못된 시간이나 빈 입력 방어 |
| F03 | 후보 열기·닫기·다른 후보 선택 | 상세 내용과 선택 대상 일치. Back 행동 예측 가능 |
| F04 | 시간 예산이 부족한 조건 선택 | 불가능한 일정을 가능하다고 표시하지 않음. 빈 결과 또는 대안 제공 |
| F05 | 같은 버튼 빠르게 반복 터치 | 중복 화면·중복 인계 없음. 진행 중 상태 일관 |
| F06 | 앱을 뒤로 보내고 복귀 | 충돌 없이 복귀. 보존/초기화 규칙과 실제 동작 일치 |
| F07 | 창 크기·화면 방향 변경 | 핵심 버튼·텍스트 잘림과 겹침 없음. 선택 상태 정책 유지 |
| F08 | 목적지 인계 실행 | 시연·미지원·실패·성공을 구별. 실제 인계 전 성공 표시 없음 |
| F09 | 차량 제한 상태를 알 수 없음 | 실제 주행 허용으로 오인하지 않음. 관련 조작 제한·안내 |
| F10 | 데이터·권한·플랫폼 기능 불가 | 재시도/대안 가능하며 앱 종료·무한 로딩 없음 |
| F11 | Gleo 요청에 잘못된 활동, 소수·범위 밖 시간, 4 KB 초과 JSON, 모순된 시간 조건 전달 | 거절 또는 추가 확인. 자동 내비 호출 없음 |

도메인 단위 테스트는 시간 예산 경계값, 부족한 시간, 조건 필터, 빈 데이터, 결과 정렬의 결정성 등 계산 규칙을 검증합니다. 화면 구성 자체를 복제하는 테스트보다 사용자에게 잘못된 시간·추천·성공을 보여줄 수 있는 분기를 우선합니다.

## 정적 확인

2026-11-15 초기 소스 26개 공개 후보 파일을 대상으로 알려진 토큰·개인키 패턴, 이메일·국내 휴대폰 형태, 제외 대상 확장자를 검사했습니다. 실제 키·연락처나 금지 파일은 발견하지 못했습니다. 이메일 형태 탐지 1건은 Kotlin의 `this@Canvas.size` 표현에 대한 오탐으로 확인했습니다. 이는 이후 추가되는 파일·Git 이력·최종 APK 검사까지 완료했다는 뜻은 아닙니다.

release 중간 검증 시 공개 후보 36개 파일의 알려진 키·전화번호 패턴을 재검사했고 발견 0건이었습니다. 같은 APK의 DEX/XML/JSON 등 34개 항목에서 알려진 키 패턴도 발견하지 못했습니다. 패턴 검사는 모든 형태의 비밀값을 탐지한다는 보장이 없으며 최종 파일이 달라지면 다시 확인합니다.

최종 소스·APK 검사에서는 공개 후보 48개 파일에서 실제 연락처·알려진 비밀키·제외 대상 파일을 발견하지 못했습니다. 이메일 형태 2건은 앞서 확인한 Kotlin 표현과 그 오탐 설명 문서였습니다. 최종 APK의 DEX/XML/JSON 등 36개 항목에서 알려진 키 패턴 0건을 확인했습니다. `local.properties`와 `keystore.properties`의 Git 제외 및 Git author 이메일의 GitHub noreply 사용도 확인했습니다. 원문 키나 개인정보를 검사 결과에 출력하지 않았습니다.

최종 APK를 `apksigner verify --verbose --print-certs`로 다시 검사해 v3 서명과 동일 인증서 SHA-256을 확인했습니다. v2는 false이며 minSdk 28의 이번 APK는 v3로 검증됩니다. 병합 manifest의 backup/cleartext 비활성화와 명시적 백업·전송 제외 규칙도 재확인했습니다.

마지막 제품 문구 교정 후 공개 후보가 61개인 시점에 파일 목록과 텍스트 검사를 반복했습니다. 앞서 설명한 이메일 오탐 2건 외 실제 연락처·알려진 키·제외 대상 파일은 없었습니다. 해시 `a96732a8…8816610`의 최종 APK에서 검사한 36개 항목의 알려진 키 패턴도 0건이었고, v3 서명·인증서와 파일 크기를 다시 확인했습니다. 영상·이미지는 텍스트 패턴 검사 대상과 구분합니다.

테마에 정의된 주요 텍스트/배경 색상 6쌍을 정적으로 계산한 명암 대비는 6.48:1~15.96:1입니다. 이미지 위 텍스트·투명도·시스템 렌더링을 포함한 전체 화면의 접근성 시험 결과는 별도입니다.

release 병합 manifest에서 위치·마이크·INTERNET 권한이 없고, backup 및 cleartext가 비활성화되어 있음을 확인했습니다. `MainActivity`는 launcher와 Gleo action을 위해 export됩니다. AndroidX 초기화 provider는 비공개이고, AndroidX ProfileInstallReceiver는 export되어 있지만 `android.permission.DUMP`로 보호되어 있습니다. 프레임워크가 추가한 signature 수준의 내부 동적 receiver 권한도 확인했습니다.

주행 제한 시 정상 조작 UI를 구성하지 않는 분기, 기본 제한 상태, 내비 호출 직전의 재검사를 코드와 D/P 전환 시험에서 확인했습니다. Gleo 요청은 제한·미확인 상태에서 거절되며, 화면 적용 확인 후에만 샘플 화면을 열었다고 응답합니다. 서비스 해제·모든 권한 거부 조합·장시간 백그라운드 동작은 별도 시험 범위입니다.

## 차량 UX 확인

가로 전체 화면과 좁은 앱 창에서 핵심 정보와 버튼을 확인합니다. 텍스트 확대, 터치 표적, 명암 대비, 시스템 바·키보드 가림, 로딩 중 중복 조작을 함께 확인합니다. 사용자 입력 후 응답 또는 진행 상태 표시의 목표는 250 ms 이내이며, 색 대비 목표는 4.5:1 이상입니다. 반복 애니메이션·자동 스크롤을 주행 환경의 장식으로 사용하지 않습니다. 이 수치는 시험 목표이며 현재 충족 측정값이 아닙니다. [Pleos UI 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)

실제 `CarUxRestrictions` 변경과 제한 해제 복귀는 위의 D/P 전환으로 확인했습니다. 모든 권한 거부, 서비스 연결 해제, 앱 복귀 후 경합, 목적지 인계 실패의 조합은 후속 시험 범위입니다. 데모 토글 시험은 플랫폼 통합 시험을 대체하지 않습니다.

## 환경 기록 방법

아래 명령 결과에서 개인 경로·장치 식별자·로그 내용은 공개하지 않고 환경값만 요약합니다. AVD 이름만으로 Pleos 버전을 단정하지 말고 설치된 system image 패키지의 버전 정보와 런타임 빌드 정보를 함께 확인합니다.

```powershell
java -version
.\gradlew.bat --version
adb devices
adb emu avd name
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.display.id
adb shell wm size
adb shell wm density
```

디스플레이 전체 해상도와 앱의 실제 사용 window 크기는 다릅니다. 실제 실행 캡처와 창 영역을 함께 확인하여 README에 기록합니다. 여러 장치가 있으면 `adb -s <실제 serial>`을 사용합니다.

## APK 확인

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintDebug :app:assembleRelease
Get-FileHash -Algorithm SHA256 -LiteralPath 'apk/설우현_내키면_release.apk'
apksigner verify --verbose --print-certs 'apk/설우현_내키면_release.apk'
apkanalyzer manifest application-id 'apk/설우현_내키면_release.apk'
apkanalyzer manifest debuggable 'apk/설우현_내키면_release.apk'
adb install -r 'apk/설우현_내키면_release.apk'
adb shell am start -W -n com.naekimyeon.pleos/.MainActivity
```

`apksigner`와 `apkanalyzer`는 Android SDK 설치 위치에 따라 전체 경로가 필요할 수 있습니다. 서명 키나 비밀번호를 문서·셸 이력에 넣지 않습니다. `am start -W` 한 번의 수치를 cold start 성능 전체로 해석하지 않습니다.

## 성능 측정 계획

초기 release 실행 1회에서 `am start -W`의 `TotalTime`은 **3,481 ms**였습니다. 대기 상태 메모리 표본 1회는 **PSS 51,086 KB(약 49.9 MiB), RSS 140,780 KB(약 137.5 MiB)**였습니다. 반복 측정, CPU 사용률, 상위 지연값, 장시간 동작, 실차 측정은 아직 없습니다. 단일 표본을 성능 보장이나 공식 자원 기준의 전체 통과로 해석하지 않습니다.

release APK에서 동일 환경으로 cold start를 반복 측정하고 장치·시나리오·시도 수를 기록합니다. 첫 화면, 후보 변경, 상세 전환, 백그라운드 대기 시 메모리·CPU를 확인합니다. 에뮬레이터와 실차의 수치는 별도로 관리합니다.

공식 자원 기준을 목표로 삼습니다: 비내비 앱의 주행 중 CPU 5% 미만, 정차 중 CPU 25% 이하, RAM 400 MB 이하. 현재 앱의 수치를 실제로 측정하기 전에는 기준 통과를 표시하지 않습니다. [Pleos 성능 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt)

## 최종 기록

- 검사 일시: 2026-11-15; 최종 제출본 검증 계속
- 검사한 Git 커밋: 제출 직전 확정
- APK SHA-256: `a96732a899c78df565c2e594a7f1e719f087cfe0b89719b0495ef3e348816610`
- 실제 Pleos Connect system image 버전: 2.1.0 / Android 14 / API 34
- 실제 해상도 / dpi: 2560 × 1440 px / 160 dpi; 앱 창 1682 × 1210 및 1198 × 1210 px 확인
- 설치·화면 시나리오 결과: release 실행, 내비 인계, D/P 제한, 방향 변경 시 선택 유지 확인
- 남은 제한: 실차, 전체 네이티브 분할 창 조합, 외부 실시간 데이터, Gleo 공식 승인·음성 호출, 반복 성능 측정은 미검증
