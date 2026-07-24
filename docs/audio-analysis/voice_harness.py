#!/usr/bin/env python3
"""voice_harness.py — autonomous voice-tuning loop for beepstreet.

Render each SynthDef (via SuperCollider NRT, offline — no device, no norns conflict)
using the REAL Lua resolver params, analyze the audio with essentia, and print the
measured features next to the per-voice sonic targets. Re-run after editing
lib/voices.lua or lib/Engine_Beepstreet.sc — it always reflects the current code.

Run with the essentia venv python:
  <scratch>/essentia-venv/bin/python docs/audio-analysis/voice_harness.py [workdir] [points]

points = semicolon-separated "x,y,z" macro coordinates (default "0.6,0.6,0.5").
Shorthand sweeps: "sweep:x" -> x in {0,.25,.5,.75,1} at y=.6 z=.5 (likewise y/z);
"sweep:all" runs the x, y and z sweeps in one pass.

Pipeline: luajit(resolver) -> params ; python builds a render.scd embedding the
engine's SynthDefs -> sclang writes one OSC score per (voice, point) ->
scsynth NRT -> WAVs -> essentia MusicExtractor -> comparison table.
"""
import json, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WORK = sys.argv[1] if len(sys.argv) > 1 else "/tmp/beepstreet-harness"
POINTS = sys.argv[2] if len(sys.argv) > 2 else "0.6,0.6,0.5"
SC = "/Applications/SuperCollider.app/Contents"
SCLANG, SCSYNTH = SC + "/MacOS/sclang", SC + "/Resources/scsynth"
ENGINE = os.path.join(ROOT, "lib", "Engine_Beepstreet.sc")
ORDER = ["click1", "click2", "beep", "additive", "noise", "kick", "bass"]

# what each voice should measure like (from docs/sonic-targets.md) — printed as guide
TARGETS = {
    "click1":   "bright/ultrasonic: centroid >3k, high zcr, short",
    "click2":   "woody/dry: centroid 0.8-3k, mid",
    "beep":     "sine ping: low flatness, centroid near pitch",
    "additive": "complex drone: mid dissonance .40-.49, sustained",
    "noise":    "noisy: high flatness, centroid = Z",
    "kick":     "deep: low centroid (<300 body), punchy",
    "bass":     "clean sub: low centroid, sub-heavy (band_low high), sustained drone",
}

HITS = [round(0.02 + i * 0.4, 3) for i in range(7)]   # 7 hits, 0.4s apart


def expand_points(spec):
    grid = [0.0, 0.25, 0.5, 0.75, 1.0]
    if spec.startswith("sweep:"):
        axes = ["x", "y", "z"] if spec == "sweep:all" else [spec[6:]]
        pts, base = [], {"x": 0.6, "y": 0.6, "z": 0.5}
        for ax in axes:
            for v in grid:
                p = dict(base); p[ax] = v
                s = f'{p["x"]},{p["y"]},{p["z"]}'
                if s not in pts:
                    pts.append(s)
        return ";".join(pts)
    return spec


def sh(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=600, **kw)


def get_params(points):
    r = sh(["luajit", os.path.join(ROOT, "docs/audio-analysis/render_params.lua"), ROOT, points])
    if r.returncode != 0:
        sys.exit("resolver failed:\n" + r.stderr)
    return json.loads(r.stdout.strip())   # list of {voice, point, ...}


def extract_synthdefs():
    txt = open(ENGINE).read()
    defs = re.findall(r"SynthDef\(.*?\}\)\.add;", txt, re.DOTALL)
    if len(defs) != len(ORDER):
        sys.exit(f"expected {len(ORDER)} SynthDefs, found {len(defs)}")
    return defs


def key(p):
    return f'{p["voice"]}__{p["point"]}'


def build_render_scd(params, defs, oscdir):
    lines = ["("]
    lines += defs
    for p in params:
        name = p["voice"]
        end = round(HITS[-1] + p["rel"] + 0.4, 3)
        events = [f'[0.0, [\\d_recv, SynthDescLib.global[\\{name}].def.asBytes]]']
        for i, t in enumerate(HITS):
            a = (f'\\out,0, \\freq,{p["freq"]:.3f}, \\amp,{p["amp"]:.4f}, \\atk,{p["atk"]:.5f}, '
                 f'\\rel,{p["rel"]:.4f}, \\curve,{p["curve"]:.3f}, \\pan,0, '
                 f'\\p1,{p["p1"]:.5f}, \\p2,{p["p2"]:.5f}, \\p3,{p["p3"]:.5f}')
            events.append(f'[{t}, [\\s_new, \\{name}, {1000+i}, 0, 0, {a}]]')
        events.append(f'[{end}, [\\c_set, 0, 0]]')
        score = ",\n  ".join(events)
        lines.append(f'Score([\n  {score}\n]).writeOSCFile("{oscdir}/{key(p)}.osc");')
    lines += ['"__RENDERED__".postln;', "0.exit;", ")"]
    return "\n".join(lines)


def render(params, oscdir, wavdir):
    for p in params:
        osc, wav = f"{oscdir}/{key(p)}.osc", f"{wavdir}/{key(p)}.wav"
        r = sh([SCSYNTH, "-o", "2", "-N", osc, "_", wav, "48000", "WAV", "int16"])
        if not os.path.exists(wav):
            print(f"  ! render failed for {key(p)}: {r.stdout[-200:]} {r.stderr[-200:]}")


def analyze(params, wavdir):
    import essentia
    essentia.log.warningActive = False; essentia.log.infoActive = False
    import essentia.standard as es
    rows = {}
    for p in params:
        wav = f"{wavdir}/{key(p)}.wav"
        if not os.path.exists(wav):
            continue
        try:
            f, _ = es.MusicExtractor(lowlevelStats=["mean"], rhythmStats=["mean"], tonalStats=["mean"])(wav)
        except Exception as e:
            rows[key(p)] = f"analysis fail: {e}"; continue
        eb = [f["lowlevel.spectral_energyband_low.mean"], f["lowlevel.spectral_energyband_middle_low.mean"],
              f["lowlevel.spectral_energyband_middle_high.mean"], f["lowlevel.spectral_energyband_high.mean"]]
        s = sum(eb) or 1
        import math
        audio = es.MonoLoader(filename=wav)()
        rms = es.RMS()(audio)
        peak = max(abs(audio.max()), abs(audio.min()))
        rows[key(p)] = dict(
            rms=round(20 * math.log10(rms + 1e-9), 1),
            peak=round(20 * math.log10(peak + 1e-9), 1),
            centroid=round(f["lowlevel.spectral_centroid.mean"]),
            rolloff=round(f["lowlevel.spectral_rolloff.mean"]),
            flatness=round(f["lowlevel.melbands_flatness_db.mean"], 3),
            zcr=round(f["lowlevel.zerocrossingrate.mean"], 4),
            dissonance=round(f["lowlevel.dissonance.mean"], 3),
            bands="/".join(str(round(100 * x / s)) for x in eb),
        )
    return rows


def main():
    os.makedirs(f"{WORK}/osc", exist_ok=True)
    os.makedirs(f"{WORK}/wav", exist_ok=True)
    params = get_params(expand_points(POINTS))
    scd = build_render_scd(params, extract_synthdefs(), f"{WORK}/osc")
    open(f"{WORK}/render.scd", "w").write(scd)
    r = sh([SCLANG, f"{WORK}/render.scd"])
    if "__RENDERED__" not in r.stdout:
        sys.exit("sclang render failed:\n" + r.stdout[-800:] + r.stderr[-400:])
    render(params, f"{WORK}/osc", f"{WORK}/wav")
    rows = analyze(params, f"{WORK}/wav")
    print(f"\n{'voice':<9} {'point':<15} {'rms':>6} {'peak':>6} {'centroid':>8} {'rolloff':>7} {'flat':>6} {'zcr':>7} {'diss':>6} {'bands L/mL/mH/H':>16}")
    print("-" * 108)
    for name in ORDER:
        mine = [p for p in params if p["voice"] == name]
        if not mine:
            continue
        for p in mine:
            r = rows.get(key(p))
            if isinstance(r, dict):
                print(f"{name:<9} {p['point']:<15} {r['rms']:>6} {r['peak']:>6} {r['centroid']:>8} {r['rolloff']:>7} {r['flatness']:>6} "
                      f"{r['zcr']:>7} {r['dissonance']:>6} {r['bands']:>16}")
            else:
                print(f"{name:<9} {p['point']:<15} {r or 'no wav'}")
        print(f"          target: {TARGETS[name]}")
    print(f"\nWAVs: {WORK}/wav/  (points: {POINTS})")


if __name__ == "__main__":
    main()
