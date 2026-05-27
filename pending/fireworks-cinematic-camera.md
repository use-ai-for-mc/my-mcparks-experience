# Fireworks Cinematic Camera (a.k.a. "Castle Viewpoint")

> **Status:** Prototype, merged to `main` (commit `6c52251`). The engine works and is
> manually toggleable, but the feature is **not yet gated or shipped as a user feature** —
> there is no castle viewpoint preset, no time/location activation, and no config UI.
> This doc is the handoff for picking the work back up later.

---

## 1. Goal

During MCParks fireworks shows there are great vantage points to watch the castle. We want
a **client-side detached camera** that places the viewer at a curated spot facing the castle,
**without** physically moving the player entity. The viewer can then pan/rotate freely **within
a small bounded box** for framing — but cannot fly off across the map.

This is "freecam, but on a leash."

## 2. MCParks policy context (IMPORTANT)

- MCParks staff were asked directly: **full Freecam is explicitly banned.**
- Staff indicated a **purpose-specific, bounded** camera tool (small closed region, for show
  viewing, no gameplay advantage) **should be acceptable.**
- Design consequences that MUST be preserved:
  - Movement is **hard-clamped to a small AABB** around the viewpoint (see `Viewpoint.clamp`).
  - **No** no-clip / unbounded flight / "freeze player and roam" mode.
  - The code carries **no "freecam" naming** anywhere (classes, strings, logs, keybind) — it's
    "cinematic" / "viewpoint". Keep it that way if staff ever inspect the jar.
  - Long-term intent: only **activate during the scheduled fireworks window** and **near the
    castle** (activation gating — not yet built; see §8).

## 3. How it works

Standard "swap the camera entity" approach (same core technique as Freecam, heavily trimmed):

1. A **client-only dummy player** (`CinematicCamera extends LocalPlayer`) is spawned into the
   client world. Its `ClientPacketListener.send()` is a **no-op**, so it never talks to the
   server — the real player keeps standing where they were, fully in sync with the server.
2. `MinecraftClient#setCameraEntity(dummy)` makes the game render/listen from the dummy.
3. **Mouse look** is redirected to the dummy by intercepting `Entity#turn` (see
   `CinematicEntityMixin`) — vanilla calls `player.turn()`, we forward it to the camera.
4. **WASD / space / shift** are read by the dummy's own `KeyboardInput` and translated to a
   velocity in `CinematicMotion`. The real player's input is blanked each tick in
   `CinematicCameraManager.tick()` so the body doesn't move.
5. After vanilla movement + collision runs (`super.aiStep()`), the dummy's position is
   **clamped to the viewpoint AABB** in `CinematicCamera.aiStep()`.
6. On disable, the camera entity is restored to the real player, perspective/`smartCull`/hand
   rendering are restored.

### Why `smartCull = false`?
The camera renders the world from a spot offset from the real player. With smart culling on,
chunks "behind" the player can be culled and show as void. Disabling it while active fixes that.

### The hard constraint: chunk loading
The server only sends chunks within render distance **of the real player's body**. If the
viewpoint (or where you pan to) is farther than render distance from the player, you see void.
**The viewpoint must be within render distance of where the player physically stands.** For the
castle this is fine if the player is in the normal viewing area. Document this for users.

## 4. File map

All under `src/main/java/com/chenweikeng/mcparks/`:

| File | Role |
|---|---|
| `cinematic/CinematicCamera.java` | Dummy `LocalPlayer`; silent network; AABB clamp in `aiStep()`; silences player-ish side effects (fall damage, water, climbing, pose). |
| `cinematic/CinematicMotion.java` | WASD/space/shift → velocity vector. Hardcoded `H_SPEED=0.15`, `V_SPEED=0.10`. No sprint multiplier. |
| `cinematic/Viewpoint.java` | Record: `name, origin (Vec3), yaw, pitch, bounds (AABB)`. `centered(...)` factory + `clamp(Vec3)`. |
| `cinematic/CinematicCameraManager.java` | Singleton orchestrator: `enable/disable/toggle/tick/onDisconnect`. Camera-entity swap, perspective save/restore, input blanking. `makePlayerAnchoredViewpoint()` is the dev-only prototype viewpoint. |
| `mixin/CinematicEntityMixin.java` | Redirect `Entity#turn` → camera; cancel push between player and camera. |
| `mixin/CinematicGameRendererMixin.java` | Hide hovered-block outline while active. |
| `mixin/CinematicGuiMixin.java` | `getCameraPlayer` → real player (HUD health/hunger/XP stay correct). |
| `mixin/CinematicEntityRenderDispatcherMixin.java` | Never render the dummy (prevents Iris shadow). |
| `mixin/CinematicCameraMixin.java` | Snap eye-height instantly on toggle (no nausea slide). |
| `mixin/CinematicMinecraftMixin.java` | Cancel attack/pick/break interactions while active; teardown on `clearLevel`. |

Integration touch-points elsewhere:
- `MCParksExperienceClient.java` — registers keybind **V** (`key.my-mcparks-experience.cinematic_toggle`), calls `tickCinematicCamera()`, tears down on disconnect.
- `cursor/CursorManager.java` — while cinematic active, releases the cursor and **re-releases on right-click** (mirrors on-ride behavior), re-grabs on exit.
- `mixin/LivingEntityMixin.java` + `fullbright/DayTimeHandler.java` — **fullbright is suppressed** while cinematic is active (fireworks are night-time; we don't want forced noon or fake night-vision).
- `my-mcparks-experience.mixins.json` — all six `Cinematic*` mixins registered.
- `assets/.../lang/en_us.json` — keybind + category strings.

## 5. Attribution

Engine adapted from **MinecraftFreecam/Freecam**, `1.19.2` branch, **MIT licensed**.
https://github.com/MinecraftFreecam/Freecam — same mappings (Mojmap) as this project, so code
was ported with minimal translation. If we ship this, preserve an MIT attribution notice.

## 6. What was deliberately NOT copied from Freecam

- `freezePlayer` injectors (desyncs the real player from the server — anticheat-hostile).
- Player-control mode / `switchControls` (drive the body while watching through the camera).
- Tripod system (hotbar-chord saved camera slots).
- Server whitelist/blacklist machinery.
- No-clip / unbounded flight / user-configurable flight speed.
- Hand-tracking-the-camera item render (we just hide the hand instead).

## 7. Current prototype controls & tuning knobs

- **Toggle:** press **V** in-game. Enables a viewpoint **anchored at the player's current
  position + eye height**, facing the player's current yaw/pitch, with a **5×3×5 box**
  (half-extents 2.5 / 1.5 / 2.5). Press **V** again to release.
- This player-anchored viewpoint is a **prototype stand-in** (`makePlayerAnchoredViewpoint`).
  The real feature needs fixed castle presets (see §8).
- Tuning knobs:
  - Speed: `CinematicMotion.H_SPEED` / `V_SPEED`.
  - Box size: the half-extents passed to `Viewpoint.centered(...)`.

## 8. Open TODOs / future work (the actual "pending" items)

1. **Castle viewpoint preset(s).** Go in-game during a show, find the best spot, capture exact
   `origin` (x,y,z) + `yaw`/`pitch`, and define one or more fixed `Viewpoint`s. Replace the
   player-anchored prototype with a real preset (or a "nearest preset" picker).
2. **Tune the AABB.** Decide how much pan freedom feels good without becoming "roam." Likely
   smaller than the prototype 5×3×5.
3. **Activation gating** (the staff-compliance story):
   - Only allow enabling during the **scheduled fireworks window** (reuse Show Times data?).
   - Only allow enabling when the player is **physically near the castle viewpoint** (within a
     radius / same region), so it can't be used as general freecam elsewhere.
   - Auto-disable when the show ends or the player leaves the region.
4. **Multiple viewpoints + config.** JSON config (like ride data) so spots can be added without
   recompiling; maybe cycle presets with a key.
5. **Boundary feedback.** Subtle visual cue when you hit the AABB edge or approach the
   chunk-loading limit (screen-edge fade), so the void constraint isn't jarring.
6. **Per-park support.** Castle differs across parks (Disneyland vs WDW Magic Kingdom, etc.).
   Key presets off the park code (`ParkTracker`).
7. **Config toggle / ModMenu entry** to enable the whole feature, default off until vetted.

## 9. Testing checklist (when resuming)

- [ ] Toggle on/off is clean — no camera lurch (eye-height snap working).
- [ ] WASD speed + box size feel right for framing.
- [ ] HUD still shows the **real** player's health/hunger/XP.
- [ ] Hand hidden, no block outline, no crosshair weirdness, no dummy shadow under Iris.
- [ ] Cursor releases and **stays released on right-click**; re-grabs on exit.
- [ ] Fullbright does NOT kick in while active (night stays night).
- [ ] Real player stays put server-side (no movement packets; no kick).
- [ ] Panning to the box edge near render-distance limit doesn't reveal void inside the box.
- [ ] No desync / rubber-banding when disabling after a long session.

## 10. Risks / watch-items

- **Anticheat:** we never freeze or teleport the real player and send no fake packets, so this
  should be invisible server-side. Re-verify after any change that touches the real player.
- **Mapping/version:** ported from 1.19.2 Mojmap. Any MC version bump needs the `Entity#turn`,
  `Camera#setup`, `GameRenderer#shouldRenderBlockOutline`, and `Gui#getCameraPlayer` targets
  re-checked.
- **Chunk-loading void** is a physical limit, not a bug — keep viewpoints close to the body.
