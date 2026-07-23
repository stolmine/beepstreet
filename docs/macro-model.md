# Macro model — how X/Y/Z work

The design law for beepstreet's voice controls. Companion to
[sonic-targets.md](sonic-targets.md) (what we're aiming *at*); this doc is *how* the
three macros get there.

## Principle

An X/Y/Z macro is **not a knob on a parameter** — it is a designed trajectory through
the voice's parameter space. Moving one macro moves a *bundle* of underlying synth
params, each along its own curve.

- **Voices come into focus over regions, not points.** The mapping curves are shaped
  so musical timbres occupy broad, findable zones (plateaus / attractors), not
  razor-thin settings. Outside a focus region a voice may sound generic; that's fine —
  the macro is tuned so the good zones are wide and easy to fall into.
- **Sweet spots are repeatable because they're engineered.** Since good regions are
  plateaus, **quantizing a macro to a lattice** (the 16-step grid strips) lands you
  *on* them by design. A param-lock stores a coordinate, not a pile of param values —
  so patches recall exactly and users re-find spots they liked across sessions.
- **Voice identity = the region of the space it's tuned to occupy.** A voice is
  versatile precisely because one gesture sweeps a curated slice of a large space.

## Uniform axis semantics

Same *meaning* on every voice, different *implementation* per voice. This is what lets
the three strips read the same on every page, builds muscle memory, and makes patches
portable. (Refined per voice as we build; this is the convention.)

| axis | meaning | low → high |
|---|---|---|
| **X** | complexity / timbre | simple/pure → complex/noisy/dissonant |
| **Y** | duration / shape | hard gate → steep-log → smooth env |
| **Z** | register / motion | static/one-band → moving/tilted |

### Y is special: gate ↔ envelope as one continuum

Genre-critical (harsh cuts, clicks, drones that stop dead). Every voice's amplitude is
**one VCA fed a morphable shaper**:

- **Y=0** — the shaper passes the raw **gate** straight through (instant on/off). This
  is "gates over VCAs, not envelopes."
- **Y≈focus** — steep-log: fast curved attack + defined body ("smooth yet defined").
- **Y=1** — full smooth envelope (soft attack, long release).

**Length is decoupled from shape.** Y controls curvature; the *step gate-length* (or
hold) controls duration. So a **drone that cuts suddenly** = long step length × low Y
(hard release). The whole harsh-cut→drone palette comes from those two controls
crossed. The attack/decay micro-grid (3-way per step) is the *coarse override* layer;
Y is the *continuous* morph — they complement, not duplicate.

### X for additive: consonance ↔ dissonance

The complexity axis becomes a **harmonic-complexity sweep**: sine → consonant intervals
→ full harmonic series → stretched/inharmonic → clustered/detuned. One axis walks the
partial ratio set + amplitude rolloff + inharmonicity coefficient. Measured refs sit
mid-sweep (dissonance .40–.49) but the full range is reachable.

## Worked example — Ikeda bass

Recipe: two slightly detuned sines + a touch of cross-mod FM, VCA on a very steep log
shape (smooth yet defined). It decomposes to ~8 addressable params:

| param | role | range |
|---|---|---|
| `f0` (pitch) | fundamental | discrete, from grid |
| `detune` | osc2 offset → beating/width | 0–~20 cents |
| `fmIndex` | cross-mod depth → sidebands/edge | 0–~0.4 ("touch") |
| `fmRatio` | carrier ratio → harmonic↔metallic | ~1:1, exposable |
| `feedback` | self-FM grit | 0–small |
| `drive` | saturation → definition | 0–light |
| `atk / atkCurve` | onset shape | ~0–5 ms, curve steep |
| `rel / relCurve` | tail shape (the "steep log") | curve ≈ −4…−8 |

"Smooth yet defined" lives in the curve exponents; "cuts suddenly" is `rel → 0`.

### Its X/Y/Z (bold = the focus zone where the recipe lives)

| | X — weight→grit | Y — duration/shape | Z — motion/register |
|---|---|---|---|
| 0.0 | pure clean sub (detune 0, fm 0) | **instant gate** (atk 0, rel 0) | static, sub-heavy |
| **~0.3** | **slight detune + touch FM** | **steep-log: fast curved atk, defined body** | **faint LFO on detune/fm — breathing** |
| 1.0 | rough metallic FM, driven | soft atk + long smooth release (drone) | wide LFO + upper-harmonic tilt |

- **X** sweeps `detune`, `fmIndex`, `feedback`, `drive` together along tuned curves;
  the sweet spot is a plateau ~⅓ up.
- **Y** is the gate→steep-log→smooth shaper above.
- **Z** spends the axis on **movement** (pitch is discrete on the grid): LFO depth into
  detune/fmIndex + a spectral tilt. Static at 0, alive at 1.

## Open questions (resolve as we build)

- Per-voice: do all three axes always earn their keep, or do some voices want a macro
  to be a no-op / duplicate? (Keep the *semantic* even if the range is narrow.)
- Lattice resolution: 16 steps per strip = coarse. Fine mode (127-step, cf. gladiola)
  for macro nudging?
- How Z's "motion" LFO relates to the global assignable LFOs (`[beep:lfo-automation]`)
  — is Z a dedicated per-voice LFO, or a depth into the shared LFO bus?
