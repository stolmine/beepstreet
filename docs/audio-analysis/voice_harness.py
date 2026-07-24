#!/usr/bin/env python3
"""voice_harness.py — autonomous voice-tuning loop for beepstreet.

Render each SynthDef (via SuperCollider NRT, offline — no device, no norns conflict)
using the REAL Lua resolver params, analyze the audio with essentia, and print the
measured features next to the per-voice sonic targets. Re-run after editing
lib/voices.lua or lib/Engine_Beepstreet.sc — it always reflects the current code.

Run with the essentia venv python:
  <scratch>/essentia-venv/bin/python docs/audio-analysis/voice_harness.py [workdir]

Pipeline: luajit(resolver) -> params ; python builds a render.scd embedding the
engine's SynthDefs -> sclang writes 7 OSC scores -> scsynth NRT -> 7 WAVs ->
essentia MusicExtractor -> comparison table.
"""
import json, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WORK = sys.argv[1] if len(sys.argv) > 1 else "/tmp/beepstreet-harness"
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


def sh(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=120, **kw)


def get_params():
    r = sh(["luajit", os.path.join(ROOT, "docs/audio-analysis/render_params.lua"), ROOT])
    if r.returncode != 0:
        sys.exit("resolver failed:\n" + r.stderr)
    return {p["voice"]: p for p in json.loads(r.stdout.strip())}


def extract_synthdefs():
    txt = open(ENGINE).read()
    defs = re.findall(r"SynthDef\(.*?\}\)\.add;", txt, re.DOTALL)
    if len(defs) != len(ORDER):
        sys.exit(f"expected {len(ORDER)} SynthDefs, found {len(defs)}")
    return defs


def build_render_scd(params, defs, oscdir):
    lines = ["("]
    lines += defs
    for name in ORDER:
        p = params[name]
        end = round(HITS[-1] + p["rel"] + 0.4, 3)
        events = [f'[0.0, [\\d_recv, SynthDescLib.global[\\{name}].def.asBytes]]']
        for i, t in enumerate(HITS):
            a = (f'\\out,0, \\freq,{p["freq"]:.3f}, \\amp,{p["amp"]:.4f}, \\atk,{p["atk"]:.5f}, '
                 f'\\rel,{p["rel"]:.4f}, \\curve,{p["curve"]:.3f}, \\pan,0, '
                 f'\\p1,{p["p1"]:.5f}, \\p2,{p["p2"]:.5f}, \\p3,{p["p3"]:.5f}')
            events.append(f'[{t}, [\\s_new, \\{name}, {1000+i}, 0, 0, {a}]]')
        events.append(f'[{end}, [\\c_set, 0, 0]]')
        score = ",\n  ".join(events)
        lines.append(f'Score([\n  {score}\n]).writeOSCFile("{oscdir}/{name}.osc");')
    lines += ['"__RENDERED__".postln;', "0.exit;", ")"]
    return "\n".join(lines)


def render(params, oscdir, wavdir):
    for name in ORDER:
        p = params[name]
        dur = round(HITS[-1] + p["rel"] + 0.5, 3)
        osc, wav = f"{oscdir}/{name}.osc", f"{wavdir}/{name}.wav"
        r = sh([SCSYNTH, "-o", "2", "-N", osc, "_", wav, "48000", "WAV", "int16"])
        if not os.path.exists(wav):
            print(f"  ! render failed for {name}: {r.stdout[-200:]} {r.stderr[-200:]}")


def analyze(wavdir):
    import essentia
    essentia.log.warningActive = False; essentia.log.infoActive = False
    import essentia.standard as es
    rows = {}
    for name in ORDER:
        wav = f"{wavdir}/{name}.wav"
        if not os.path.exists(wav):
            continue
        try:
            f, _ = es.MusicExtractor(lowlevelStats=["mean"], rhythmStats=["mean"], tonalStats=["mean"])(wav)
        except Exception as e:
            rows[name] = f"analysis fail: {e}"; continue
        eb = [f["lowlevel.spectral_energyband_low.mean"], f["lowlevel.spectral_energyband_middle_low.mean"],
              f["lowlevel.spectral_energyband_middle_high.mean"], f["lowlevel.spectral_energyband_high.mean"]]
        s = sum(eb) or 1
        rows[name] = dict(
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
    params = get_params()
    scd = build_render_scd(params, extract_synthdefs(), f"{WORK}/osc")
    open(f"{WORK}/render.scd", "w").write(scd)
    r = sh([SCLANG, f"{WORK}/render.scd"])
    if "__RENDERED__" not in r.stdout:
        sys.exit("sclang render failed:\n" + r.stdout[-800:] + r.stderr[-400:])
    render(params, f"{WORK}/osc", f"{WORK}/wav")
    rows = analyze(f"{WORK}/wav")
    print(f"\n{'voice':<9} {'centroid':>8} {'rolloff':>7} {'flat':>6} {'zcr':>7} {'diss':>6} {'bands L/mL/mH/H':>16}")
    print("-" * 78)
    for name in ORDER:
        r = rows.get(name)
        if isinstance(r, dict):
            print(f"{name:<9} {r['centroid']:>8} {r['rolloff']:>7} {r['flatness']:>6} "
                  f"{r['zcr']:>7} {r['dissonance']:>6} {r['bands']:>16}")
            print(f"          target: {TARGETS[name]}")
        else:
            print(f"{name:<9} {r or 'no wav'}")
    print(f"\nWAVs: {WORK}/wav/  (preset x=.6 y=.6 z=.5)")


if __name__ == "__main__":
    main()
