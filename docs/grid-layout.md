# Grid layout — the voice page

The reserved map for a per-voice page on a 128 (16×8) grid, and the modal law that
governs it. Companion to [macro-model.md](macro-model.md) (what the X/Y/Z strips
mean) and [sonic-targets.md](sonic-targets.md) (what we're aiming at).

## Reserved map

```
        cols 1–8                    cols 9–16
row 1  ┌───────────────────┐   ┌───────────────────────┐
row 2  │   STEP BLOCK      │   │   MICRO faders         │  per-voice column faders
row 3  │   4×8 = 32 steps  │   │   (vol pan prob …)     │
row 4  └───────────────────┘   └───────────────────────┘
row 5  ├──────────────── X strip ──────────────────────┤
row 6  ├──────────────── Y strip ──────────────────────┤   macro axes (lib/strip)
row 7  ├──────────────── Z strip ──────────────────────┤
row 8  ├──── GLOBAL: transport + page switching ────────┤   play/pause at (1,8)
```

- **Step block** (rows 1–4, cols 1–8): 32 steps; rows = beats, cols = the eight
  32nds per beat. Playhead sweeps here.
- **Micro faders** (rows 1–4, cols 9–16): up to eight 1-wide vertical column faders
  of per-voice params (lib/fader).
- **Strips** (rows 5–7): the X / Y / Z macro axes (lib/strip).
- **Global row** (row 8): transport + paging. During a step-hold it becomes pitch
  helpers (octave/scale) — see the law below.

## The modal law

> **Hold a step → the whole grid becomes that one step's editor.**

| region | no step held (per-voice) | step held (per-step plock) |
|---|---|---|
| strips 5–7 | edit the **global** X/Y/Z macro | plock X/Y/Z on the held step |
| micro faders 9–16 | edit **per-voice** vol/pan/… | plock the per-step-able ones on the held step |
| step block 1–8 | show the pattern (on/off + playhead) | become a **pitch keyboard** for the held step |
| global row 8 | transport + paging | **octave −/+**, scale/root, clear |

One rule explains everything. A fader/strip **plocks only if it makes sense
per-step**: vol, pan, probability, send, X/Y/Z, pitch are per-step; clock-division
and pattern-length are pattern-level (per-voice only — inert while holding).

Steps store their plocks on the step table (`st.x/y/z`, `st.vol/pan/prob`,
`st.pitch`); the trigger falls back to the global macro / per-voice value per field.

## Strips (rows 5–7) — see [strip.lua](../lib/strip.lua)

Horizontal fader: tap-along = coarse; re-tap the top key = fine (4 stops, brightness
spread for legibility); **hold the lowest key = clean zero** (debounced so it never
flashes the coarse value first). Un-held = global macro; held-step = plock.

## Micro faders (rows 1–4, cols 9–16) — see [fader.lua](../lib/fader.lua)

Vertical 4-key column faders. Coarse is correct here (nobody wants 240 pan values).

- **Unipolar** (vol, probability, send): fill from the bottom, 5 levels (0…1). Tap a
  cell to set; tap the top of the bar to dial down; bottom cell reaches 0.
- **Bipolar** (pan, clock-div, transpose): center detent using the two middle keys as
  the 0 zone. `+2` rows 1–2 · `+1` row 2 · `0` rows 2–3 dim · `−1` row 3 · `−2` rows
  3–4. Tap the lit center key to return to 0.

Proposed columns (per-voice): **vol · pan · clock-div · length · probability · send ·
transpose · spare**. Attack/decay are NOT here — they're the **Y** macro now.

## Pitch keyboard (step block, on hold) — see pitch-select-grid item

While a step is held, the 31 non-held step pads become a **scale-quantized keyboard**;
tap a pad → set that step's pitch (stored absolute). The held pad stays lit as the
anchor. **Octave −/+** on the global row shifts the visible window; holding a step
that already has a note auto-centers the keyboard on it. Root + scale are per-voice.
Layout: scale-degrees per row (legible for stepwise lines). Per-voice default pitch
via a full-grid pitch page (later) or "inherits last note."

## Global row (row 8)

Transport (play/pause `(1,8)`, reset `(2,8)`) + paging to voice pages / arrangement /
mixer (later). Repurposed to octave/scale helpers while a step is held.

## Implementation status

- **Built:** reserved map, strips at 5–7, micro faders (vol/pan/probability) per-voice
  + per-step plock, grid transport on row 8. `param-lock` (strips) done.
- **Next:** pitch keyboard + octave (the step-block-on-hold mode); remaining micro
  columns (clock-div/length/send/transpose); the global paging bar.
