# My MCParks Experience

A Fabric **1.19** client-side mod that enhances the experience of playing on the
[MCParks](https://www.mcparks.us) Minecraft server — streaming in-park audio
from MyMusic+, cleaning up the HUD, and making long ride sessions comfortable.

- **Minecraft:** 1.19
- **Loader:** Fabric (`>=0.14.8`)
- **Java:** 17+
- **Dependencies:** Fabric API, [Cloth Config](https://modrinth.com/mod/cloth-config)
- **Suggests:** [Mod Menu](https://modrinth.com/mod/modmenu)
- **License:** CC0-1.0

---

## Features

### MyMusic+ Audio Streaming
- Streams MCParks MyMusic+ in-park audio (OGG Vorbis + MP3 via `jorbis` / `jlayer`).
- **Auto-connect** when the client joins an MCParks server — no manual step needed.
- Per-client volume control (0–100).
- Commands: `/audioconnect`, `/audiodisconnect`, `/audioreconnect`, `/volume <n>`.
- **Diagnostics:**
  - `/audiolist` — shows every currently-playing track with loop/oneshot kind,
    how long it's been playing, how many times the server triggered it, the
    raw server message, and a short diagnosis (e.g. "server sent one loop
    command; will play until server says stop").
  - `/audiostop <name>` — client-side mute for a specific track, without
    disconnecting the audio session. Useful when the server leaves a stale
    loop running or layers conflicting area music on top of a ride.

### Ride Quality-of-Life
- **Cursor release on ride** — frees the mouse cursor the moment you're a
  passenger, so you can alt-tab, chat, or browse without being pinned by
  look-rotation.
- **Suppresses the "Press SHIFT to dismount" overlay** — which normally flashes
  repeatedly as MCParks transfers the player between entities during inversions.
- **Ride detection + HUD** — recognizes registered rides and renders a small
  on-screen indicator.

### HUD Cleanup
- **Hide health bar** — hides hearts, armor, and hunger (MCParks is PvE-only).
- **Hide vehicle health bar** — hides the mount-health row during coasters.
- **Hide XP level & bar** — reduces visual noise.

### Fullbright (Night Vision + Time Lock)
Four modes via the `fullbrightMode` setting:
- `NONE` — off.
- `ONLY_WHEN_RIDING` — only active while you're a passenger.
- `ONLY_WHEN_NOT_RIDING` — off while riding (useful if you prefer scenic darkness on rides).
- `ALWAYS` — always on.

Implementation injects `NIGHT_VISION` via `LivingEntity.hasEffect` /
`getEffect` and clamps the client day-time at noon to prevent sunset /
nightfall rendering.

### Movement & Utility
- **Auto `/fly`** — automatically runs `/fly` after server join (respects the
  server's own fly permissions; no-ops if the server denies it).
- **Speed multiplier** — configurable client-side sprint/walk multiplier.
- `/nearby` — list nearby players / armor stands for debugging.

### Skin Cache
Persistent on-disk cache of resolved player skin textures — skins reappear
instantly on reconnect instead of re-fetching from Mojang's session server.
Implemented via mixins on `HttpTexture` and `SkullBlockRenderer`.

### Subtitle System
Renders localized subtitles for in-park audio cues on top of the normal HUD.

---

## Configuration

Open the config screen in-game with **`/mymcparks`** (or from Mod Menu if
installed). Settings are persisted to
`config/my-mcparks-experience.json`:

```json
{
  "volume": 20,
  "autoConnect": true,
  "cursorReleaseOnRide": true,
  "hideMountMessageOnRide": true,
  "autoFly": true,
  "speedMultiplier": 1.0,
  "hideHealthBar": true,
  "hideExperienceLevel": true,
  "fullbrightMode": "ALWAYS"
}
```

---

## Server Detection

All MCParks-specific features are **gated to MCParks servers only** — they
stay inert on other servers. Detection checks:

1. `Minecraft.getCurrentServer().ip` — the saved server entry you clicked in
   the multiplayer list.
2. As a fallback, the live connection's remote hostname
   (`Connection.getRemoteAddress()` → `InetSocketAddress.getHostString()`),
   which covers **Direct Connect** and the `--server` launch argument (cases
   where `getCurrentServer()` returns `null`).

A hostname containing `"mcparks"` (case-insensitive) counts as MCParks.

---

## Build & Deploy

Build the mod and copy it into the local Modrinth `Fabric 1.19` profile:

```bash
./build-and-deploy.sh
```

The script runs `./gradlew build`, verifies the jar, and copies
`build/libs/my-mcparks-experience-1.0.0.jar` to
`~/Library/Application Support/ModrinthApp/profiles/Fabric 1.19/mods/`.

For a plain build without deploying:

```bash
./gradlew build
```

---

## Mixin Surface

| Mixin | Target | Purpose |
|---|---|---|
| `GuiMixin` | `net.minecraft.client.gui.Gui` | Hide health / vehicle-health / XP bar; suppress mount overlay |
| `LivingEntityMixin` | `net.minecraft.world.entity.LivingEntity` | Inject `NIGHT_VISION` effect for fullbright |
| `ClientPacketListenerMixin` | `net.minecraft.client.multiplayer.ClientPacketListener` | Clamp incoming day-time to noon so fullbright never blinks dark |
| `MinecraftMixin` | `net.minecraft.client.Minecraft` | Tick hooks, speed multiplier |
| `ChatComponentMixin` | Chat component | Park tracking, per-ride subtitle routing, generic subtitle hooks |
| `SoundOptionsScreenMixin` | Sound-options screen | MyMusic+ volume integration |
| `SkinCacheHttpTextureMixin` / `SkinCacheSkullBlockRendererMixin` | Skin pipeline | Persistent skin cache |

---

## Ride Experience System

Each named ride is represented by a dedicated class under
`ride/experience/rides/` implementing the `RideExperience` interface. Every
experience declares:

- **`park()`** — MCParks park filter (e.g. `"Disneyland Resort"`), matched
  against the park name parsed from the server's
  `"Traveling to <rideId> in <park>"` chat announcement by `ParkTracker`.
- **`isActive(ctx)`** — a programmable predicate over the
  `ExperienceContext` (park, ride id, vehicle, nearby armor-stand models,
  dimension, passenger status).
- **`captureSubtitle(message)`** — optional hook that inspects incoming chat
  messages while the ride is active and turns them into subtitles
  (e.g. Disneyland Railroad strips the `[Narrator] ` prefix and sends the
  rest through `SubtitleManager`).
- **`rideTimeSeconds()` / `onBoard()` / `onDismount()`** — HUD metadata and
  lifecycle hooks.

Experiences take precedence over the JSON-backed `RideRegistry` for HUD name
and ride-time display; the JSON registry remains the fallback for rides
without a dedicated class.

---

## Project Layout

```
src/main/java/com/chenweikeng/mcparks/
├── MCParksExperienceClient.java   # client entrypoint & command registration
├── audio/                         # MyMusic+ streaming (jorbis / jlayer)
├── config/                        # Cloth Config screen + persisted ConfigSetting
├── cursor/                        # CursorManager — mouse release while riding
├── fly/                           # Auto /fly
├── fullbright/                    # Fullbright mode + day-time lock
├── mixin/                         # All mixins (see table above)
├── ride/                          # Ride registry, detector, HUD renderer
│   └── experience/                # Programmable per-ride filters (RideExperience + rides/)
├── skincache/                     # Persistent skin texture cache
└── subtitle/                      # Subtitle manager + renderer
```

---

## License

CC0-1.0 — see [LICENSE](LICENSE). Not affiliated with, endorsed by, or
sponsored by MCParks, Mojang, Microsoft, or The Walt Disney Company.
