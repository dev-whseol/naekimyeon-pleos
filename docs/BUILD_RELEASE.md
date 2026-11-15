# Release 빌드와 서명

심사용으로 실행할 때는 저장소 `apk/`에 있는 검증된 release APK를 사용합니다. 소스에서 개발할 때는 JDK 21, Android SDK Platform 36, Gradle wrapper 8.13을 사용합니다.

```powershell
.\gradlew.bat testReleaseUnitTest assembleRelease assembleReleaseAndroidTest lintDebug
```

Release 서명에는 로컬 `keystore.properties`가 필요합니다. `keystore.properties.example`을 복사한 뒤 본인의 서명 파일 경로·별칭·비밀번호를 기입합니다. 실제 속성 파일과 keystore는 Git에서 제외되어 있습니다. 예시의 `CHANGE_ME_LOCALLY`는 비밀번호가 아니며 그대로 사용하면 안 됩니다.

별도 키가 없다면 JDK의 `keytool -genkeypair`로 저장소 밖의 개인 보관 폴더에 새 키를 만들 수 있습니다. 비밀번호는 도구의 입력 프롬프트에서 정하고 보관합니다. 제출 APK에 사용한 원래 키를 GitHub에 올리거나 다른 개발자와 공유할 필요는 없습니다. 같은 앱의 업데이트를 배포할 때는 서명 연속성·키 보관 절차를 유지해야 합니다.

```powershell
keytool -genkeypair -keystore <저장소_밖의_서명파일> -alias naekimyeon -keyalg RSA -keysize 3072 -validity 10000
```

현재 데모의 서명 설정은 minSdk 28에 맞는 APK Signature Scheme v3로 검증했습니다. 빌드 결과는 `app/build/outputs/apk/release/app-release.apk`에 생성됩니다. 서명 파일을 제공하지 않은 소스 빌드의 결과물을 제출용으로 혼동하지 않도록 다음 확인을 거칩니다.

```powershell
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk
```

패키지·버전·서명과 non-debuggable 상태를 확인한 다음 실제 Pleos 에뮬레이터에 설치합니다. 기존 설치 APK의 서명이 다르면 새 APK로 덮어쓸 수 없으므로 별도 테스트 AVD를 사용합니다. 테스트 APK는 release 대상과 같은 서명으로 빌드되어야 합니다. Gleo 계측 테스트는 Intent·JSON과 콜백 계약 검증이며 실제 음성 승인을 대신하지 않습니다.
