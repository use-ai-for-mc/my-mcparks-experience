# Happily Ever After — Implementation Handoff

Status as of 2026-04-26.

## What's done

Detection, JSON skeletons, and HUD wiring all shipped and built. The mod
will:

- Activate **Happily Ever After (Preshow)** when `HEA/Preshow10Min` or
  `HEA/Preshow5Min` plays AND the player is at the Magic Kingdom castle.
  HUD shows a 15:16 countdown; experience deactivates at T+10:00 when the
  preshow audio stops (5-min narration gap follows).
- Activate **Happily Ever After** when any of `HEA/1SFX` … `HEA/7SFX`
  plays AND the player is at the Magic Kingdom castle. HUD shows
  18:22 progress; experience deactivates when `HEA/7SFX` stops.

Source files:

- `src/main/java/com/chenweikeng/mcparks/ride/experience/rides/HappilyEverAfter.java`
- `src/main/java/com/chenweikeng/mcparks/ride/experience/rides/HappilyEverAfterPreshow.java`
- `src/main/resources/assets/my-mcparks-experience/subtitles/hea.json`
- `src/main/resources/assets/my-mcparks-experience/subtitles/hea_preshow.json`

Detection-pattern changes that touched other code:

- Added `RideExperience.isTheaterMode()` opt-in (`RideExperience.java`).
- `RideDetector.isOnRide()` now returns true for theater-mode experiences
  without requiring `wasPassenger`; `boardingTimeMs` anchors at match-time
  so the HUD timer starts.

## What's still TODO — subtitle authoring

The two JSON files have placeholder `<<TODO: trackname>>` entries with the
correct `endMs` caps but no real text. To finish the experience:

1. Listen to each track and transcribe / write atmospheric captions.
2. Replace the single placeholder entry per track with a real timeline of
   `{"startMs": …, "endMs": …, "text": "…"}` entries, all with times
   relative to the track's own playback start (not relative to the
   experience start — that's what `rideOffsetMs` is for).
3. Do not extend any entry past the `endMs` cap shown in the placeholder
   — the server stops most tracks early, and any cue past the cap will
   never display.

Track durations (audible window in milliseconds):

| Track | Duration | Notes |
|---|---|---|
| `HEA/Preshow10Min` | 296655 | 10-min announcement, server-stopped |
| `HEA/Preshow5Min` | 297978 | 5-min announcement, server-stopped |
| `HEA/1SFX` | 146538 | first show segment |
| `HEA/2SFX` | 252227 | longest segment (~4m12s — see open question below) |
| `HEA/3SFX` | 160754 | |
| `HEA/4SFX` | 156646 | |
| `HEA/5SFX` | 134741 | |
| `HEA/6SFX` | 131612 | |
| `HEA/7SFX` | 108101 | finale segment |

## Open audio questions (need to listen)

These were marked TODO in `HappilyEverAfter.java` Javadoc:

1. **Is `HEA/2SFX` always ~4m12s?** It's a big outlier — every other SFX
   is 1m48s–2m40s. Could be one specific song segment that's always long,
   or could be tonight's showrunner pacing it loosely.
2. **Was `DarrenNeutral` cut after only 7.9 s by design or a misfire?**
   Doesn't affect detection (the narration tracks aren't gated), but
   matters if you ever add a separate narration phase. Compare against
   `RyanNeutral` which finished naturally (~24.3 s).

## Raw data

The full audio-event log from the 2026-04-25 capture is at
`/tmp/hea_capture.log` (1982 lines, 22:42:45 → 23:30:09). Filtered to
just the events: `grep "MCParks" "/Users/cusgadmin/Library/Application Support/ModrinthApp/profiles/Fabric 1.19/logs/latest.log"`
on the live log file (this captures whichever session is current).

Per-track summary table is reconstructable from the log — see the
analysis section in the conversation history that produced the JSON
skeletons.

## Excluded from gates (deliberate, do not add back without re-thinking)

- `MK/Shows/SpecialOpening`, `…/DarrenNeutral`, `…/RyanNeutral` — shared
  with Wishes / Enchantment / other castle shows; including these would
  false-positive HEA on non-HEA nights.
- `Anniversary/MomentousTag`, `Anniversary/CelebrateYou` — fire as a tail
  after every HEA but also at non-HEA contexts. Excluding leaves a clean
  ~14-second cutoff after `HEA/7SFX` ends.

## Verification for next HEA capture

To re-verify everything still works on the next show:

1. With Minecraft running, `mcp_connect port=9877`.
2. Check `FireworkLocations.MAGIC_KINGDOM.isHereNow(mc)` returns `true`.
3. Tail `latest.log` for `Audio message received: show-0-HEA/Preshow10Min`
   when the 10-minute warning fires.
4. Confirm `Preshow experience matched: Happily Ever After (Preshow)`
   logs immediately after.
5. ~15 minutes later, expect `Matched ride experience: Happily Ever After`
   when `HEA/1SFX` triggers.
6. HUD should show the show name + countdown + progress bar through both
   phases.
