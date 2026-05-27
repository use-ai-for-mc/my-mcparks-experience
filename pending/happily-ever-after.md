# Happily Ever After (WDW Magic Kingdom)

> **Status:** Detection + HUD wiring implemented and built; **subtitle text
> is NOT written yet** — both JSONs ship with `<<TODO: trackname>>`
> placeholders. The two experiences activate correctly (audio + castle
> location gates), the preshow countdown HUD renders, and timed-subtitle
> playback is wired — it just has nothing to display until the placeholder
> entries are replaced with real captions. This doc is the handoff for that
> authoring pass plus two open audio questions.
>
> Sibling reference: **Remember… Dreams Come True** (Disneyland) was built
> the same session with the same pattern and *does* have real subtitles
> (its dialogue is chat-published; HEA's is not — see §3).

---

## 1. What it is

The Walt Disney World Magic Kingdom nighttime fireworks spectacular in front
of Cinderella Castle. A **theater show, not a vehicle ride**: guests stand in
the hub watching the castle, there is no boarding event, and `isPassenger`
stays `false` throughout — so detection keys entirely off audio + location,
and the HUD uses the `isTheaterMode()` path (see §4).

Park: `Walt Disney World Resort`. Viewing spot: the Magic Kingdom castle
forecourt, detected via the existing `FireworkLocations.MAGIC_KINGDOM` Walt
("Partners") statue check at `(-247, 55, 759.5)` / helmet
`walt_statue_head.png`.

The experience is split into two phases / two classes:

- **Preshow** (`HappilyEverAfterPreshow`) — the 10-minute countdown.
- **Main show** (`HappilyEverAfter`) — the ~18-minute pyrotechnic show.

Between them is a ~5-minute narration window that is **intentionally not
gated** (see §2).

## 2. Detection design (audio allowlist + castle location)

Both classes AND two conditions: the right HEA-namespaced audio track is
active, **and** the player is at the Magic Kingdom castle
(`FireworkLocations.MAGIC_KINGDOM.isHereNow(ctx.mc)`). MCParks audio is
geo-fenced, so the audio gate is already implicitly local; the location
AND-condition is belt-and-suspenders per Weikeng's request ("geo-fenced
gates, so it should be good for detection, but we should still gate based on
the location that we have").

**Main show** — fires on any of:
```
HEA/1SFX  HEA/2SFX  HEA/3SFX  HEA/4SFX  HEA/5SFX  HEA/6SFX  HEA/7SFX
```
The seven segments play strictly sequentially 1→7 with sub-second
server-side stop-then-trigger handoffs, so any one active keeps the gate
continuously hot across the whole show — no debounce needed.

**Preshow** — fires on any of:
```
HEA/Preshow10Min   HEA/Preshow5Min
```

### Deliberately excluded from both gates
- `MK/Shows/SpecialOpening`, `…/DarrenNeutral`, `…/RyanNeutral` — the opening
  narration bed + cast-member voiceovers. **Shared with Wishes / Enchantment
  / other castle shows**, so gating on them would false-positive HEA on
  non-HEA nights. This is why the ~5-minute narration window between preshow
  and `HEA/1SFX` is dead time as far as the mod is concerned.
- `Anniversary/MomentousTag`, `Anniversary/CelebrateYou` — the closing music
  tail. Weikeng confirmed HEA always ends with these, but they also fire at
  non-HEA anniversary contexts, so they're excluded (answer to gate-extension
  question was "N/A"). The experience ends cleanly ~14 s after `HEA/7SFX`
  stops.

### Confirmed by Weikeng
1. **No HEA variants** — the seven `HEA/<n>SFX` names are the whole show; no
   holiday/special-event alternates to glob for.
2. **Strict 1→7 order**, every show.
3. **Identical every night** — same tracks, same sequence.
4. **Geo-fenced** — location gate added as confirmation.

## 3. Timed subtitles — SKELETON ONLY

Unlike Tiki Room / RDCT, **HEA does not publish its dialogue as chat**. The
2026-04-25 capture contained only audio events — no `[Speaker]` lines for the
SFX segments. So the JSONs were generated from **audio timing alone**: one
placeholder entry per track, `endMs` capped at the observed audible duration,
text = `<<TODO: trackname>>`. Someone has to listen to the MP3s and write the
captions (atmospheric cues like `[fireworks crescendo]` and/or any sung
lyrics).

Because there's no chat, **neither class needs `captureSubtitle()`** — there
are no chat duplicates to suppress. (Contrast RDCT, which overrides it to hide
its bracketed dialogue.)

### `hea.json` — main show (anchor `HEA/1SFX` = T+0, `totalRideTimeSec` 1102)

| Track | rideOffsetMs | Audible dur (ms) | Notes |
|-------|-------------:|-----------------:|-------|
| `HEA/1SFX` |      0 | 146538 | |
| `HEA/2SFX` | 148000 | 252227 | **outlier — ~4m12s vs 2–2.5m for the rest** |
| `HEA/3SFX` | 402000 | 160754 | |
| `HEA/4SFX` | 564000 | 156646 | |
| `HEA/5SFX` | 722000 | 134741 | |
| `HEA/6SFX` | 859000 | 131612 | |
| `HEA/7SFX` | 992000 | 108101 | finale |

### `hea_preshow.json` — preshow (anchor `HEA/Preshow10Min` = T+0, `totalRideTimeSec` 916)

| Track | rideOffsetMs | Audible dur (ms) | Notes |
|-------|-------------:|-----------------:|-------|
| `HEA/Preshow10Min` |      0 | 296655 | T-10 announcement |
| `HEA/Preshow5Min`  | 300000 | 297978 | T-5 announcement |

`totalRideTimeSec` is **916** (not 600): the countdown runs from
Preshow10Min start to `HEA/1SFX` start (15:16). The experience itself
deactivates at T+10:00 when the preshow audio stops, so the HUD shows the
countdown reaching ~5:16-left and then disappears for the narration gap.

> **Caps matter:** every SFX track was server-*stopped*, not run to its
> natural end, so the MP3 files are longer than these audible windows. Do
> **not** author caption entries past the `endMs` caps above — anything past
> the cap never displays.

## 4. Infrastructure added this session (shared, not HEA-only)

Two changes outside the HEA classes that other experiences inherit:

### `isTheaterMode()` — HUD for boarding-less experiences
`RideExperience` gained `default boolean isTheaterMode() { return false; }`.
The ride HUD and macOS menu-bar countdown previously required
`wasPassenger == true` (`RideDetector.isOnRide()`), which never becomes true
for theater shows. Now:
- `RideDetector.isOnRide()` returns true for any active `isTheaterMode()`
  experience regardless of `wasPassenger`.
- The preshow-match path anchors `boardingTimeMs` at match-time for
  theater-mode experiences so the HUD timer starts immediately.

Both HEA classes return `true`. **TikiRoom was intentionally left untouched**
(still `false`) to preserve its current no-HUD behavior — flip it later if a
Tiki HUD is wanted.

### Audio capture logging (in `MCParksAudioService`)
To capture shows for analysis, audio events were promoted from `DEBUG` →
`INFO` (Fabric suppresses DEBUG): every WebSocket frame (`Audio message
received: …`), parsed `Show:`/`Loop:`/`Playing:` lines, and stop/finish
events. Plus a **2-second active-track sampler** logging each track's
`positionMs`. This is how the timeline below was reconstructed and is the
mechanism to use for any future show capture. (Leave it on; it's cheap.)

## 5. Show timeline (2026-04-25 capture, anchor = first HEA trigger 22:50:00)

| T+ | Track | Duration | In gate? |
|----|-------|----------|----------|
| 00:00 | `HEA/Preshow10Min` | 4:57 | preshow |
| 05:00 | `HEA/Preshow5Min` | 4:58 | preshow |
| 10:01 | `MK/Shows/SpecialOpening` | 5:14 | **no** (shared narration) |
| 11:18 | `…/DarrenNeutral` | 0:08 | **no** (cut early — see §7) |
| 14:48 | `…/RyanNeutral` | 0:24 | **no** |
| 15:16 | `HEA/1SFX` | 2:26 | main |
| 17:44 | `HEA/2SFX` | 4:12 | main |
| 21:58 | `HEA/3SFX` | 2:40 | main |
| 24:40 | `HEA/4SFX` | 2:36 | main |
| 27:18 | `HEA/5SFX` | 2:14 | main |
| 29:35 | `HEA/6SFX` | 2:12 | main |
| 31:48 | `HEA/7SFX` | 1:48 | main (ends 33:38) |
| 33:39 | `Anniversary/MomentousTag` | 1:46 | **no** (tail) |
| 35:32 | `Anniversary/CelebrateYou` | 2:38 | **no** (tail) |

Other observations: no fade-ins on any HEA track (hard cuts); `show-1-…`
frames map to `seekSec=0.001` (server convention, treat as 0); a post-show
mass `stop-…` sweep at the end targets several names that never played
(`HEA/PS`, `HEA/preshow2021`, `HEA/PreShowAudio`, …) — defensive, ignore.

## 6. File map

| File | Role |
|------|------|
| `…/ride/experience/rides/HappilyEverAfter.java` | main-show class (audio + castle gate, theater-mode) |
| `…/ride/experience/rides/HappilyEverAfterPreshow.java` | preshow countdown class |
| `…/subtitles/hea.json` | main subtitles — **placeholders only**, 7 tracks |
| `…/subtitles/hea_preshow.json` | preshow subtitles — **placeholders only**, 2 tracks |
| `…/ride/experience/RideExperienceRegistry.java` | both registered (HEA between GreatMoments and HauntedMansion; preshow right after) |
| `…/ride/experience/RideExperience.java` | + `isTheaterMode()` default |
| `…/ride/RideDetector.java` | `isOnRide()` + boardingTime anchoring for theater mode |
| `…/audio/MCParksAudioService.java` | INFO-level capture logging + 2 s sampler |

## 7. How the data was gathered

Live MC on DebugBridge port **9877** (1.19, ModrinthApp "Fabric 1.19"). The
INFO logging from §4 wrote the full event stream to `logs/latest.log`; it was
filtered to `/tmp/hea_capture.log` (1982 lines, 22:42:45 → 23:30:09) and
analyzed there. Castle detection was confirmed live via
`FireworkLocations.current()` returning the Magic Kingdom location while
standing at the statue.

## 8. Verify after a rebuild

`./build-and-deploy.sh`, then at the MK castle at HEA showtime:
- When `HEA/Preshow10Min` fires → HUD shows **"Happily Ever After (Preshow)"**
  with a countdown toward 15:16; log shows `Preshow experience matched:`.
- ~15 min later when `HEA/1SFX` fires → HUD switches to **"Happily Ever
  After"** (18:22 progress); log shows `Matched ride experience:`.
- HUD disappears during the ~5-min narration gap and again after `HEA/7SFX`.
  (Subtitles won't show until §3 placeholders are filled.)

## 9. Open TODOs

1. **Write the subtitles (the main remaining work).** Replace the
   `<<TODO: …>>` placeholders in `hea.json` and `hea_preshow.json` with real
   captions. No chat source exists, so this is a listen-and-transcribe pass
   against the MP3s at `https://mcparks.us/audio_files/HEA/<name>.mp3` (and
   `HEA/Preshow10Min.mp3` etc.). Keep entries within the `endMs` caps in §3.
2. **`HEA/2SFX` duration (need to listen).** It's a big outlier (~4m12s).
   Confirm whether it's always that long (one specific long song segment) or
   whether the showrunner paced it loosely that night — affects HUD accuracy
   and caption pacing. Noted as a TODO in `HappilyEverAfter.java`.
3. **`DarrenNeutral` cut after 7.9 s (need to listen).** Doesn't affect the
   gate (narration is excluded), but matters if a narration phase is ever
   added. Compare against `RyanNeutral`, which finished naturally (~24 s). Was
   Darren a misfire, an A/B narrator pick, or intentional? Noted as a TODO in
   `HappilyEverAfter.java`.
4. **Preshow → show narration gap.** The ~5 minutes of
   `MK/Shows/SpecialOpening` between preshow end and `HEA/1SFX` is currently
   unrepresented (HUD hidden). If continuity is wanted, it would need a
   distinguishing signal beyond the shared narration tracks — or accept the
   gap. Left as-is intentionally.
5. **Anniversary tail.** If a future decision wants the experience to run
   through `Anniversary/MomentousTag` → `Anniversary/CelebrateYou`, gating on
   them needs a companion check (recent HEA SFX) to avoid firing at non-HEA
   anniversary contexts.
