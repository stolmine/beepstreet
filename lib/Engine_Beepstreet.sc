// lib/Engine_Beepstreet.sc — norns (Crone) engine for beepstreet.
//
// Seven voice commands, one per voice type, all sharing the signature:
//   freq, amp, atk, rel, curve, pan, p1, p2, p3
// freq comes from the grid pitch; p1/p2/p3 are type-specific scalars resolved
// Lua-side (lib/voices.lua) from the X/Y/Z macros. The engine is a dumb DSP host.
// Per-note synths spawn into voiceGroup and free themselves (Done.freeSelf).
//
// CPU regime (this engine must run dense polyphony on a Pi3 — measured with
// tools/device_cpu.py, the device NRT wall-clock proxy):
//  - Per-note params NEVER change after spawn, so every param is a SCALAR-rate
//    control (\name.ir) and all param-derived morph/lattice math runs once at
//    synth init — zero per-block cost. Only genuinely time-varying UGens are
//    audio/control rate. Do not "upgrade" these to kr: nothing n_sets them.
//  - noise: the 5-station tent morph only ever hears 2 adjacent stations, so it
//    is 4 two-station segment defs picked at spawn (trigVoice). The def
//    dispatch is mirrored in tools/device_cpu.py (dispatch()) — keep in sync.
//  - Every def has \gate (default 1): a cutoff EnvGen frees the synth ~10ms
//    after gate->0. Voice stealing: per-type caps below; oldest synth gets
//    gate=0 when a spawn would exceed the cap. This bounds worst-case overlap
//    (the earlier device engine-load hang was RT CPU saturation from unbounded
//    3s-release drones retriggering on the 32nd grid — a saturated server
//    can't answer the next script load's server.sync).

Engine_Beepstreet : CroneEngine {
	var voiceGroup;
	var active;   // per-type List of live synths, oldest first (voice stealing)
	var caps;     // per-type polyphony cap; see planning/ledger.toml cost notes

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	alloc {
		voiceGroup = Group.new(context.xg);
		active = Dictionary.new;
		[\beep, \bass, \click1, \click2, \noise, \kick, \additive].do { arg k;
			active[k] = List.new;
		};
		// caps sized from measured per-instance cost (device proxy, worst-case
		// macros): additive ~1.9%, bass ~1.2%, noise ~0.9%, kick ~0.9%/core.
		caps = (beep: 8, bass: 6, click1: 8, click2: 8, noise: 6, kick: 4, additive: 4);

		// beep — Ikeda dual-sine ping: two detuned sines + cross-FM + an inharmonic partner tone.
		//   p1 = detune (cents), p2 = cross-FM index, p3 = inharmonic partner-tone level 0..1
		SynthDef(\beep, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.3), atk=\atk.ir(0.001), rel=\rel.ir(0.2),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(0), p3=\p3.ir(0);
			var s1, s2, s3, sig, env, kill, f2;
			f2 = freq * (2 ** (p1 / 1200));
			s2 = SinOsc.ar(f2);
			s1 = SinOsc.ar(freq + (s2 * (p2 * freq)));
			s3 = SinOsc.ar(freq * 2.757) * (p3 * 0.4);                         // clangy partner tone, irrational ratio
			sig = ((s1 + s2) * 0.5) + s3;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// bass — clinical: pure sine fundamental + integer-ratio FM (harmonic edge, no
		// filter) + sub octave, GATED. softclip (not tanh — cheap on ARM) as gentle safety.
		//   p1 = FM index (0..6), p2 = sub level (0..1)
		SynthDef(\bass, { arg out, gate=1;
			var freq=\freq.ir(110), amp=\amp.ir(0.34), atk=\atk.ir(0.01), rel=\rel.ir(0.5),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(0.5);
			var fund, sub, sig, env, kill;
			fund = SinOsc.ar(freq, SinOsc.ar(freq * 2, 0, p1.clip(0, 6)));   // integer-ratio PM = clean harmonics
			sub  = SinOsc.ar(freq * 0.5) * p2.clip(0, 1);
			sig  = (fund * 0.6) + (sub * 0.9);
			sig  = (sig * 0.85).softclip;                                    // gentle safety only, no drive
			env  = EnvGen.ar(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// click1 — bright Ikeda click: impulse<->noise excitation rung through a Ringz, HPF'd.
		//   p1 = noise amount (0..1), p2 = ring/center freq
		SynthDef(\click1, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.4), rel=\rel.ir(0.01),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(6000);
			var env, kill, c, exc, sig;
			env = EnvGen.ar(Env.perc(0.0002, rel.max(0.004), 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			c = p2.clip(1000, 16000);
			exc = (Impulse.ar(0) * (1 - p1.clip(0, 1))) + (WhiteNoise.ar * p1.clip(0, 1));
			sig = Ringz.ar(exc, c, rel.clip(0.004, 0.06)) * 0.6;
			sig = HPF.ar(sig, 400);
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// click2 — woody/dry modal click (SND woodblock).
		//   p1 = resonance 0..1 (dead thud->woodblock ping), p2 = center freq (400..4000)
		SynthDef(\click2, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.3), atk=\atk.ir(0), rel=\rel.ir(0.03),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0.05), p2=\p2.ir(1500);
			var env, kill, c, ring, exc, sig;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			c = p2.clip(300, 4500);
			ring = 0.004 + (p1.clip(0, 1) * 0.076);                            // 4ms dead -> 80ms ringing
			exc = WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.0015));         // 1.5ms noise burst = wood-fiber strike
			sig = Klank.ar(`[ [c, c * 2.756, c * 5.404], [1, 0.42, 0.18], [ring, ring * 0.6, ring * 0.35] ], exc);
			sig = (sig * (1.2 + p1) * (0.05 / ring).clip(0.5, 14)).tanh;       // deterministic compaction
			sig = LPF.ar(HPF.ar(sig, 250), 6000);                              // mid-forward: no rumble, no gloss
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// noise — Rauschen-style model morph: white -> crushed -> crackle -> velvet ->
		// particle, tent-weight crossfade on X, through the LP/BP/HP morph (Z). Textures
		// pitch-track the grid freq. Split into 4 two-station segment defs (only the two
		// adjacent stations of the tent are ever non-zero) picked at spawn — the full
		// 5-source sum would run every station on every note for nothing.
		//   p1 = model position 0..4, p2 = filter center, p3 = filter morph (0 LP..0.5 BP..1 HP)
		// (defs are literal blocks, not loop-generated: tools/device_cpu.py and
		// docs/audio-analysis/voice_harness.py extract each def block verbatim)

		// noise_wc — segment 0: white <-> crush (Latch sample-rate crush, hold rate pitch-tracked)
		SynthDef(\noise_wc, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.28), atk=\atk.ir(0.005), rel=\rel.ir(0.2),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(4000), p3=\p3.ir(0);
			var env, kill, frac, sig, c;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			frac = p1.clip(0, 1);
			sig = (WhiteNoise.ar * (1 - frac))
				+ (Latch.ar(WhiteNoise.ar, Impulse.ar((freq * 8).clip(400, 8000))) * frac);
			c = p2.clip(40, 18000);
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(sig, c, 0.55), BPF.ar(sig, c, 0.5), RHPF.ar(sig, c, 0.55) ]).softclip;
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// noise_cc — segment 1: crush <-> crackle (Crackle 1.9 chaotic recurrence)
		SynthDef(\noise_cc, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.28), atk=\atk.ir(0.005), rel=\rel.ir(0.2),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(4000), p3=\p3.ir(0);
			var env, kill, frac, sig, c;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			frac = (p1 - 1).clip(0, 1);
			sig = (Latch.ar(WhiteNoise.ar, Impulse.ar((freq * 8).clip(400, 8000))) * (1 - frac))
				+ (Crackle.ar(1.9) * 6 * frac);
			c = p2.clip(40, 18000);
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(sig, c, 0.55), BPF.ar(sig, c, 0.5), RHPF.ar(sig, c, 0.55) ]).softclip;
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// noise_cv — segment 2: crackle <-> velvet (Dust2, density pitch-tracked)
		SynthDef(\noise_cv, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.28), atk=\atk.ir(0.005), rel=\rel.ir(0.2),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(4000), p3=\p3.ir(0);
			var env, kill, frac, sig, c;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			frac = (p1 - 2).clip(0, 1);
			sig = (Crackle.ar(1.9) * 6 * (1 - frac))
				+ (Dust2.ar((freq * 6).clip(300, 6000)) * 5 * frac);
			c = p2.clip(40, 18000);
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(sig, c, 0.55), BPF.ar(sig, c, 0.5), RHPF.ar(sig, c, 0.55) ]).softclip;
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// noise_vp — segment 3: velvet <-> particle (Dust -> Ringz, per-hit TExpRand freq)
		SynthDef(\noise_vp, { arg out, gate=1;
			var freq=\freq.ir(440), amp=\amp.ir(0.28), atk=\atk.ir(0.005), rel=\rel.ir(0.2),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(4000), p3=\p3.ir(0);
			var env, kill, frac, ptrig, sig, c;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			frac = (p1 - 3).clip(0, 1);
			ptrig = Dust.ar((freq * 0.8).clip(15, 200));
			sig = (Dust2.ar((freq * 6).clip(300, 6000)) * 5 * (1 - frac))
				+ (Ringz.ar(ptrig, TExpRand.ar(300, 5000, ptrig), 0.03) * 3 * frac);
			c = p2.clip(40, 18000);
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(sig, c, 0.55), BPF.ar(sig, c, 0.5), RHPF.ar(sig, c, 0.55) ]).softclip;
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// kick — Ikeda bare sub stab -> Tessera modal clang. Pure pitched sub stab at
		// X=0; as X rises an inharmonic modal lattice fades in, gets folded, and
		// sub+bank are soft-clipped together. LPF deep at X=0, opens with X.
		// Lattice trimmed 8 -> 5 modes (kept the ringing core -1/0/+1 and one snap
		// tier each side, -3/+4) — measured indistinguishable bands, ~40% cheaper bank.
		//   p1 = lattice/clang X 0..1, p2 = pitch-decay seconds (Z)
		SynthDef(\kick, { arg out, gate=1;
			var freq=\freq.ir(60), amp=\amp.ir(0.34), atk=\atk.ir(0.001), rel=\rel.ir(0.35),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(0.05);
			var env, kill, fenv, sub, fc, spread, ks, amps, rings, exc, bank, clk, pre, sig, cut;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			fenv = EnvGen.ar(Env([freq * (1.5 + (p2 * 25)), freq], [p2.clip(0.005, 0.3)], \exp)); // Z: tight thump -> laser fall
			sub = SinOsc.ar(fenv);
			fc = (freq * 10).clip(240, 1600);                          // clang cluster center (~544Hz region)
			spread = 0.05 + (p1 * 0.13);                               // lattice spacing r
			ks    = [-3, -1, 0, 1, 4];                                 // inharmonic lattice offsets (trimmed)
			amps  = [0.55, 0.9, 1, 0.9, 0.5];
			rings = [0.016, 0.045, 0.05, 0.045, 0.015] * (0.6 + p1);   // two decay tiers: core rings, sidebands snap
			exc = Impulse.ar(0) + (WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.002)) * 0.5);
			bank = Klank.ar(`[ (1 + (ks * spread)).max(0.12) * fc, amps, rings ], exc);
			bank = (bank * (14 * (1 + (p1 * 1.5)))).fold2(1);          // fold -> extra clang harmonics at high X
			clk = HPF.ar(WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.004)), 3000) * (0.1 + (p1 * 0.35));
			pre = (sub * (1.15 - (p1 * 0.75))) + (bank * ((p1 ** 0.7) * 2.5)); // sub ducks as the clang takes over
			sig = (pre * 1.3).softclip;
			cut = (freq * 5).clip(200, 520).max(fc * (p1 * 4.2));      // deep at X=0, opens with X
			sig = LPF.ar(sig, cut) + clk;
			Out.ar(out, Pan2.ar(sig * env * (amp * kill), pan));
		}).add;

		// additive — Plaits-style chord engine over stacked FM voices, stereo. 7-chord
		// table (OCT, P5, sus4, m, m7, m9, m11); p1 = continuous chord position 0..6
		// (tent-basis ratio interpolation = chord glide). p2 = brightness (note-level
		// rolloff). p3 = voicing/roughness: lifts lower notes by octaves, raises FM
		// index (irrational modulator ratios), opens stereo width. Motion: slow
		// LFNoise1 drift per note; the upper two notes are L/R detuned pairs
		// (interchannel beating = the quaver). CPU: the lower two notes are MONO
		// (their stereo beat is inaudible down there) — 6 PMOsc instead of 8 — and
		// all chord/lift/level math is scalar-rate (runs once at spawn).
		//   env is a gate/window (Env.linen): rel = flat sustain length (drone).
		SynthDef(\additive, { arg out, gate=1;
			var freq=\freq.ir(220), amp=\amp.ir(0.26), atk=\atk.ir(0.01), rel=\rel.ir(0.6),
				curve=\curve.ir(-4), pan=\pan.ir(0), p1=\p1.ir(0), p2=\p2.ir(0.5), p3=\p3.ir(0);
			var chords, mods, env, kill, pos, z, bright, lvls, norm, idx, cfs, drift;
			var low, upL, upR, sigL, sigR, width, mid, side;
			chords = [
				[1, 2, 2, 4],                  // OCT
				[1, 1.5, 2, 3],                // P5
				[1, 1.3333, 2, 2.6667],        // sus4
				[1, 1.2, 1.5, 2],              // m
				[1, 1.2, 1.5, 1.7778],         // m7
				[1, 1.2, 1.5, 2.25],           // m9
				[1, 1.2, 1.7778, 2.6667]       // m11
			];
			mods = [1.41, 1.73, 2.76, 3.16];                           // irrational modulator ratios -> inharmonic
			env = EnvGen.ar(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			kill = EnvGen.kr(Env.asr(0, 1, 0.01), gate, doneAction: Done.freeSelf);
			pos = p1.clip(0, 6);
			z = p3.clip(0, 1);
			bright = 0.35 + (0.65 * p2.clip(0, 1));                    // note-level rolloff base
			lvls = Array.fill(4, { arg k; bright ** k });
			norm = 0.9 / (lvls.sum + 0.4);
			idx = z * 2.2;                                             // FM sideband index
			cfs = Array.fill(4, { arg k;                               // scalar: chord glide + octave lift
				var ratio = (0..6).collect({ arg j; chords[j][k] * (1 - (pos - j).abs).clip(0, 1) }).sum;
				var lift = ((z * 3) - k).clip(0, 1);                   // rotate lower notes up octaves, continuous
				freq * ratio * ((lift * 0.6931).exp)
			});
			drift = Array.fill(4, { arg k; 1 + (LFNoise1.kr(0.11 + (k * 0.04)) * 0.0015) }); // slow living detune
			low = (PMOsc.ar(cfs[0] * drift[0], cfs[0] * mods[0], idx) * lvls[0])
				+ (PMOsc.ar(cfs[1] * drift[1], cfs[1] * mods[1], idx) * lvls[1]);
			upL = (PMOsc.ar(cfs[2] * 0.998 * drift[2], cfs[2] * mods[2], idx) * lvls[2])
				+ (PMOsc.ar(cfs[3] * 1.003 * drift[3], cfs[3] * mods[3], idx) * lvls[3]);
			upR = (PMOsc.ar(cfs[2] * 1.002 * drift[2], cfs[2] * mods[2], idx) * lvls[2])
				+ (PMOsc.ar(cfs[3] * 0.997 * drift[3], cfs[3] * mods[3], idx) * lvls[3]);
			sigL = (low + upL) * norm;
			sigR = (low + upR) * norm;
			width = 0.25 + (z * 0.45);                                 // stereo width opens with Z
			mid  = (sigL + sigR) * 0.5;
			side = (sigL - sigR) * (width);                            // 0.5 * width * 2
			sigL = mid + side; sigR = mid - side;
			Out.ar(out, Balance2.ar(sigL * env * (amp * kill), sigR * env * (amp * kill), pan));
		}).add;

		context.server.sync;

		// one command per voice type: freq amp atk rel curve pan p1 p2 p3
		this.addCommand("beep",     "fffffffff", { arg m; this.trigVoice(\beep, m) });
		this.addCommand("bass",     "fffffffff", { arg m; this.trigVoice(\bass, m) });
		this.addCommand("click1",   "fffffffff", { arg m; this.trigVoice(\click1, m) });
		this.addCommand("click2",   "fffffffff", { arg m; this.trigVoice(\click2, m) });
		this.addCommand("noise",    "fffffffff", { arg m; this.trigVoice(\noise, m) });
		this.addCommand("kick",     "fffffffff", { arg m; this.trigVoice(\kick, m) });
		this.addCommand("additive", "fffffffff", { arg m; this.trigVoice(\additive, m) });
	}

	// def dispatch — mirrored by tools/device_cpu.py dispatch(); keep in sync.
	defFor { arg name, msg;
		if(name == \noise) {
			^[\noise_wc, \noise_cc, \noise_cv, \noise_vp].at(msg[7].clip(0, 3.99).floor.asInteger);
		};
		^name
	}

	trigVoice { arg name, msg;
		var list, syn;
		list = active[name];
		if(list.size >= caps[name]) {                       // voice stealing: fade the oldest
			list.removeAt(0).set(\gate, 0);                 // 10ms cutoff env, then frees
		};
		syn = Synth(this.defFor(name, msg), [
			\out, context.out_b,
			\freq, msg[1], \amp, msg[2], \atk, msg[3], \rel, msg[4],
			\curve, msg[5], \pan, msg[6], \p1, msg[7], \p2, msg[8], \p3, msg[9]
		], voiceGroup);
		list.add(syn);
		syn.onFree({ list.remove(syn) });
	}

	free { voiceGroup.free; }
}
