# Voice recipes — measured + extracted synthesis specs

Concrete synthesis targets from (a) essentia analysis of the reference tracks and
(b) extraction of er-301-habitat units (Rauschen, Tessera, Plaits chord engine).
Feeds the voice-* and enhancement ledger items. Companion to
[sound-design-refs.md](sound-design-refs.md) (aesthetic) and
[sonic-targets.md](sonic-targets.md) (measured feature targets).

## Kick — `kick-breadth` (Ikeda + Tessera)

Ikeda "data.reflex" kicks are **overwhelmingly the bare type**, tonal element locked:
- **Type A (bare stab, primary):** sine **40 Hz** (E1), attack ~1 ms, decay ~350 ms
  exp, one-shot. Optional 3–5 ms HP noise burst at the attack for punch. Optional
  near-inaudible inharmonic character partials at 1.46/2.8/3.5/5.5/7.3/9.2 ×f0,
  each −28…−35 dB.
- **Type B (compound, sparse/higher):** 12-partial inharmonic stack ~544 Hz, ratios
  ≈[.39 .41 .48 .51 .67 .69 .78 .80 .82 .97 1.0 1.02]×f0, attack <1 ms, decay ~40 ms.

Tessera gives the mechanism to span A↔B: a **modal bank on an inharmonic lattice**
`f = fc·(h + k·r)`, with **two decay tiers** (core/harmonic modes ring long, sideband
modes short — this split is the brightness/decay control), soft-clipped **post-mix
with the sub** (the character is the resonator+sub slamming the clipper together).
- SC: `Klank`/`DynKlank` (~6–10 modes) or `Ringz` bank (live-modulatable r).
- **X** = lattice spacing r / body brightness (bare sub-stab r→0 ↔ compound clang r↑),
  **Y** = Character (fold modes on/off + reweight = pure-body↔clangy), **Z** = pitch /
  pitch-decay. Default the recurring kick to a fixed ~40 Hz bare stab.

## Additive chord — `additive-motion-stereo` (Plaits chord engine)

Three orthogonal levers (we can beat Plaits' discrete chord snap by interpolating):
- **X = chord structure:** table of interval-sets (port Plaits' 11: OCT,5,sus4,m,m7,
  m9,m11,69,M9,M7,M; trim to 6–8). **Linearly interpolate the ratios** as X sweeps
  (continuous chord glide), not hysteresis-snap.
- **Y = brightness / consonance:** partial rolloff `amp[k]=rolloff^k`. Low Y = dark,
  few partials, soft/consonant; high Y = bright, odd-partial-boosted, upper-partial
  roughness (perceptually less consonant).
- **Z = inversion/voicing + FM depth:** port Plaits' `ComputeChordInversion` rotation;
  ALSO blend FM sidebands per note (index rises with Z) — a second, independent
  consonance lever (Z=0 pure additive octaves → Z=1 FM-inharmonic).
- **Motion:** fixed micro-detune ±0.2–0.4% per note (chorus, à la Plaits ×0.998/1.004)
  **plus a slow drifting detune/phase** between partials for life. **Stereo width** macro.

## Noise models — `noise-models` (Rauschen)

Six algorithms, each fully parametrized by 2 macros (X density/decimation/chaos,
Y spectral/width/coupling) + a shared morph filter:
- **white** = decimate + bitcrush → `Decimator.ar` (X=rate, Y=bits).
- **crackle** = chaotic recurrence `y=|p·y₋₁−y₋₂−energy|` → `Crackle.ar(1+X)` + Y as
  fold intensity (exact energy-term needs a custom UGen).
- **henon** = Hénon map → `HenonN.ar(freq,a,b)` (sc3-plugins): X→a 1.15–1.45, Y→b 0–0.6.
- **cellular** = CA-as-wavetable (read-head scans an evolving CA row) → custom UGen
  (faithful) or `TGrains` on a periodically-regenerated buffer (approx).
- **particle** = stochastic impulses into a ringing bandpass, per-hit random freq →
  `Dust.ar(dens)` → `Ringz.ar(freq=Demand(Dwhite…), decay)`. X=density, Y=freq spread.
- **velvet** = one windowed raised-cosine pulse per window → `Impulse`/`Dust` + per-hit
  `EnvGen(Env.sine(width))` × random sign. X=density 10 Hz–10 kHz, Y=pulse width.

**Model-morph macro:** run models live, tent-weight crossfade (crackle→velvet→particle
→cellular), mirroring Rauschen's own 7-zone filter-morph crossfade. (Chaotic maps blend
as decorrelated signals, fine for sound-design; a shared-impulse-source variant is the
deeper "true morph" for later.)

## Quaver chord voice — `voice8-bretschneider` ("The Day It Rained Forever")

The quaver is **detuned-oscillator beating**, not tremolo/vibrato:
- **~25–30 cents constant detune** on unison pairs → beat rate scales with pitch
  (5.8 Hz at G4, 1.4 Hz at G2).
- Cores: **G2 (98 Hz)** + **G4 (392 Hz)** detuned unison pairs.
- Inharmonic body: partials at 203/243/283/324 Hz = **linear 40 Hz spacing** → best as
  **ring-mod/AM ~40 Hz around the G drone** (sum/difference sidebands), not fixed additive.
- Waveform: soft saw/triangle (harmonics 2–3 down 15–25 dB, LP'd).
- Slow secondary tremolo ~0.2–0.4 Hz; **mono/centered**; very slow swell attack.
- Fold into additive as a mode, or a standalone 8th voice (needs NV=7→8 + voice-select).

## Sources
essentia analysis of the two tracks (scratchpad ikeda/ + bretschneider/);
er-301-habitat mods/spreadsheet/Rauschen.*, Tessera.*, CellularEngine.h;
eurorack/plaits/dsp/engine/chord_engine.cc + dsp/chords/chord_bank.cc.
