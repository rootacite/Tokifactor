<p align="center">
  <img src="tokifactor_clip.png" alt="tokifactor logo" width="168"/>
</p>

<h1 align="center">tokifactor</h1>

<p align="center">
  <strong>Encrypted chat · one magic string · no server of our own</strong><br/>
  Two (or more) devices that share a passphrase can exchange encrypted text, mixed text-plus-image messages, images, and files over a public MQTT v5 broker.
</p>

<p align="center">
  Android · Desktop (JVM) · Kotlin Multiplatform · Material 3
</p>

---

## What this is

TokiFactor is a small Kotlin Multiplatform chat client for **Android** and **Desktop (JVM)**. Two (or more) devices that share a passphrase can exchange encrypted text, images, and files over a **public MQTT v5 broker** — no account, no phone number, and **no server of our own**.

TokiFactor is a convenience tool: spin up a shared channel on infrastructure that already exists on the public internet. It is **not** a replacement for Signal, iMessage, or a self-hosted messenger.

## Why it exists

Running a private chat stack (TURN, a message store, TLS termination, user accounts) is a lot of work when all you want is “we both open an app and talk.” MQTT public brokers already punch through NATs, accept anonymous clients, and fan messages out to whoever is subscribed.

TokiFactor sits on top of that:

1. You pick a public broker (HiveMQ, EMQX, Mosquitto, and several China-reachable nodes are built in).
2. You agree on a **magic string** — a shared secret, not a login.
3. The app encrypts every payload before publish, so the broker only sees ciphertext, topic names, and timing.

The channel exists for as long as clients stay connected. There is nothing to deploy on the far side except this app.

## Features

- Encrypted **text** chat in a Line/WeChat-style bubble list.
- On Linux, messages are received by the `tokifactord` user service even when the
  desktop UI is closed; the Compose app reconnects over a local Unix socket.
- Incoming human messages raise an XDG notification through `notify-send`, using
  the sender's cached avatar as the notification icon when available.
- **Multi-agent member bar:** agents answering the encrypted control probe on the
  same broker and magic string appear in a compact, horizontally scrollable member
  bar with avatars and status dots. The second line shows the selected member's
  model, reasoning effort, context usage, queue depth, and pending approvals.
  Active work is shown in the member bar instead of the message list, and selecting
  `Info` and every member avatar open a full member profile. Tokifactor users also
  announce a profile, so humans and agents share the same roster. Without remote
  members, the app stays a normal tokifactor chat client.
- **Solo time:** open an agent profile and choose **Solo time** to switch to that
  member's derived private channel. The group connection stays alive, so group
  messages and notifications continue to arrive; aki-layer remains owned by Aki.
  The conversation
  panel separates the persistent group history from each member's shared solo history;
  historical solo sessions are read-only until you resume them. `akictl solo` uses the
  same retained-control handshake.
- Agent-aware structured replies: Markdown, progress status, tool cards, and inline
  tool-approval buttons for the `akid.v1` envelope.
- Agent replies with multiple blank-line-separated paragraphs render as separate
  bubbles; Markdown lists, fenced/indented code blocks, and block quotes stay intact,
  and selection/delete still target the original message.
- Consecutive tool cards and approvals from the same turn collapse into one expandable
  group; pending approvals auto-expand so they stay actionable.
- **Photos** and **arbitrary files**, sent in chunks with size + percent progress, cancel on the sender, and a system chip on the receiver if the peer cancels, fails, or times out.
- **Display name**, **avatar**, and channel-visible **introduction** (shown next to
  your bubbles and on your member profile).
- **Broker picker** with live latency probes (including TLS vs plain TCP) so you can choose a node that actually reaches you.
- Four KataHana-inspired **dark appearances** (Sky & Sakura, Ink & Paper,
  Midnight & Snow, Lilac & Peach) with a lavender-glass visual system.
- Settings, chat history, photos, and files persist locally (magic string, immutable
  member id, identity-bound flag, name, introduction, broker, device id, avatar, text
  size, and appearance included).

There is no iOS, Wasm, or native-mobile target. There is no backend and no user directory.

## How it works

```
Android: MainActivity ─► AppGraph ─► ChatEngine ─► HiveMQ v5 ─► broker

Linux:   CMP UI ─► DesktopChatSession ─► Unix socket ─► tokifactord.service
                                                        │
                                                        ├─► ChatEngine
                                                        ├─► ChatHistoryStore
                                                        ├─► XDG notifications
                                                        └─► HiveMQ v5 ─► broker
```

On Linux the daemon is the only process that subscribes to MQTT chat topics and
the only writer of chat history, peer avatars, and transfer caches. The Compose
app mirrors daemon snapshots/events and sends commands (send, delete, cancel,
clear history, approvals) back over `$XDG_RUNTIME_DIR/tokifactor/tokifactord.sock`.
Other desktop operating systems continue to use the embedded engine.

Almost all product code lives in `:shared` (`commonMain` is JVM-shaped Kotlin: HiveMQ, BouncyCastle, `java.nio`). `:androidApp` and `:desktopApp` are thin entry points.

### Transport

- The Linux daemon owns the persistent MQTT session, including text, pictures,
  files, avatars, Aki presence, and control messages. The desktop app does not
  subscribe to those topics.
- MQTT **v5** with a 30-day persistent session and a stable per-device/channel client ID. The broker queues `text`, `picture`, and `file` messages while the app is offline.
- `text` / `picture` / `file` use **QoS 1**; the ephemeral `control` and `avatar` topics use **QoS 0**. All subscriptions use `noLocal(true)` so the broker does not echo our own publishes.
- Text envelopes carry a deterministic message UUID derived from sender, send time, and content. The receiver drops duplicate deliveries, including duplicates replayed after an app restart.
- Chat bubbles are inserted by their sender timestamp rather than arrival order. MQTT does not provide a global clock or ordering across publishers, so clock skew can still affect relative order.
- Wire payload: **UTF-8 Base64 of `nonce || ciphertext`** (12-byte random nonce, then AES-GCM-SIV output).
- Default broker: `broker.hivemq.com:8883` (TLS). You can switch in Settings; probes are cached so the list stays usable offline.
- Text, pictures, files, avatars, and the encrypted Aki control probe use **separate topics**
  so an older client does not try to render a file as an image, and clients without Aki
  support can ignore the control topic.

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

Each install keeps a local immutable `memberId` in `config.toml`. A fresh install gets
`mem_<uuid4hex>`; an existing install migrates once to the deterministic
`legacy_<sha256("tokifactor-member-v1\0" + NFKC(trim(lower(Name))))[:32]>`, so PC and
Android converge on the same entity without any central server. `deviceId` remains the
local transport/avatar source, not the channel identity. The current display Name is a
label: `identities.json` beside the config records aliases and label ownership for this
client only. Members are keyed by `memberId`; control messages still carry
`agentId=memberId` and `agentName=memberName` for older clients, plus new
`memberId`/`memberName` fields. The first source that announces an id owns its profile;
later sources only add liveness. Text/file/image envelopes, chunk metadata, avatar ids,
and local history use `memberId`, so messages from another same-id device render as
your own (`isMine=true`). Avatars are encrypted on `tokifactor/avatar` and cached by
member id. On startup old Name/device keys in history and the avatar cache are migrated,
and the current Name from the local registry is used for display.

Introductions are encrypted control metadata (`description`) alongside
`memberKind=user` for Tokifactor clients and `memberKind=agent` for Aki gateways. A
missing `memberKind` is treated as `agent` for older clients. Introductions allow
newlines and are limited to 512 weighted units: ASCII counts as 1, while CJK,
full-width characters, and emoji count as 2. That means 512 English characters or
256 Chinese characters. Settings shows the live counter and explains that everyone
holding the same broker and magic string can see the introduction. It is **not
private**. Labelling your profile with a Name already owned by another member id is
handled locally: an unbound fresh client silently adopts that member id, Name,
introduction, aliases, and cached avatar; a bound client shows a confirmation dialog
because old messages stay with the old id and are no longer rendered as yours.
Renaming to a free label keeps the id and permanently retains the old Name as an alias.
The registry is client-local and never authoritative; a conflicting remote id can stay
online by id, but it cannot take a label from its recorded owner.

## How to use

1. Install tokifactor on every device that should join the channel.
2. Open **Settings** (gear on the chat bar).
3. Set a **magic string** that is *not* the default, and share it out of band (in person, another messenger, etc.). Both sides must match **exactly**.
4. Pick the same **MQTT broker**. Prefer TLS. Use the latency marks if one region is unreachable.
5. Optionally set a display name, avatar, introduction, and text size.
6. Wait until the send and attach buttons enable (connected). Then type, or attach a photo / file.

If any Tokifactor client or Aki gateway is listening on the same broker and magic
string, tokifactor probes `tokifactor/control` every 15 seconds while empty and every
20 seconds once members are present. Members are keyed by immutable `memberId`, time
out after 30 seconds without a heartbeat, and are shown in the top member bar. The default
selection prefers the first `Aki` and falls back to the next online member when it
goes offline. Tap a member avatar or `Info` to open its full profile; tap the chip
text to select it for the compact status row. Your own avatar opens your profile with
an **Edit profile** shortcut. The page keeps the member snapshot taken when it
opened, so a transient disconnect does not blank it. Configure the broker and magic
manually; tokifactor does not ship an Aki-specific broker preset.

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

- Offline delivery depends on the broker keeping the persistent session and queued QoS 1 messages. A client that has never completed a subscription, or whose 30-day session has expired, cannot receive older messages.
- Public brokers rate-limit, expire sessions, and go down. QoS 1 plus a persistent session is at-least-once transport, not a guaranteed durable inbox.
- Older builds still interoperate, but their messages carry neither a stable message ID nor a sender timestamp, so duplicates cannot be identified and ordering falls back to arrival time.
- Chat history and completed media are stored locally and restored on startup.
- On Linux, chat history is written by `tokifactord`; the UI keeps a read-only
  local copy while the daemon is unavailable and replaces it with the daemon
  snapshot after reconnecting.
- Incomplete file transfers time out after 5 seconds of silence; large or lossy links may fail more often.
- `isConnected()` is “we still have a client object,” not a live ping.
- No iOS. No accounts, general user presence, typing indicators, or read receipts.
- `commonMain` uses Java APIs; this is not a pure Kotlin Multiplatform library you can drop onto native/JS as-is.

If you need real confidentiality, run your own broker with authentication, use a proper messenger, or both.

## Requirements

| Piece | Version / note |
| --- | --- |
| JDK | **25** (Android + Desktop compile target) |
| Android | minSdk 24, compile/target SDK 37 |
| Gradle | 9.6.1 via `./gradlew` — do not rely on a system `gradle` |
| Android SDK | set `sdk.dir` in `local.properties` (gitignored) |
| Linux daemon | systemd user manager and `systemctl --user` |
| Notifications | `notify-send` from libnotify (warned about, but not required, at install time) |

Network access is required at runtime. Android already declares `INTERNET` (and legacy storage permissions for file save/pick).

## Building and running

From the repo root:

```bash
# Desktop
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto          # Compose hot reload

# Linux daemon (foreground; normally started by systemd)
./gradlew :desktopApp:packageUberJarForCurrentOS
java -jar desktopApp/build/compose/jars/*.jar --daemon

# Android debug APK
./gradlew :androidApp:assembleDebug

# Library only (useful after Metro / graph changes)
./gradlew :shared:compileKotlinJvm
./gradlew :shared:compileAndroidMain
```

IDE run configurations from the Compose Multiplatform template should also work.

### Linux installation

`install.sh` installs the desktop entry, the daemon jar/launcher, and the
`tokifactord.service` user unit, then enables and starts the service:

```bash
./install.sh
systemctl --user status tokifactord.service
journalctl --user -u tokifactord.service -f
./install.sh -u   # stop/disable daemon and remove installed files
```

The daemon follows the login session and does not enable lingering. Its XDG
notifications require a reachable user D-Bus session and `notify-send`; if the
command is missing, messages are still received and stored, but no popup appears.
Chat data in `~/.tokifactor` is preserved by uninstall.

### Packages

```bash
# Desktop installer for the current OS (dmg / msi / deb, as configured)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Android release APK (debug-signed in this repo’s Gradle config)
./gradlew :androidApp:assembleRelease
```

There is nothing to deploy on a server. “Deployment” is: install the APK or desktop package on each device, pick the same broker, set the same magic string.

## Code layout

| Module | Role |
| --- | --- |
| `:shared` | UI, ViewModels, MQTT, crypto, models (Compose Multiplatform) |
| `:androidApp` | `MainActivity`, manifest, launcher icons |
| `:desktopApp` | JVM `main()`, Compose Desktop window, and `--daemon` entry point |

Stack: Kotlin 2.4, Compose Multiplatform 1.11, Material 3, Metro DI, HiveMQ MQTT client, BouncyCastle, Coil 3, FileKit.

## License

GPL v2
