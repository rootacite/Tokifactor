# tokifactor

A small Kotlin Multiplatform chat client for **Android** and **Desktop (JVM)**. Two (or more) devices that share a passphrase can exchange encrypted text, images, and files over a **public MQTT v5 broker** — no account, no phone number, and **no server of our own**.

TokiFactor is a convenience tool: spin up a shared channel on infrastructure that already exists on the public internet. It is **not** a replacement for Signal, iMessage, or a self-hosted messenger.

## Why it exists

Running a private chat stack (TURN, a message store, TLS termination, user accounts) is a lot of work when all you want is “we both open an app and talk.” MQTT public brokers already punch through NATs, accept anonymous clients, and fan messages out to whoever is subscribed.

TokiFactor sits on top of that:

1. You pick a public broker (HiveMQ, EMQX, Mosquitto, and several China-reachable nodes are built in).
2. You agree on a **magic string** — a shared secret, not a login.
3. The app encrypts every payload before publish, so the broker only sees ciphertext, topic names, and timing.

The channel exists for as long as clients stay connected. There is nothing to deploy on the far side except this app.

## What it does

- Encrypted **text** chat in a Line/WeChat-style bubble list.
- **Photos** and **arbitrary files**, sent in chunks with size + percent progress, cancel on the sender, and a system chip on the receiver if the peer cancels, fails, or times out.
- **Display name** and **avatar** (shown next to your bubbles on the other device).
- **Broker picker** with live latency probes (including TLS vs plain TCP) so you can choose a node that actually reaches you.
- Light and dark **Material 3** theme that follows the system appearance.
- Settings persist locally (magic string, name, broker, device id, avatar). Chat history is **in memory** and gone when the process dies.

There is no iOS, Wasm, or native-mobile target. There is no backend, no user directory, and no tests yet.

## How it works

```
Android MainActivity / Desktop main()
        │
        ▼
   AppGraph (Metro DI)
        │
        ▼
   Compose UI  ──►  MainPageViewModel
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
     MqttService   SettingsStore  SecureMessageChannel
     (HiveMQ v5)   (local TOML)   (BouncyCastle)
          │
          ▼
   public MQTT broker
     tokifactor/text | picture | file | avatar
```

Almost all product code lives in `:shared` (`commonMain` is JVM-shaped Kotlin: HiveMQ, BouncyCastle, `java.nio`). `:androidApp` and `:desktopApp` are thin entry points.

### Transport

- MQTT **v5**, QoS 1, `noLocal(true)` so the broker does not echo our own publishes.
- Wire payload: **UTF-8 Base64 of `nonce || ciphertext`** (12-byte random nonce, then AES-GCM-SIV output).
- Default broker: `broker.hivemq.com:8883` (TLS). You can switch in Settings; probes are cached so the list stays usable offline.
- Text, pictures, files, and avatars use **separate topics** so an older client does not try to render a file as an image.

### Crypto

`SecureMessageChannel` derives a 256-bit AES key with **HKDF-SHA256** from:

- the magic string (IKM),
- a hardcoded salt,
- info `"P2P_CHANNEL_V1"`.

Each payload is encrypted with **AES-GCM-SIV** (128-bit tag) under a **fresh 12-byte random nonce**. The nonce is prepended to the ciphertext and sent with the MQTT message so the receiver can decrypt; it is not secret. Wrong magic strings fail authentication and are dropped silently.

This wire format is **not compatible** with older builds that used a fixed all-zero nonce. Both peers need a current app.

This is a **shared-passphrase** scheme, not modern E2E. See [Limitations](#limitations).

### Images and files

Large binaries are not stuffed into one MQTT packet:

- Source is copied to a disk cache (`transfers/` next to the config file).
- Plaintext is split into chunks of at most **8 KiB** (further limited by the broker’s `Maximum Packet Size`).
- Each chunk is encrypted on its own, then published with bounded concurrency.
- Integrity is a SHA-256 of the full plaintext.
- Incomplete receives time out after **5 seconds with no new accepted chunk**.
- Abort is published on both `tokifactor/file` and `tokifactor/picture` so the other side can stop.

Images over 8 MiB are shown with the file bubble instead of an in-chat preview.

### Identity

Each install gets a random `deviceId`. Display name defaults to the OS device name. Avatars are encrypted on `tokifactor/avatar` and cached per sender id on the receiver.

## Requirements

| Piece | Version / note |
| --- | --- |
| JDK | **25** (Android + Desktop compile target) |
| Android | minSdk 24, compile/target SDK 37 |
| Gradle | 9.6.1 via `./gradlew` — do not rely on a system `gradle` |
| Android SDK | set `sdk.dir` in `local.properties` (gitignored) |

Network access is required at runtime. Android already declares `INTERNET` (and legacy storage permissions for file save/pick).

## Build and run

From the repo root:

```bash
# Desktop
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto          # Compose hot reload

# Android debug APK
./gradlew :androidApp:assembleDebug

# Library only (useful after Metro / graph changes)
./gradlew :shared:compileKotlinJvm
./gradlew :shared:compileAndroidMain
```

IDE run configurations from the Compose Multiplatform template should also work.

### Packages

```bash
# Desktop installer for the current OS (dmg / msi / deb, as configured)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Android release APK (debug-signed in this repo’s Gradle config)
./gradlew :androidApp:assembleRelease
```

There is nothing to deploy on a server. “Deployment” is: install the APK or desktop package on each device, pick the same broker, set the same magic string.

## How to use

1. Install tokifactor on every device that should join the channel.
2. Open **Settings** (gear on the chat bar).
3. Set a **magic string** that is *not* the default, and share it out of band (in person, another messenger, etc.). Both sides must match **exactly**.
4. Pick the same **MQTT broker**. Prefer TLS. Use the latency marks if one region is unreachable.
5. Optionally set a display name and avatar.
6. Wait until the send and attach buttons enable (connected). Then type, or attach a photo / file.

Peers who use a different magic string will not decrypt your traffic (and you will not see theirs). Peers who use a different broker are on a different bus.

Both sides should run a **compatible app build**. Current picture/file transfer encrypts **per chunk**, and ciphertext is `nonce || body`. Older builds that used a whole-file ciphertext or a fixed nonce will not interoperate.

## Limitations

Treat this as a hobby / LAN-replacement-over-MQTT client.

**Security**

- The magic string is a **group password**. Anyone who knows it and subscribes to the same topics can read and write. There is no per-user key, no forward secrecy, and no authentication of devices.
- The default magic string `TOKIFACTOR` is public. Leave it unchanged and you share a channel with every other default install on that broker.
- AES-GCM-SIV uses a **random 12-byte nonce per encrypt**, carried in the clear at the front of the packet. Nonce reuse is extremely unlikely with `SecureRandom`, but the scheme still has no forward secrecy.
- The broker is **untrusted**. It can log topics, sizes, timestamps, client IPs, and ciphertext. TLS (when you pick a TLS broker) only protects the hop to the broker, not the broker itself.
- Topics (`tokifactor/text`, …) are **global names** on that public broker, not a private namespace. Changing them is a product decision; they are not a secret.
- Decrypt failures are swallowed so garbage on the topic cannot crash the collector — you simply never see those packets.

**Reliability and product**

- Public brokers rate-limit, drop clients, and go down. Delivery is MQTT QoS 1, not a durable inbox.
- Chat is not stored. Kill the app and the transcript is gone.
- Incomplete file transfers time out after 5 seconds of silence; large or lossy links may fail more often.
- `isConnected()` is “we still have a client object,” not a live ping.
- No iOS. No accounts, presence, typing indicators, or read receipts.
- `commonMain` uses Java APIs; this is not a pure Kotlin Multiplatform library you can drop onto native/JS as-is.

If you need real confidentiality, run your own broker with authentication, use a proper messenger, or both.

## Project layout

| Module | Role |
| --- | --- |
| `:shared` | UI, ViewModels, MQTT, crypto, models (Compose Multiplatform) |
| `:androidApp` | `MainActivity`, manifest, launcher icons |
| `:desktopApp` | JVM `main()` and Compose Desktop window |

Stack: Kotlin 2.4, Compose Multiplatform 1.11, Material 3, Metro DI, HiveMQ MQTT client, BouncyCastle, Coil 3, FileKit.

## License

GPL v2
