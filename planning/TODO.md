beepstreet TODO (generated — DO NOT EDIT)
=========================================

*Generated from `planning/ledger.toml` by `scripts/dev render`. Edit the
ledger, not this file. Status/verification are gate-enforced (`scripts/dev
check`): a `done` item must have a real test or its named artifact.*

**40 items** — 7 done, 15 wip, 18 todo. *Rendered 2026-07-24.*

## Engine (SuperCollider DSP)

| | id | item | verify |
|---|---|---|---|
| ~ | `additive-motion-stereo` | Additive: drifting detune/phase, stereo width, darker/brighter, consonance | manual · 2026-07-24 |
| ~ | `kick-breadth` | Kick: more breadth — tonal element, bare-vs-compound range | manual · 2026-07-24 |
| ~ | `noise-models` | Noise: multiple algorithms interpolable through a macro | manual · 2026-07-24 |
| ~ | `voice-additive` | Additive-chord voice with X/Y/Z macros | manual · 2026-07-24 |
| ~ | `voice-bass` | Bass voice with X/Y/Z macros | manual · 2026-07-23 |
| ~ | `voice-beep` | Beep voice: sine ping with X/Y/Z macros | manual · 2026-07-23 |
| ~ | `voice-click1` | Click 1 voice: SC synthdef with X/Y/Z macro inputs | manual · 2026-07-23 |
| ~ | `voice-click2` | Click 2 voice: second click character, same X/Y/Z axes | manual · 2026-07-23 |
| ~ | `voice-kick` | Kick-ish voice with X/Y/Z macros | manual · 2026-07-24 |
| ~ | `voice-noise` | Noise voice with X/Y/Z macros | manual · 2026-07-24 |
|   | `hard-gate-env` | True hard-gate (rectangular) envelope for sharp on/off sections | manual · 2026-07-24 |
|   | `voice8-bretschneider` | Possible 8th voice: quavering chord-ish background (Bretschneider) | manual · 2026-07-24 |

## Voice model (X/Y/Z macros, pitch)

| | id | item | verify |
|---|---|---|---|
| ~ | `multi-voice` | Seven independent voices play at once; select current voice on the global row | manual · 2026-07-23 |
|   | `combinatoric-macros` | Combinatoric macro space: parameters from X×Y×Z combinations | manual · 2026-07-24 |
|   | `key-scale-select` | Global row control to set key (root) + scale | manual · 2026-07-23 |
|   | `keyboard-chromatic` | Pitch keyboard offers chromatic vs scale-quantized mode | manual · 2026-07-23 |
|   | `xyz-macro-lattice` | X/Y/Z macros select coordinates from a per-voice interacting lattice | manual · 2026-07-23 |
| ✓ | `pitch-select-grid` | Discrete pitch selected via grid (not a knob) | manual *(attested)* · 2026-07-23 |

## Sequencing (patterns, param-locks)

| | id | item | verify |
|---|---|---|---|
| ~ | `adsr-mix-microgrid` | Per-voice micro-fader quadrant (vol/pan/prob/…), per-step-plockable | manual · 2026-07-23 |
| ~ | `step-tap-hold-debounce` | Bug: holding a step with no plock edit deletes it on release | manual · 2026-07-24 |
|   | `micro-faders-perf` | Remaining micro-fader columns: clock-div, length, send, transpose | manual · 2026-07-23 |
|   | `pattern-set-seq` | Pattern-set sequencing chains patterns across voices | manual · 2026-07-23 |
|   | `patterns-per-voice` | Multiple patterns stored per voice | manual · 2026-07-23 |
|   | `step-copy-paste` | Copy/paste steps via the global bar while holding a step | manual · 2026-07-24 |
| ✓ | `param-lock` | Hold a step + strips to param-lock X/Y/Z for that step | manual *(attested)* · 2026-07-23 |
| ✓ | `step-sequencer` | 4x8 grid step sequencer: tap to instantiate a step | manual *(attested)* · 2026-07-23 |

## Grid controller

| | id | item | verify |
|---|---|---|---|
| ~ | `grid-voice-page` | Voice page layout: steps + micro faders + X/Y/Z strips + global row | screenshot: grid photo: step block + micro-fader quadrant + three strips + transport row · 2026-07-23 |
|   | `y-snap-grid` | Y strip coarse stops snap to musical 1/16-note increments | manual · 2026-07-24 |

## norns UI

| | id | item | verify |
|---|---|---|---|
|   | `ui-page-routing` | norns screen routes between voice pages, mix page, and pattern view | manual · 2026-07-23 |

## Mix (volume/pan)

| | id | item | verify |
|---|---|---|---|
| ~ | `per-step-mix` | Per-step volume/pan variation available from voice pages | manual · 2026-07-23 |
|   | `mix-page` | Dedicated mix page: per-voice volume and pan | manual · 2026-07-23 |

## Effects

| | id | item | verify |
|---|---|---|---|
|   | `global-fx` | Global effect bus on the master | manual · 2026-07-23 |
|   | `per-voice-sends` | Per-voice effect sends | manual · 2026-07-23 |

## Infrastructure

| | id | item | verify |
|---|---|---|---|
|   | `lfo-automation` | Assignable LFOs modulate voice params (X/Y/Z, vol, pan) | manual · 2026-07-23 |
|   | `modulation` | Modulation system — where LFO/env/random routes to macros/params | manual · 2026-07-24 |
|   | `params-persistence` | norns params defined + pset save/load restores full instrument state | manual · 2026-07-23 |
| ✓ | `clock-transport` | Clock/transport drives sequencer at 32nd-note resolution | manual *(attested)* · 2026-07-23 |
| ✓ | `ledger-regime` | Ledger + gate regime stood up; scripts/dev check exits 0 | manual *(attested)* · 2026-07-23 |
| ✓ | `script-scaffold` | Thin beepstreet.lua boots on device and requires lib/ modules | manual *(attested)* · 2026-07-23 |
| ✓ | `voice-harness` | Autonomous voice-tuning harness: NRT render -> essentia -> compare to targets | manual *(attested)* · 2026-07-23 |

