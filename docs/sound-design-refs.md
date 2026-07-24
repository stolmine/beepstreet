# Sound-design references — clinical-digital synthesis

Distilled from research on our reference artists (Ikeda, Bretschneider, Kangding Ray)
plus Mark Fell / SND and Gábor Lázár. Actionable techniques → SuperCollider, for
tuning beepstreet's voices. Companion to [sonic-targets.md](sonic-targets.md) (the
measured targets) and the offline tuning loop
[audio-analysis/voice_harness.py](audio-analysis/voice_harness.py).

## The shared "clinical digital" law

Everything these artists share, and the thing that makes it read as machine-precise
rather than warm/analog:

- **Spectral-domain synthesis** — additive sine stacks and/or FM. Exact frequencies,
  **no oscillator drift or detune** (except deliberate Hz-level beating, Ikeda).
- **Dead dry** — no reverb, no delay, no chorus. "Make the sound right at the source
  so it doesn't need effects" (Errorsmith). Excite a room with a short IR only if you
  actually want space (Ikeda).
- **Hard-gated static envelopes** — near-zero attack, sharp edges, no vibrato / no
  filter-cutoff sweep / no portamento. Fell: two element types only — percussive hits
  and "oscillations that won't move."
- **Complexity in the pattern, not the timbre** — "normal sounds, weird patterns"
  (Fell). Odd-subdivision grids, off-grid placement, hard ping-pong pan.
- **NOT "synthy"** = avoid saw-through-resonant-lowpass with a cutoff sweep. That is
  the exact thing they all avoid.
- Deterministic distortion only (`.tanh`/`.softclip`/`.fold2`/Cheby `Shaper`), never
  analog fuzz — and reviews stress "crystal clear," so use sparingly. Final `Limiter`
  for the loud/precise master.

## Per-artist technique → SC

- **Ikeda** — sine waves + white noise, *nothing else*. Clicks = the **impulse**
  (`Impulse.ar`). Millisecond-grid precision (`Demand`/`Duty`, quantize to `SampleDur`).
  Sub + ultra-high extremes; beating via two sines a few Hz apart. Mono, dry, extreme
  dynamics, brickwall master.
- **Bretschneider** — on/off binary gating (`LFPulse`/`Trig`), Buchla/West-Coast
  timbre (FM + waveshaping, **low-pass gate** = `sig*env` → `LPF` cutoff tracking the
  same env), Nord Modular. Sample&hold chaos → order (`Latch`/`LFNoise0` modulating
  freq/index). Micro-timing jitter (±8 ms) for "funk." (Max/MSP unconfirmed.)
- **Kangding Ray** — drones **cut by fast VCAs** = beats as amplitude-gated drone
  (`SinOsc * Env.perc` triggered by `Impulse`/`Dust`). Distorted-granular
  (`GrainBuf`→`.tanh`→`Compander`). Physical-modelling resonators (`Klank`/`Ringz`).
  Dual-purpose sounds (a `Ringz` ping that is both note and hit).
- **Mark Fell / SND** — 4-op **FM** (TX81Z/DX100/FS1R, FM8). **Integer ratios =
  consonant/organ; irrational ratios (×1.41, ×1.73, ×3.16) = metallic/inharmonic.**
  Chords = stacked FM voices in close/jazzy voicings (added 9ths/2nds → partials
  within a critical band = roughness). Dry, static, hard-gated. `PMOsc.ar(c, c*ratio,
  index)`; brightness = index.
- **Gábor Lázár** — additive sine stacks (NI Razor / Reaktor / LazerBass), Max/MSP as
  the *instrument/sequencer* not the synth. His one named processing move is
  **filter-envelope modulation** as articulation. Crystal-clear, not lo-fi — harshness
  from bright pure spectra + hard edits, *not* bitcrush. Odd-grid, ping-pong stereo.
  Errorsmith/Razor Rosetta: additive gives exact control over "how harmonic or
  dissonant" — even filters/reverb are done by reshaping partials, so it never leaves
  the harmonic domain (why it sounds clinical).

## How this maps to beepstreet voices (status)

- **additive** — DONE per this research: 4 stacked FM voices at a chord voicing with
  irrational modulator ratios; X = FM index walks consonant sine chord → inharmonic
  clang. Harness dissonance 0.07 → **0.47** (target .40–.49).
- **bass** — DONE: dropped filtered-saw for pure sine + integer-ratio FM + sub octave,
  no filter sweep, dry; stays sub-dominant (band_low 86%).
- **beep** — Ikeda sine + detune-beating + FM grit (largely there).
- **clicks** — Ikeda impulse (`Ringz`-excited), Bretschneider gated fragments. click2
  still needs to sit lower/woodier than click1 (harness: near-identical).
- **noise** — Ikeda white noise, raster-noton bitcrush/rate-reduction for crackle.
- **kick** — pitch-enveloped sine + click; consider Kangding-Ray drone-carved-by-VCA.

## Key sources

Fell: factmag.com/2018/10/06/mark-fell-signal-path, straylandings.co.uk (2016),
daily.redbullmusicacademy.com/2012/05/mark-fell-interview. Lázár:
musicradar.com (Gábor Lázár on sound design in Max), blog.native-instruments.com/
sketches-gabor-lazar, blog.native-instruments.com/razor-at-10-errorsmith. Ikeda:
daily.redbullmusicacademy.com/2017/10/ikeda-tech-feature, muziekgebouw.nl (sine waves
and white noise). Bretschneider: headphonecommute.com in-the-studio, factmag.com
sinn-form. Kangding Ray: daily.redbullmusicacademy.com/2015/12/kangding-ray-interview,
groove.de on Cory Arcane. Microsound: Curtis Roads; nathan.ho.name pulsar-synthesis.
