# AGENT.md — TokiFactor

Guidance for coding agents working in this repository.

## What this project is

TokiFactor is a **Kotlin Multiplatform Compose** chat app for **Android** and **Desktop (JVM)**. Peers exchange encrypted text and images over a public MQTT v5 broker.

There is **no iOS target**, no backend of our own, and **no tests yet**. Product code lives almost entirely in `:shared`; `:androidApp` and `:desktopApp` are thin entry points.

Package: `com.acite.tokifactor`  
App name / window title: `tokifactor`

## Modules

| Module | Role |
|---|---|
| `:shared` | All UI, ViewModels, MQTT, crypto, models. KMP library with `commonMain`, `androidMain`, `jvmMain`. |
| `:androidApp` | `MainActivity` + AndroidManifest + launcher icons. |
| `:desktopApp` | JVM `main()` + Compose Desktop window. |

Gradle includes only these three (`settings.gradle.kts`). Do not add iOS / wasm / native unless asked.

### Where to put code

- **Default: `shared/src/commonMain/kotlin/com/acite/tokifactor/`**
  - `pages/` — Compose screens + ViewModels
  - `services/` — MQTT, crypto, other I/O
  - `model/` — data classes
  - root — `App`, Metro graph, leftover KMP expect/actual
- Platform `actual`s: `shared/src/androidMain/...` and `shared/src/jvmMain/...`
- Android resources/manifest: `androidApp/src/main/`
- Compose Multiplatform resources: `shared/src/commonMain/composeResources/`

`commonMain` currently uses **Java APIs** (HiveMQ client, BouncyCastle, `java.nio`, `javaClass`, `System.currentTimeMillis`). Treat it as JVM-shaped KMP, not pure Kotlin Multiplatform. Do not introduce `android.*` into `commonMain`. Prefer `kotlinx` / expect-actual when adding new platform-sensitive code.

## Stack (source of truth: `gradle/libs.versions.toml`)

Do not hardcode versions in module `build.gradle.kts`. Add catalog entries, then `libs.*` references.

| Piece | Current |
|---|---|
| Gradle | 9.6.1 |
| Kotlin | 2.4.10 |
| AGP | 9.1.1 |
| Compose Multiplatform | 1.11.1 |
| Material3 | 1.11.0-alpha07 |
| JVM target | **25** (Android + shared) |
| Android compile/target SDK | 37 |
| Android minSdk | 24 |
| DI | Metro `1.3.2` + Metrox ViewModel |
| MQTT | HiveMQ MQTT client `1.3.17` (MQTT **v5**, async) |
| Crypto | BouncyCastle `bcprov-jdk18on` |
| Images | Coil 3 |
| File pick/save | FileKit `0.8.8` |
| Config (declared, unused) | ktoml, okio |

Root plugins are declared `apply false` so they load once. Metro is applied on **all three** modules.

## Runtime architecture

```
MainActivity / desktop main()
  createGraph<AppGraph>()
  App(metroViewModelFactory)
    CompositionLocalProvider(LocalMetroViewModelFactory)
      MainPage()  -- metroViewModel() -> MainPageViewModel
                      MqttService (singleton)
                      SecureMessageChannel (object)
```

### DI (Metro)

- Graph: `AppGraph` in `Graphs.kt` — `@DependencyGraph(AppScope::class) interface AppGraph : ViewModelGraph`
- Create graphs with `dev.zacsweers.metro.createGraph<AppGraph>()` in each entry point.
- Pass `appGraph.metroViewModelFactory` into `App`.
- `InjectedViewModelFactory` (`Factory.kt`) `@ContributesBinding(AppScope)` so ViewModels resolve.

**ViewModel recipe** (copy this pattern):

```kotlin
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class FooViewModel(...) : ViewModel()

@Composable
fun FooPage(viewModel: FooViewModel = metroViewModel()) { ... }
```

**Service recipe:**

```kotlin
@SingleIn(AppScope::class)
class FooService @Inject constructor() { ... }
```

Do not use Hilt, Koin, or manual `ViewModelProvider` factories. New injectables must be constructible from `AppGraph` (constructor `@Inject` + Metro contribution annotations). After changing graph/bindings, a Gradle compile is required so Metro codegen runs.

### UI

- Single screen today: `pages/MainPage.kt` — message list + input bar + image attach.
- Cards: `MessageCard` (selectable text, dismiss) and `ImageCard` (Coil `ByteImage`, FileKit save, dismiss).
- Theme: default `MaterialTheme` in `App()`. No custom Color/Typography files yet — add them under `shared` if theming is requested.
- List state: `MainPageViewModel.messages` is a `mutableStateListOf` exposed as `List<ChatMessage>` (explicit backing field). Mutate the list in place; do not replace it with an immutable copy if you want Compose to see adds/removes.
- Images: Coil `AsyncImage` from raw `ByteArray`; disk cache disabled.

### MQTT

`MqttService` (`services/MqttService.kt`):

- Broker default: `broker.hivemq.com:8883` with SSL.
- MQTT v5, `noLocal(true)` on subscribe so the broker does not echo our own publishes.
- Topics: `tokifactor/text`, `tokifactor/picture`.
- Payloads on the wire: **Base64 of ciphertext** (UTF-8 string).
- `subscribe()` returns `Flow<MqttMessage>` via `callbackFlow`.
- `getMaximumPacketSize()` comes from CONNACK restrictions; send path rejects oversized payloads and shows an error card.
- `isConnected()` is `client != null` (not a live ping).

### Crypto

`SecureMessageChannel` is a singleton `object`:

- HKDF-SHA256 from a shared **magic string** (`MainPageViewModel.magicString = "TOKIFACTOR"`) + hardcoded salt + info `"P2P_CHANNEL_V1"`.
- AES-GCM-SIV, 128-bit tag, **fixed 12-byte zero nonce**.

This is **not** production-grade E2E (fixed nonce + shared passphrase + public broker). Do not “improve” crypto unless the user asks. Keep encrypt/decrypt API: `ByteArray` in/out plus magic string.

### Models

- `ChatMessage(id, content, pic: ByteArray?, isError)` — `pic != null` means image card; `isError` uses error container colors.
- `MqttMessage(topic, payload, timestamp)` — inbound MQTT only.

## Commands

From repo root, use the wrapper (do not rely on a system `gradle`):

```bash
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto          # Compose hot reload
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinJvm
./gradlew :shared:compileAndroidMain
```

IDE run configs are also expected (README). `org.gradle.configuration-cache` and caching are on.

## Coding conventions

- Kotlin official style (`kotlin.code.style=official`).
- Match existing files: 4-space indent, no trailing noise, KDoc only when behavior is non-obvious.
- Prefer `viewModelScope.launch(Dispatchers.IO)` for MQTT/file I/O; hop to `Dispatchers.Main` before mutating Compose state.
- Swallow decrypt failures on inbound MQTT (`catch (_: Exception) {}`) — bad packets must not crash the collector. Do not make this louder unless asked.
- File pick/save goes through FileKit (`rememberFilePickerLauncher` / `rememberFileSaverLauncher`), not platform-specific pickers in UI code.
- New Gradle deps: catalog first (`gradle/libs.versions.toml`), then `implementation(libs.…)`.
- Packaging already excludes Netty/META-INF collisions on Android — keep those excludes if you add Netty-adjacent libs.

## Dead / leftover template code

Safe to ignore unless you are cleaning up on request:

- `Greeting.kt`, `GreetingUtil.kt`, `Platform.kt` + android/jvm `actual`s — unused by the chat UI.
- Unused imports / `AnimatedVisibility` / Compose resource painter in `App.kt`.
- `ktoml` and `okio` are on the classpath but unused.
- `kotlinx-serialization` plugin is in the catalog but **not applied** to modules.

Do not delete these in drive-by diffs. Do not extend Greeting as the app’s architecture.

## Constraints and gotchas

- **Public MQTT broker** — topics are shared with anyone on HiveMQ Cloud public broker using the same topic names. Changing topics or broker host is a product decision.
- Android INTERNET (and storage) permissions are already in the manifest.
- HiveMQ + BouncyCastle pull Java/Netty; Android packaging excludes conflicting META-INF files on purpose.
- `ChatMessage.equals` uses `javaClass`; keep it consistent if you touch equality.
- No git repo in this tree at the time of writing; do not assume remotes or CI.
- `local.properties` is gitignored (SDK path). Do not commit secrets there.

## How to implement typical tasks

**New screen:** composable + ViewModel in `pages/`, Metro annotations as above, navigate/call from `App` or `MainPage`. There is no navigation library yet — add one only if multi-screen nav is required.

**New injectable service:** `@SingleIn(AppScope)` + `@Inject constructor`, take it in the ViewModel constructor.

**New MQTT topic:** subscribe in `MainPageViewModel.init` (or a dedicated service), decrypt Base64 → `SecureMessageChannel.decrypt`, then `addMessage` / `addPicture`. Publish the same way as `sendMessage` / `sendPicture` (encrypt → Base64 → size check → `publish`).

**UI-only change:** stay in `MainPage.kt` composables; keep ViewModel as the I/O boundary.

After Kotlin/Metro/graph changes, compile `:shared` before considering the work done.
)

## 