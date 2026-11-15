# Pleos integration record

Checked against official sources on 2026-11-15. This document separates implemented Android code from a platform capability that has actually been demonstrated. A successful build or installation does not establish that navigation or Gleo accepted a request.

The product remains a vehicle app for spontaneous outings when the destination is undecided. The 0.2 revision adds an optimized single-candidate driving flow within that scope; it does not change the product into pickup, split-outing, or reunion coordination. The user's corrected scope takes precedence over prior model recommendations. See [the context repair record](AGORA_CONTEXT.md).

## Implemented code and verification status

| Area | Included implementation | Verification boundary |
| --- | --- | --- |
| NaviHelper | `platform/NaviAdapter.kt`, direct SDK imports and asynchronous callbacks | Release on official Pleos 2.1.0 / API 34 handed off the sample coordinate to native navigation. The revised new-route/fresh-destination flow reached app-side `CONFIRMED`, observed in the UI and diagnostic event sequence. |
| Driving restrictions | `platform/DrivingSafetyAdapter.kt`, AAOS `CarUxRestrictionsManager` | 0.2 displayed its single-candidate panel and two active actions in actual emulator D state; the driving panel test passed after the request/render race fix. No OS gray block, keyboard, or photos were shown. Unknown or unavailable state remains fail-closed for navigation. |
| Gleo | `platform/GleoBridge.kt`, `assets/schema.json` | The previous 27-test instrumentation run passed. Two new single-candidate driving-response tests bring the Gleo suite to 29 and await execution. Schema has **not** been submitted or approved. Live Gleo voice integration is **not verified**. |
| Recommendations | Local app domain and sample candidates | No live POI, weather, parking occupancy, or traffic service is connected by these adapters. |
| Travel-time preview | Not connected | `requestTrip()` is an available official API; displaying live ETA or a live round-trip budget requires further integration and runtime verification. |

The 0.2 release passed 38 JVM tests and 32 device instrumentation tests, 70 total across runs. Device coverage comprises 31 tests plus the corrected driving-panel test, which passed in 25.7 seconds. Actual emulator D showed the optimized panel; a Gleo Android action was also delivered in D. This is not approval or end-to-end proof of live Gleo voice. Native route acceptance and app-side confirmation were verified with the unchanged navigation adapter in the prior release. See [the test report](TEST_PLAN.md) for exact scope.

## Build and manifest requirements

The public Maven repository serves `ai.pleos.playground:NaviHelper:3.0.5` without repository credentials. Its published POM resolves `navi-data:3.0.4` and `navi-aidl:3.0.1`. The implementation was checked against the publisher's source JARs and the official sample; no SDK binary was decompiled. [Official import guide](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk), [official sample](https://github.com/Pleos-Playground/pleos-sample-navi-app), [published POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.pom).

```kotlin
// settings.gradle.kts, dependencyResolutionManagement.repositories
maven("https://nexus-playground.pleos.ai/repository/maven-releases/")

// app/build.gradle.kts
android { useLibrary("android.car") }
dependencies { implementation("ai.pleos.playground:NaviHelper:3.0.5") }
```

```xml
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />

<!-- Inside application. Root Activity checks automotive feature before constructing the safety adapter. -->
<uses-library android:name="android.car" android:required="false" />

<!-- Inside the exported singleTop MainActivity. -->
<intent-filter>
    <action android:name="FIND_BREAK_PLACE" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

`NAVI_ROUTE` covers information requests. `NAVI_ROUTE_SEARCH` covers route mutation. The app does not call the custom-map/screen API and does not need `NAVI_CUSTOM_MAP`. The official SDK source marks the two used permissions as normal permissions; the adapter still checks grants and handles `SecurityException`. [Permission guide](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions).

Source artifacts used to verify public types:

- [NaviHelper 3.0.5 published sources](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar)
- [navi-data 3.0.4 published sources](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar)

## Navigation behavior

`NaviAdapter(context, onState)` exposes `start()`, `navigate(latitude, longitude, name)`, and `close()`. `NavigationState` contains a `NavigationStatus` and a Korean display message. Create it from the Activity, call `start()`, and release it with `close()` when the Activity is destroyed. A failed initial connection releases the SDK so another `start()` retries. A closed adapter cannot be restarted; create another instance.

`start()` uses `NaviHelper.initialize(onConnected, onFailed)` and registers the same listener instance that it later removes. Initial connection has a 10-second deadline. Generation checks discard callbacks from a released connection. All callbacks are serialized onto the Android main thread. [Initialization guide](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release).

Only an explicit navigation action calls `navigate()`. It invokes `requestRoute(RouteInfo(...))`; displaying, selecting, or filtering a suggestion never changes the route. Coordinates are validated. For the current coordinate-based handoff, `poiId`, `address`, and `poiSubId` are empty strings, with the place title in `poiName`. The installed Pleos 2.1.0 navigation provider accepted this payload and displayed the sample destination with a native route. The app-side confirmation was also verified separately through the `onRouteEnded` → fresh matching destination → `routeConfirmed` sequence and the app's confirmation message.

`REQUESTING` means the request is outstanding. `CONFIRMED` requires a matching destination observed after new route-start/completion progress, plus completion or `NavigationStatus.ROUTE_DRIVE` after that start. Coordinates must match within 0.001 degrees. Both `onRoutingCompleted()` and deprecated `onRouteEnded()` are handled because the SDK dispatches them separately. Read-only destination/state queries begin after one second and repeat every two seconds while pending, with a 30-second deadline. Readback of an already-active nearby route cannot confirm a new request before new-route progress. Errors and missing permission produce non-success states. A timeout means the result was not confirmed; it does not assert that the route stayed unchanged. One outstanding request is allowed because these callbacks have no request ID. [Event guide](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners), [error guide](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/error-handling).

The tested provider sends `onRouteCancelled()` for the old route during replacement, followed later by new-route started/ended events. Therefore cancellation clears accumulated confirmation evidence but keeps the new request pending. The same provider reports `RouteStateInfo.isRouting=false` after `NavigationStatus.ROUTE_DRIVE`; that field is logged for diagnosis only and neither establishes nor revokes guidance confirmation. A negative status does not erase an already received completion event. The six-second Gleo screen deadline is independent of the navigation deadline.

Diagnostics use the `NaekimyeonNavi` log tag and contain event names and Boolean readiness/match results only. No destination names, coordinates, route details, or account information are logged by this adapter.

The optional future ETA integration should call the real `requestTrip(TripRequest(...))` and await `onTripInfo(TripInfo)`. The published model's `distance` is meters and `eta` is seconds. `TripInfo` does not establish a round-trip budget or parking occupancy. This API is currently documented here, not connected to the sample recommendation UI.

## Driving restrictions and lifecycle

`DrivingSafetyAdapter(context, onState)` exposes `start()` and `close()`. `isKnown` distinguishes available platform policy from an unknown/unavailable state. `restricted` (also exposed as `requiresDistractionOptimization`) means a distraction-optimized experience is required, not that all app functions are prohibited. The state retains `activeRestrictions` and the platform's maximum string length, cumulative content items, and content depth. Unknown state remains fail-closed; the connection deadline is eight seconds. The adapter never infers policy from gear or speed. [Pleos restriction guide](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [Android integration guide](https://developer.android.com/training/cars/parked/automotive-os).

The driving-mode revision uses a single candidate and explicit destination action when policy is known, while hiding keyboard entry, lists, scrolling, setup, dialogs, and raw voice transcription. Unknown policy disables navigation. Full browsing remains an unrestricted-state experience. The Activity must declare the following metadata for the OS to allow that optimized screen while UX restrictions are active. This is an official AAOS declaration, not an override of `CarUxRestrictions`; every active feature limit must still be enforced. Metadata alone does not establish Pleos App Market approval. [AOSP distraction-optimized activity declaration](https://source.android.com/docs/automotive/driver_distraction/guidelines), [Pleos regional restriction policy](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy).

```xml
<!-- Inside the Activity that implements both full and minimal driving experiences. -->
<meta-data android:name="distractionOptimized" android:value="true" />
```

Pleos permits limited location/mobility and navigation functionality while driving. App category must match actual functionality. This app delegates route guidance to the installed navigation provider; it must not claim ownership of navigation or use `APP_MAPS` solely to obtain a category exception. A true Maps/Navigation/NaviContainer app has additional category and location-permission requirements. [Pleos app categories](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-category), [navigation development policy](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide).

Pleos can allocate one portrait window or two landscape app areas beside the fixed driving view. Its window placement may change while an app remains alive. Keep the Activity resizable, preserve the user's selections, and verify both narrow and wide configurations. There is no custom window-management SDK in this app. [Pleos app structure guide](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model).

For real window transitions, the system's app window button switches portrait/landscape; a three-finger window-bar drag is another documented gesture. Window-bar taps and three-finger swipes also change app position. Verify the resulting display and actual touch targets together; a changed `am task resize` configuration alone is insufficient evidence of a usable Pleos window. [Official Window Policy](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window). The separate official FormFactorConsole can test virtual display presets using Host Display Bridge; it requires a visible standalone emulator window. [Form factor testing](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/form-factor-test-guide).

## Gleo schema and Android action harness

The single proposed function is `FIND_BREAK_PLACE`. It accepts either `need` (`breeze`, `conversation`, or `shelter`) or an explicitly supplied `feeling` phrase; at least one is required by the parser. Existing need-only commands remain compatible. A supplied feeling must be a nonblank string of at most 120 characters and is trimmed before UI handoff. A feeling-only command leaves `need` null; it does not invent a category. Optional `timeMinutes` accepts whole numbers from 1 through 240. Optional `noTimeLimit` must be Boolean and cannot be true alongside a numeric budget. Omitted time and explicitly unlimited time remain distinct.

The official Gleo contract uses Android intents and `assets/schema.json`; it does not require a separate Gleo SDK dependency. The schema in this repository is an integration candidate and has not been approved. Publishing the asset alone is not proof of a live voice connection. [Schema guide](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability), [delivery and review guide](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema).

Call `GleoBridge.parse(intent)` in initial-intent handling and `onNewIntent`. An `Accepted` request only prepares matching **sample** UI; it must never call `navigate()`. After the UI actually accepts the request, `respondSampleOpened()` reports that sample candidates opened and live recommendations are not connected. Missing/conflicting inputs return `Asking`; malformed or unsupported inputs return `Rejected`.

For known driving restrictions, `respondDrivingCandidate(context, request, candidateTitle)` is called only after the optimized panel has presented that specific single candidate. It returns a voice-mode payload with one sample title, `confirmationRequired=true`, and `navigationStarted=false`. It includes neither raw feeling text nor coordinates. A missing candidate cannot produce a success response. Only a separate explicit destination action can call NaviHelper. This still tests Android action/response handling, not an approved or connected live Gleo voice service.

Parsing validates the supplied fields; it does not classify feelings or claim to infer a person's mental state. The UI's local rules map explicit phrases to sample experiences. An unmatched phrase asks the user to choose an experience instead of reporting a successful match. The bridge does not persist the phrase. Callback data contains only a `hasFeeling` Boolean, and the request's diagnostic string also redacts the phrase. The caller owns transient UI input and should release the incoming payload after consumption.

The parser accepts `ACTION_PARAM` JSON and individually typed extras, reflecting both formats described in the official guide. It also accepts a JSON-string-wrapped object shown by the official sending example. It rejects oversized or wrongly typed envelopes and trailing non-whitespace content. Responses use the documented `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` broadcast with `packageName` and `response` extras. A successful `respond()` return proves only broadcast submission. It does not prove Gleo receipt, speech output, schema approval, or a live assistant connection. Responses contain no position or account data. [Official code details](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples).

Response statuses are `success`, `asking`, and `fail`; callback messages are English as required by the public schema guideline. Gleo's documented action timeout is seven seconds. MainActivity uses a six-second deadline, one pending screen request, and a policy/deadline recheck before acknowledging UI application. An unavailable vehicle-policy state, timeout, or Activity destruction cancels pending work. A known driving-restricted state instead selects the minimal candidate path. A second accepted command is rejected while the first remains pending. No route operation is acknowledged through Gleo. [Callback guide](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback).

Example local harness (tests Android intent handling, **not** live Gleo voice):

```powershell
adb -P 5039 -s 127.0.0.1:5557 shell am start -W -n com.naekimyeon.pleos/.MainActivity -a FIND_BREAK_PLACE --es need breeze --ei timeMinutes 60
adb -P 5039 -s 127.0.0.1:5557 shell am start -W -n com.naekimyeon.pleos/.MainActivity -a FIND_BREAK_PLACE --es need conversation --ez noTimeLimit true
# This conflicting request must ask for correction and must not change the route.
adb -P 5039 -s 127.0.0.1:5557 shell am start -W -n com.naekimyeon.pleos/.MainActivity -a FIND_BREAK_PLACE --es need shelter --ei timeMinutes 45 --ez noTimeLimit true
```

## Device verification checklist

The official 2.1.0 Windows/Linux archive's `crp_avd_out_x86_64/devices.xml` specifies **2560 × 1440**, `mdpi`, x/y DPI **160**, a 12.8-inch display, landscape default, and 4 GiB RAM. Its profile name is `Pleos Connect v2.1.0` and profile ID is `Pleos_Connect_v2_0`. The SDK repository package is `system-images;android-34;pleos_car_emulator;x86_64`. The archive has one containing directory; its contents belong directly in that package's `x86_64` directory. This metadata was read from the downloaded official archive, without inspecting executable binaries. [Official image repository metadata](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml).

Use Cold boot and 4 GB RAM as the setup guide specifies. The image's `source.properties` also contains an 8192 MB default override, so explicitly setting the documented 4 GB avoids inheriting that value accidentally. GPU mode is `host` in the image metadata. Quick boot is unsuitable for VHAL testing. [Installation guide](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [configuration guide](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator).

- Build and install the signed release APK on Pleos Connect Emulator 2.1.0 / API 34.
- Record the initial SDK state with and without an available default navigation provider.
- Confirm that filtering and Gleo intents never call navigation.
- On a deliberately confirmed navigation action, distinguish sent, confirmed, rejected, and timed-out states.
- Verify route acceptance with the actual sample coordinates; do not stage a successful callback in demo mode.
- Change vehicle restrictions with the official virtual console; check fail-closed unknown state and simplified UI.
- Rotate/resize, background, return, and recreate the Activity; verify preserved preferences and released listeners.
- Exercise each Gleo harness command, malformed JSON, invalid need, fractional time, and conflicting time settings.
- Describe any unavailable platform feature explicitly in the demo recording and release notes.

The official 2.1.0 Virtual Console is available by forwarding emulator port 8088, then opening `http://127.0.0.1:8088/` in a PC browser. Use Emulation Controls > Motion > Gear Selection / Brake Pedal to change vehicle conditions, and observe the resulting platform UX restriction state. Setting a gear is not, itself, proof of a particular restriction policy. The legacy `hmg_car_service` command mentioned in another official FAQ is absent on the tested 2.1.0 user image; privileged car-service shell commands are not used. [Virtual Console setup](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [Virtual Console user guide](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide).

```powershell
adb -P 5039 -s 127.0.0.1:5557 forward tcp:8088 tcp:8088
```

The port/server arguments above identify this workspace's isolated Pleos test device. Do not omit them in this session: another emulator is connected to the default ADB server.
