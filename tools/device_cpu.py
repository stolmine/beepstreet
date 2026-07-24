#!/usr/bin/env python3
"""device_cpu.py — real-time CPU proxy for beepstreet voices, measured ON THE DEVICE.

Why: the Mac harness renders NRT faster-than-real-time, so it is structurally
blind to CPU cost — a voice set that renders fine offline can saturate the Pi's
live scsynth under polyphony (and a saturated server can't answer server.sync,
stalling the NEXT script load). This tool renders a dense polyphonic passage
with the DEVICE's own scsynth (NRT, /tmp only — never touches the live
instrument) and times it with wall-clock. wall/audio ratio ~= fraction of one
core's DSP budget; if it approaches 1.0 the passage will overload in real time.

Safety: no device sclang (its startup touches the live server port). Scores are
compiled on the Mac (SynthDef bytecode + OSC scores are platform-independent),
scp'd to norns:/tmp/bpcpu, and only `scsynth -N` runs there.

Usage:
  python3 tools/device_cpu.py [options]
    --engine PATH     engine .sc file to measure (default lib/Engine_Beepstreet.sc)
    --root PATH       repo root whose lib/voices.lua resolves params (default repo)
    --voices LIST     comma list (default all 7); "none" = skip per-voice runs
    --skip-all        skip the aggregate all-voices scenario
    --interval S      per-voice hit spacing (default 0.06 = 32nd grid worst case)
    --span S          passage length before tails (default 3.0)
    --cap SPEC        simulate voice stealing: "additive=4,bass=4" or "*=6"
                      (hit i frees the node spawned cap hits earlier)
    --runs N          timed renders per scenario, min taken (default 2)
    --host H          ssh host (default we@norns.local)
    --workdir PATH    local scratch (default $TMPDIR/beepstreet-devcpu)

Reads worst-case macro points per voice (WORST below), resolves real params via
docs/audio-analysis/render_params.lua + the target root's lib/voices.lua, so it
always measures what the instrument would actually play.

NOTE: def dispatch (voice name -> SynthDef name) must mirror
Engine_Beepstreet.trigVoice — see dispatch() below.
"""
import argparse, os, re, subprocess, sys, tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SCLANG = "/Applications/SuperCollider.app/Contents/MacOS/sclang"
ORDER = ["click1", "click2", "beep", "additive", "noise", "kick", "bass"]

# worst-case macro point per voice: longest release + heaviest signal path.
# noise x=0.875 lands in the velvet<->particle segment (Dust->Ringz, priciest).
WORST = {
    "click1":   (1.0, 1.0, 1.0),
    "click2":   (1.0, 1.0, 1.0),
    "beep":     (1.0, 1.0, 1.0),
    "additive": (0.5, 1.0, 1.0),
    "noise":    (0.875, 1.0, 0.5),
    "kick":     (1.0, 1.0, 1.0),
    "bass":     (1.0, 1.0, 1.0),
}


def dispatch(voice, p, names):
    """SynthDef name for a hit — MUST mirror Engine_Beepstreet.trigVoice dispatch.
    Falls back to the plain voice name when the engine has no variant defs
    (baseline/parked engines)."""
    if voice == "noise" and "noise_wc" in names:
        seg = min(int(p["p1"]), 3)          # tent morph: only 2 stations live
        return ["noise_wc", "noise_cc", "noise_cv", "noise_vp"][seg]
    return voice


def sh(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=900, **kw)


def get_params(root, voices):
    pts = ";".join(f"{WORST[v][0]},{WORST[v][1]},{WORST[v][2]}" for v in voices)
    r = sh(["luajit", os.path.join(ROOT, "docs/audio-analysis/render_params.lua"), root, pts])
    if r.returncode != 0:
        sys.exit("resolver failed:\n" + r.stderr)
    import json
    rows = json.loads(r.stdout.strip())
    out = {}
    for v in voices:
        lbl = "x%.1fy%.1fz%.1f" % WORST[v]
        out[v] = next(p for p in rows if p["voice"] == v and p["point"] == lbl)
    return out


def extract_synthdefs(engine):
    txt = open(engine).read()
    defs = re.findall(r"^\t*SynthDef\(\\\w.*?\}\)\.add;", txt, re.DOTALL | re.MULTILINE)
    defs = [d.strip() for d in defs]
    if not defs:
        sys.exit(f"no SynthDefs found in {engine}")
    names = [re.match(r"SynthDef\(\\(\w+)", d).group(1) for d in defs]
    return defs, names


def hit_args(p):
    return (f'\\out,0, \\freq,{p["freq"]:.3f}, \\amp,{p["amp"]:.4f}, \\atk,{p["atk"]:.5f}, '
            f'\\rel,{p["rel"]:.4f}, \\curve,{p["curve"]:.3f}, \\pan,0, '
            f'\\p1,{p["p1"]:.5f}, \\p2,{p["p2"]:.5f}, \\p3,{p["p3"]:.5f}')


def build_scenarios(params, voices, interval, span, caps, skip_all, names):
    """-> list of dicts {name, dur, events:[(t, oscstr)], defs:set}"""
    scens = []

    def one(name, members, iv):
        events, defs, nid = [], set(), 1000
        maxrel = max(params[v]["rel"] for v in members)
        n = int(span / iv)
        spawned = {v: [] for v in members}
        for i in range(n):
            for j, v in enumerate(members):
                t = round(i * iv + j * 0.003, 4)
                d = dispatch(v, params[v], names)
                defs.add(d)
                events.append((t, f'[\\s_new, \\{d}, {nid}, 0, 0, {hit_args(params[v])}]'))
                spawned[v].append(nid)
                cap = caps.get(v, caps.get("*"))
                if cap and len(spawned[v]) > cap:
                    old = spawned[v].pop(0)
                    events.append((t, f'[\\n_free, {old}]'))
                nid += 1
        dur = round(span + maxrel + 0.5, 3)
        scens.append(dict(name=name, dur=dur, events=events, defs=defs,
                          hits=n * len(members), maxrel=maxrel))

    for v in voices:
        one(v, [v], interval)
    if not skip_all:
        one("all7", list(params.keys()), interval)
    scens.append(dict(name="_baseline", dur=scens[0]["dur"] if scens else span,
                      events=[], defs=set(), hits=0, maxrel=0))
    return scens


def write_scores(scens, alldefs, workdir):
    lines = ["("] + alldefs
    for sc in scens:
        ev = [f'[0.0, [\\d_recv, SynthDescLib.global[\\{d}].def.asBytes]]' for d in sorted(sc["defs"])]
        ev += [f"[{t}, {e}]" for t, e in sc["events"]]
        ev.append(f'[{sc["dur"]}, [\\c_set, 0, 0]]')
        body = ",\n  ".join(ev)
        lines.append(f'Score([\n  {body}\n]).writeOSCFile("{workdir}/{sc["name"]}.osc");')
    lines += ['"__SCORES_DONE__".postln;', "0.exit;", ")"]
    scd = f"{workdir}/scores.scd"
    open(scd, "w").write("\n".join(lines))
    r = sh([SCLANG, scd])
    if "__SCORES_DONE__" not in r.stdout:
        sys.exit("mac sclang score build failed:\n" + r.stdout[-1200:] + r.stderr[-400:])


def render_on_device(scens, host, workdir, runs):
    names = [sc["name"] for sc in scens]
    r = sh(["ssh", "-o", "BatchMode=yes", host, "mkdir -p /tmp/bpcpu && rm -f /tmp/bpcpu/*.osc"])
    if r.returncode != 0:
        sys.exit("ssh failed: " + r.stderr)
    r = sh(["scp", "-q"] + [f"{workdir}/{n}.osc" for n in names] + [f"{host}:/tmp/bpcpu/"])
    if r.returncode != 0:
        sys.exit("scp failed: " + r.stderr)
    script = "\n".join(
        f'for i in $(seq {runs}); do '
        f't0=$(date +%s.%N); '
        f'/usr/local/bin/scsynth -o 2 -N /tmp/bpcpu/{n}.osc _ /tmp/bpcpu/out.wav 48000 WAV int16 '
        f'>/dev/null 2>&1; '
        f't1=$(date +%s.%N); awk "BEGIN{{printf \\"RES {n} %.3f\\n\\", $t1-$t0}}"; done'
        for n in names)
    r = sh(["ssh", "-o", "BatchMode=yes", host, script])
    times = {}
    for line in r.stdout.splitlines():
        m = re.match(r"RES (\S+) ([\d.]+)", line)
        if m:
            times.setdefault(m.group(1), []).append(float(m.group(2)))
    missing = [n for n in names if n not in times]
    if missing:
        sys.exit(f"no timing for {missing}:\n{r.stdout[-500:]}{r.stderr[-500:]}")
    return {n: min(v) for n, v in times.items()}


def report(scens, times, interval, span):
    base = times.get("_baseline", 0.0)
    print(f"\ndevice: wall-clock NRT render (min of runs); baseline (empty score) {base:.2f}s")
    print(f"passage: hits every {interval * 1000:.0f}ms for {span:.1f}s per voice\n")
    print(f"{'scenario':<10} {'hits':>5} {'audio_s':>8} {'wall_s':>7} {'dsp_s':>7} {'RT-ratio':>8}  verdict")
    print("-" * 66)
    for sc in scens:
        if sc["name"] == "_baseline":
            continue
        w = times[sc["name"]]
        dsp = max(w - base, 0.0)
        ratio = dsp / sc["dur"]
        verdict = ("OVERLOAD" if ratio > 0.85 else
                   "danger" if ratio > 0.6 else
                   "tight" if ratio > 0.35 else "ok")
        print(f'{sc["name"]:<10} {sc["hits"]:>5} {sc["dur"]:>8.2f} {w:>7.2f} {dsp:>7.2f} {ratio:>8.2f}  {verdict}')
    print("\nRT-ratio ~= fraction of one Pi core consumed if played live. The live")
    print("server shares that core with jack/crone; keep the aggregate under ~0.5.")


def parse_caps(spec):
    caps = {}
    if spec:
        for part in spec.split(","):
            k, v = part.split("=")
            caps[k.strip()] = int(v)
    return caps


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine", default=os.path.join(ROOT, "lib/Engine_Beepstreet.sc"))
    ap.add_argument("--root", default=ROOT)
    ap.add_argument("--voices", default=",".join(ORDER))
    ap.add_argument("--skip-all", action="store_true")
    ap.add_argument("--interval", type=float, default=0.06)
    ap.add_argument("--span", type=float, default=3.0)
    ap.add_argument("--cap", default="")
    ap.add_argument("--runs", type=int, default=2)
    ap.add_argument("--host", default="we@norns.local")
    ap.add_argument("--workdir", default=os.path.join(tempfile.gettempdir(), "beepstreet-devcpu"))
    a = ap.parse_args()

    voices = [] if a.voices == "none" else [v.strip() for v in a.voices.split(",")]
    os.makedirs(a.workdir, exist_ok=True)
    params = get_params(a.root, voices or ORDER)
    defs, names = extract_synthdefs(a.engine)
    need = {dispatch(v, params[v], names) for v in params}
    lost = need - set(names)
    if lost:
        sys.exit(f"engine {a.engine} lacks defs {sorted(lost)} (dispatch mirror out of date?)")
    scens = build_scenarios(params, voices, a.interval, a.span, parse_caps(a.cap), a.skip_all, names)
    write_scores(scens, defs, a.workdir)
    times = render_on_device(scens, a.host, a.workdir, a.runs)
    report(scens, times, a.interval, a.span)


if __name__ == "__main__":
    main()
