# Sonic targets — measured reference material

What kind of material beepstreet should be able to make, derived by measuring seven
reference tracks rather than by vibes. These numbers give each voice a target to
build against. See [macro-model.md](macro-model.md) for how the X/Y/Z macros
navigate toward these targets.

## Reference set

| # | artist — track | why |
|---|---|---|
| 1 | Ryoji Ikeda — data.reflex | sub + ultrasonic clicks, scooped mids, dynamic |
| 2 | Ryoji Ikeda — data.matrix | most dynamic/gestural, glitch density |
| 3 | Frank Bretschneider — A Soft Throbbing of Time | continuous sub throb, near-atonal |
| 4 | Frank Bretschneider — Construction Shack | steady driving clicks, mid-forward |
| 5 | Frank Bretschneider — Looping VI | quiet, pointillistic, tonal/complex |
| 6 | Kangding Ray — Downshifters | bright, textured, dynamic |
| 7 | SND — 1 | driest, most pointillistic, steadiest groove |

## Method

- **essentia** `MusicExtractor` (2.1-beta6-dev, installed via pip into a venv — the
  cp314 arm64 macOS wheel installs on system Python 3.14) for the full low-level +
  rhythm + tonal descriptor set incl. EBU R128 loudness.
- **aubio** onset counts as a cross-check; **sox** spectrograms (regenerable).
- Reproduce: `docs/audio-analysis/analyze.py` → `analysis.json` (raw values).

## Measured features

### Rhythm & dynamics

| Track | BPM (alt) | onsets/s | silence@30dB | LUFS | LRA | dyn-cplx | feel |
|---|---|---|---|---|---|---|---|
| Ikeda reflex | 161 (96) | 7.5 | 0.84 | −15.6 | 6.8 | 5.1 | dense, gappy, dynamic |
| Ikeda matrix | 107 (157) | 7.7 | 0.65 | −12.5 | 7.8 | 7.2 | most dynamic/gestural |
| Bret Soft Throbbing | 90 | 4.8 | 0.28 | −13.4 | 3.7 | 3.6 | continuous sub throb |
| Bret Construction | 161 | 7.8 | 0.45 | −11.7 | **1.3** | 4.1 | steady, driving |
| Bret Looping VI | 120 | 6.3 | 0.81 | −26.9 | 3.2 | 3.7 | quiet, pointillistic |
| Kangding Ray | 146 (73) | 4.8 | 0.73 | −13.3 | 7.8 | 5.6 | dynamic, textured |
| SND — 1 | 124 | 7.6 | **0.95** | −13.3 | **0.8** | 3.3 | driest, steadiest |

### Spectrum & tonality

centroid/rolloff/zcr = perceptual brightness; flatness↑ = noisier; band % is
power-weighted (low-biased) — read **comparatively**.

| Track | centroid Hz (σ) | rolloff | zcr | flatness | dissonance | pitch-sal | key (str) | bands L/mL/mH/H % |
|---|---|---|---|---|---|---|---|---|
| Ikeda reflex | 962 (**1387**) | 7274 | .176 | .35 | .40 | .58 | Bm (.73) | 93/4/**0.2**/3 |
| Ikeda matrix | 1454 (1959) | 5248 | .144 | .42 | .44 | .38 | Bm (.55) | 86/3/0.9/**10** |
| Bret Soft Throbbing | 847 | **444** | **.020** | **.64** | .48 | **.10** | — (.58) | **98**/1/0.4/1 |
| Bret Construction | 1209 | 2185 | .059 | .50 | .41 | .28 | Bb (.47) | 93/5/0.6/2 |
| Bret Looping VI | 1511 | 3716 | .130 | .24 | .45 | .52 | G (.70) | 71/21/3/5 |
| Kangding Ray | **2378** | 3510 | .109 | .22 | **.49** | .51 | Em (.45) | 90/1/**6**/2 |
| SND — 1 | 1480 (2122) | **8759** | **.184** | .38 | .42 | .40 | Fm (.65) | 38/**41**/6/**15** |

## The aesthetic, from the numbers

1. **Fast subdivisions, sparse hits.** 5–8 onsets/s against 90–160 BPM → events land
   ~every 3rd–4th 32nd. Validates the 32nd grid: the material lives at that
   resolution but is mostly rests (high `silence_rate`). → short/decay-dominant
   default envelopes; strong rest / probability / conditional-trig support.
2. **Two dynamic archetypes.** Steady/hypnotic (SND LRA 0.8, Construction 1.3) vs
   gestural (Ikeda matrix & Kray LRA ~7.8, dyn-cplx 5–7). The same voice set must do
   both → LFOs + per-step dynamics + probability.
3. **The Ikeda scoop is real and measurable:** sub + extreme highs with a hole in the
   mids (band_midhigh 0.2–0.9%, low centroid but huge rolloff/σ). → design voices at
   the spectral extremes; treat 800 Hz–4 kHz as optional.
4. **Noisiness spans the full range:** near-sine (Bret Soft Throbbing zcr .02,
   pitch-sal .10) → broadband clicks (SND/Ikeda zcr .18, rolloff ~8–9k). → a
   tonal↔noise morph belongs on every percussive voice.
5. **Tonal material is minor & low-confidence.** When pitch exists it's Bm/Em/Fm;
   clicks/noise near-atonal. Dissonance .40–.49 (mild clustering, not lush). →
   discrete minor-friendly pitch on pitched voices; additive aims *mildly* dissonant.

## Per-voice targets

X/Y/Z axis semantics are defined in [macro-model.md](macro-model.md)
(X = complexity/timbre, Y = duration/shape, Z = register/motion).

- **click1 (bright/ultrasonic — Ikeda):** filtered impulse/noise burst. centroid
  >3 kHz, zcr high, decay 0.5–20 ms. X→tonal↔noise, Y→gate↔ping, Z→center 2–10 kHz.
- **click2 (woody/dry — SND, Bret Construction):** resonant band-pass 800 Hz–3 kHz,
  drier/mid-forward, decay 2–50 ms. X→resonance/damping, Z→center 400 Hz–4 kHz.
- **beep (sine ping — Ikeda):** near-zero-attack sine, discrete pitch, pitch-sal high.
  X→inharmonicity/2nd partial, Y→decay 5–300 ms, Z→register (sub→ultrasonic).
- **additive (Bret Looping VI, Kray):** clustered partials, most complex refs
  (spec-cplx 9.6–13). X→**consonance↔dissonance** (target .40–.49, full range
  available), Z→partial count/brightness. minor-leaning.
- **noise:** full-band filterable (dark throb → bright hiss). X→color
  white↔pink↔bandpassed (flatness), Z→filter center + LP/BP/HP morph.
- **kick-ish (Bret Soft Throbbing):** sine/tri + fast pitch env + click transient,
  deep (rolloff ~450, centroid ~850). X→click/drive, Z→tune 35–90 Hz.
- **bass (SND / dub steady):** filtered saw/pulse or sub in low-mids (SND band_midlow
  41%), sustainable. X→cutoff/drive, Y→long gates for steady feel, Z→register.
  See the [Ikeda two-sine recipe](macro-model.md#worked-example--ikeda-bass).

## Global voicing notes

- Master loudness reference ~**−12 to −15 LUFS** (Looping VI's −27 is the ambient outlier).
- The **mid-scoop** (800 Hz–4 kHz) is a feature to leave room for, not fill.
