# Walt Disney's Enchanted Tiki Room (WDW)

> **Status:** Implemented, merged to `main` (ride class + unified timed
> subtitles, commit `6c52251`). The ride is detected, the HUD timer runs
> across the whole pre-show → show sequence, and subtitles display in sync.
> **Detection is audio-only and deliberately loose** — the planned
> entity/location gate is not built yet (see §7). This doc is the handoff
> for tightening it and refining subtitle timing later.

---

## 1. What it is

The WDW Adventureland Audio-Animatronic show, handled as **one continuous
experience** from the outdoor queue pre-show (Claude / Clyde / Pele
incantation) through the theater greeting and all five recorded show
segments to the "Heigh-Ho" exit march. Total runtime ~**17:22**.

It is a **theater show, not a vehicle ride**: guests stand in the round
room, there is no boarding event, and `isPassenger` stays `false` the whole
time. That single fact drives most of the design below.

Confirmed ride name (from the on-dismount reward message):
`Walt Disney's Enchanted Tiki Room`. Park: `Walt Disney World Resort`.

## 2. Detection design (audio-track allowlist)

Because there is no vehicle marker and no `isPassenger` signal, detection
keys entirely off the **audio tracks** MCParks streams. `isActive()` returns
true while any of the nine known tracks is playing in WDW:

```
TikiRoom/WDWPreshow      TikiRoom/CM/King            TikiRoom/TikiRoom_WDWS3
TikiRoom/WDWIntro        TikiRoom/TikiRoom_WDWS1     TikiRoom/TikiRoom_WDWS4
TikiRoom/WDWIntroFade    TikiRoom/TikiRoom_WDWS2     TikiRoom/TikiRoom_WDWS5
```

This is the same `MCParksAudioService.snapshotActive()` gate that
`PeopleMover` / `TowerOfTerror` use for their pre-boarding states — except
here *every* phase is "pre-boarding".

### Audio sequence as the "still on ride" check
The timer starts the moment `WDWPreshow` fires and continues across the
whole sequence. **If the player leaves mid-show, the audio stops and the
next track in the sequence never fires** — `isActive()` flips false on the
next tick, `RideDetector` fires `onDismount` with a partial elapsed time,
and the ride ends "unsuccessfully". Only players who let the audio play
through to WDWS5's final refrain see the timer reach ~17:22. This is the
behavior Weikeng asked for, and it falls out of the allowlist gate for free
— no extra "did they follow the sequence" bookkeeping needed.

### Why pre-show + show are ONE class (not split like Tower of Terror)
`TowerOfTerror` keeps its pre-show as a separate sibling class because the
library→boarding gap is **player-paced** (you walk hallways at your own
speed; some guests never board), so folding it into the ride timeline would
produce a meaningless countdown. Tiki Room is the opposite: the
pre-show→intro transition is **server-paced** (you stand in queue, the
server decides when the show starts). So a single continuous timer is
correct, and `TikiRoomPreshow` was merged into `TikiRoom` and deleted.

## 3. Timed subtitles

Hybrid timed-driven, same pattern as Tower of Terror: MCParks echoes every
voiced line as a `[Speaker] ` chat message. `captureSubtitle()` matches
those (and `♫`-prefixed song lyrics) so the chat mixin **cancels them from
chat**, and `TimedSubtitlePlayer` drives the actual on-screen display synced
to `SourceDataLine` playback position.

Speaker prefixes suppressed: `[Announcer]` `[Cast Member]` `[Claude]`
`[Clyde]` `[José]` `[Michael]` `[Pierre]` `[Fritz]`, plus any line starting
with `♫`.

**Timing source:** the subtitle offsets were derived from **chat-line
arrival timestamps** in `logs/latest.log` (MCParks emits each line in
lockstep with the AA performance), cross-referenced against each track's
`AudioCache MISS` start time. They are accurate to ~±1 s — good enough to
ship, but a refinement pass in Aegisub against the MP3s would tighten them.

### Track table

| Track | Duration | rideOffsetMs | Subtitle lines | Notes |
|-------|---------:|-------------:|---------------:|-------|
| `TikiRoom/WDWPreshow`     | 4:14 |       0 | 41 | outdoor queue: Announcer chant → Claude/Clyde banter |
| `TikiRoom/WDWIntro`       | 2:14 | 255000 |  0 | instrumental walk-in; plays ~48 s then CM/King replaces it |
| `TikiRoom/WDWIntroFade`   | 2:16 | 255000 |  0 | fade-out variant; loaded but not always played |
| `TikiRoom/CM/King`        | 0:24 | 304000 |  6 | Cast Member greeting + "Wake up Jose" |
| `TikiRoom/TikiRoom_WDWS1` | 3:07 | 328000 | 72 | bird intros + first Tiki song |
| `TikiRoom/TikiRoom_WDWS2` | 2:11 | 516000 | 28 | bird mobile / "Birdies sing" |
| `TikiRoom/TikiRoom_WDWS3` | 2:33 | 648000 | 23 | Hawaiian luau ("Tahuwai la") |
| `TikiRoom/TikiRoom_WDWS4` | 2:51 | 802000 | 18 | Pele storm (~82 s instrumental) + finale |
| `TikiRoom/TikiRoom_WDWS5` | 1:09 | 973000 | 24 | "Heigh-Ho" exit march |

`totalRideTimeSec` = **1042**. Offsets are sequential from the observed
cache-MISS timestamps (preshow start 20:48:55 → WDWS5 start 21:05:08).

## 4. File map

| File | Role |
|------|------|
| `src/main/java/com/chenweikeng/mcparks/ride/experience/rides/TikiRoom.java` | The single ride class (detection + chat suppression) |
| `src/main/resources/assets/my-mcparks-experience/subtitles/tikiroom.json` | Unified timed subtitles (9 tracks, 212 entries) |
| `src/main/java/com/chenweikeng/mcparks/ride/experience/RideExperienceRegistry.java` | Registered between `SpaceshipEarth` and `TowerOfTerror` |

`TikiRoomPreshow.java` and `tikiroom_preshow.json` were intermediate splits
that got merged into the above and **deleted** — don't recreate them.

### Source material (not in repo)
`~/Downloads/tiki/` holds the 9 source MP3s, per-track `.srt` files, and a
`SUBTITLES.md` dialogue dump — staged for an Aegisub refinement pass. These
are per-user diagnostic artifacts and intentionally not checked in.

## 5. How the data was gathered

Live MC instance on DebugBridge port **9877** (1.19, ModrinthApp profile
"Fabric 1.19"). The full show dialogue + audio-track sequence came from
`logs/latest.log` (`[CHAT]` lines for dialogue, `AudioCache MISS` lines for
track starts, `/audiolist` snapshots for "started Ns ago" timing). Audio
durations via `ffprobe` on the MP3s pulled from
`https://mcparks.us/audio_files/TikiRoom/<name>.mp3`.

## 6. Verify after a rebuild

`./build-and-deploy.sh`, then in-game in WDW Adventureland:
- Stand in the Tiki Room queue → HUD ride name + timer should appear when
  `WDWPreshow` starts and count up toward 17:22.
- Subtitles should appear in sync and the duplicated `[Speaker]` chat lines
  should be gone from the chat pane.
- Walk away mid-show → HUD should disappear within a tick or two (the
  "unsuccessful" exit).

## 7. Open TODOs

1. **Entity/location-based detection (the main one).** The audio gate is
   loose: another player's Tiki audio in the same park instance would
   activate the experience for the local player. Weikeng's plan: detect that
   the player is physically inside the show building via a reference entity
   (a marker armor stand) and use that as the real "on ride" signal, with
   audio as a secondary/timer-start cue. Likely candidates seen in a
   `/nearby` dump during the show: `vehicles/perch_jose` (`iron_axe:78`) and
   `vehicles/perch_michael` (`iron_axe:76`) — the bird perches. Need to
   confirm they're stable, in-radius, and unique to this building before
   keying on them.
2. **Subtitle timing precision.** Current offsets are chat-derived (±1 s).
   Refine against the MP3s in Aegisub using the staged `.srt` files if
   tighter sync is wanted.
3. **`WDWIntroFade` semantics.** It's fetched alongside `WDWIntro` but never
   observed actually playing — looks like a fade-out variant the server may
   pick situationally. Left in the allowlist with empty subtitles; confirm
   whether it ever drives display.
4. **WDWS4 instrumental gap.** The first ~82 s of WDWS4 is the Pele storm
   with no dialogue — that's authentic, not missing data. Don't "fill" it.
