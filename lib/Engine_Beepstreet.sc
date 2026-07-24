// lib/Engine_Beepstreet.sc — norns (Crone) engine for beepstreet.
//
// Seven voice SynthDefs, one command each, all sharing the signature:
//   freq, amp, atk, rel, curve, pan, p1, p2, p3
// freq comes from the grid pitch; p1/p2/p3 are type-specific scalars resolved
// Lua-side (lib/voices.lua) from the X/Y/Z macros. The engine is a dumb DSP host.
// Per-note synths spawn into voiceGroup and free themselves (Done.freeSelf).

Engine_Beepstreet : CroneEngine {
	var voiceGroup;

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	alloc {
		voiceGroup = Group.new(context.xg);

		// beep — Ikeda dual-sine ping: two detuned sines + cross-FM + an inharmonic partner tone.
		//   p1 = detune (cents), p2 = cross-FM index, p3 = inharmonic partner-tone level 0..1
		SynthDef(\beep, { arg out, freq=440, amp=0.3, atk=0.001, rel=0.2, curve= -4, pan=0, p1=0, p2=0, p3=0;
			var s1, s2, s3, sig, env, f2;
			f2 = freq * (2 ** (p1 / 1200));
			s2 = SinOsc.ar(f2);
			s1 = SinOsc.ar(freq + (s2 * p2 * freq));
			s3 = SinOsc.ar(freq * 2.757) * p3;                                 // clangy partner tone, irrational ratio
			sig = ((s1 + s2) * 0.5) + (s3 * 0.4);
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// bass — clinical: pure sine fundamental + integer-ratio FM (harmonic edge, no
		// filter) + sub octave, GATED. Spectral-domain brightness = FM index, not a
		// cutoff sweep — the "clinical/digital" not "synthy" character.
		//   p1 = FM index (0..6), p2 = sub level (0..1)
		SynthDef(\bass, { arg out, freq=110, amp=0.34, atk=0.01, rel=0.5, curve= -4, pan=0, p1=0, p2=0.5, p3=0;
			var fund, sub, sig, env;
			fund = SinOsc.ar(freq, SinOsc.ar(freq * 2, 0, p1.clip(0, 6)));   // integer-ratio PM = clean harmonics
			sub  = SinOsc.ar(freq * 0.5) * p2.clip(0, 1);
			sig  = (fund * 0.6) + (sub * 0.9);
			sig  = (sig * 0.85).tanh;                                        // gentle safety only, no drive
			env  = EnvGen.ar(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// click1 — bright Ikeda click: impulse<->noise excitation rung through a Ringz,
		// HPF'd. Reliable/audible (an ultra-short ultrasonic tick reads as silence on the
		// device). X = tonal->noise, Z = ring/center freq, Y (rel) = ring length.
		//   p1 = noise amount (0..1), p2 = ring/center freq
		SynthDef(\click1, { arg out, freq=440, amp=0.4, atk=0, rel=0.01, curve= -4, pan=0, p1=0, p2=6000, p3=0;
			var env, c, exc, sig;
			env = EnvGen.ar(Env.perc(0.0002, rel.max(0.004), 1, curve), doneAction: Done.freeSelf);
			c = p2.clip(1000, 16000);
			exc = (Impulse.ar(0) * (1 - p1.clip(0, 1))) + (WhiteNoise.ar * p1.clip(0, 1));
			sig = Ringz.ar(exc, c, rel.clip(0.004, 0.06)) * 0.6;
			sig = HPF.ar(sig, 400);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// click2 — woody/dry modal click (SND woodblock).
		//   p1 = resonance 0..1 (dead thud->woodblock ping), p2 = center freq (400..4000 from resolver)
		SynthDef(\click2, { arg out, freq=440, amp=0.3, atk=0, rel=0.03, curve= -4, pan=0, p1=0.05, p2=1500, p3=0;
			var env, c, ring, exc, sig;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			c = p2.clip(300, 4500);
			ring = 0.004 + (p1.clip(0, 1) * 0.076);                            // 4ms dead -> 80ms ringing
			exc = WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.0015));         // 1.5ms noise burst = wood-fiber strike
			sig = Klank.ar(`[ [c, c * 2.756, c * 5.404], [1, 0.42, 0.18], [ring, ring * 0.6, ring * 0.35] ], exc);
			sig = (sig * (1.2 + p1) * (0.05 / ring).clip(0.5, 14)).tanh;       // deterministic compaction; 1/ring comp flattens peak across X
			sig = LPF.ar(HPF.ar(sig, 250), 6000);                              // mid-forward: no rumble, no gloss
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// noise — colour arc brown->white->rate-crushed digital crackle, through an LP/BP/HP morph.
		//   p1 = colour 0..2 (0 brown, 1 white, 1..2 latch sample-rate crush toward ~750Hz hold rate)
		//   p2 = filter center, p3 = filter morph (0 LP..0.5 BP..1 HP)
		SynthDef(\noise, { arg out, freq=440, amp=0.28, atk=0.005, rel=0.2, curve= -4, pan=0, p1=0, p2=4000, p3=0;
			var src, holdRate, sig, env, c, w;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			w = p1.clip(0, 1);
			src = (BrownNoise.ar * (1 - w)) + (WhiteNoise.ar * w);
			holdRate = 24000 * (0.5 ** ((p1.clip(1, 2) - 1) * 5.7));           // 24k (transparent) -> ~460Hz (crackle)
			sig = Latch.ar(src, Impulse.ar(holdRate)) * (1 + ((p1.clip(1, 2) - 1) * 1.2)); // makeup: crush loses band energy
			c = p2.clip(40, 18000).min(holdRate * 4);   // crushed spectrum dies above ~4x hold rate: keep Z on the live part
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(sig, c, 0.55), BPF.ar(sig, c, 0.5), RHPF.ar(sig, c, 0.55) ]).softclip; // tame resonant crackle spikes, deterministic
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// kick — Ikeda bare sub stab -> Tessera modal clang. A pure pitched sub stab at
		// X=0; as X rises, an inharmonic modal lattice (cluster around ~10x the
		// fundamental, two decay tiers: core modes ring, sideband modes snap) fades in,
		// gets folded, and the sub+bank are soft-clipped TOGETHER (the character is both
		// slamming the clipper as one). LPF stays deep at X=0 (drive can never brighten
		// the bare stab) and opens with X so the clang breathes.
		//   p1 = lattice/clang X 0..1 (bare sub stab -> compound clang), p2 = pitch-decay
		//   seconds (Z), p3 unused.
		SynthDef(\kick, { arg out, freq=60, amp=0.34, atk=0.001, rel=0.35, curve= -4, pan=0, p1=0, p2=0.05, p3=0;
			var env, fenv, sub, fc, spread, ks, amps, rings, exc, bank, clk, pre, sig, cut;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			fenv = EnvGen.ar(Env([freq * (1.5 + (p2 * 25)), freq], [p2.clip(0.005, 0.3)], \exp)); // Z: tight thump -> laser fall (depth+time together)
			sub = SinOsc.ar(fenv);
			fc = (freq * 10).clip(240, 1600);                          // clang cluster center (~544Hz region)
			spread = 0.05 + (p1 * 0.13);                               // lattice spacing r
			ks    = [-5, -3, -2, -1, 0, 1, 2, 4];                      // inharmonic lattice offsets
			amps  = [0.4, 0.55, 0.7, 0.9, 1, 0.9, 0.7, 0.5];
			rings = [0.014, 0.016, 0.03, 0.045, 0.05, 0.045, 0.03, 0.015] * (0.6 + p1); // two decay tiers
			exc = Impulse.ar(0) + (WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.002)) * 0.5);
			bank = Klank.ar(`[ (1 + (ks * spread)).max(0.12) * fc, amps, rings ], exc);
			bank = (bank * 14 * (1 + (p1 * 1.5))).fold2(1);            // fold -> extra clang harmonics at high X
			clk = HPF.ar(WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.004)), 3000) * (0.1 + (p1 * 0.35));
			pre = (sub * (1.15 - (p1 * 0.75))) + (bank * (p1 ** 0.7) * 2.5); // sub ducks as the clang takes over (Ikeda type B = stack alone)
			sig = (pre * 1.3).softclip;
			cut = (freq * 5).clip(200, 520).max(fc * p1 * 4.2);        // deep at X=0, opens with X
			sig = LPF.ar(sig, cut) + clk;
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// additive — Plaits-style chord engine over stacked FM voices, stereo. A 7-chord
		// table (OCT, P5, sus4, m, m7, m9, m11 — tension-ordered); p1 is a CONTINUOUS
		// chord position 0..6, linearly interpolating the 4 note ratios between adjacent
		// chords (chord glide, no snap). p2 = brightness: note-level rolloff (dark
		// fundamental-heavy -> all notes equal). p3 = voicing/roughness: continuously
		// lifts lower notes by octaves (rotation through inversions; fractional lift
		// passes through rough territory — intended) AND raises per-note FM-sideband
		// index (irrational modulator ratios). Motion: fixed micro-detune per note
		// (~±0.25%, different L/R) + slow LFNoise1 drift (deliberate Hz-level beating)
		// => stereo quaver. Stereo width grows with p3. Env is a gate/window
		// (Env.linen, rel = sustain length).
		SynthDef(\additive, { arg out, freq=220, amp=0.26, atk=0.01, rel=0.6, curve= -4, pan=0, p1=0, p2=0.5, p3=0;
			var chords, mods, detL, detR, env, pos, i0, frac, bright, sigL, sigR, width, lvls, norm, notesL, notesR, mid, side;
			chords = [
				[1, 2, 2, 4],                  // OCT
				[1, 1.5, 2, 3],                // P5
				[1, 1.3333, 2, 2.6667],        // sus4
				[1, 1.2, 1.5, 2],              // m
				[1, 1.2, 1.5, 1.7778],         // m7
				[1, 1.2, 1.5, 2.25],           // m9
				[1, 1.2, 1.7778, 2.6667]       // m11
			];
			mods = [1.41, 1.73, 2.76, 3.16];                           // irrational modulator ratios
			detL = [0.9975, 1.0025, 0.998, 1.003];                     // fixed micro-detune, L
			detR = [1.0025, 0.9975, 1.002, 0.997];                     // mirrored, R
			env = EnvGen.ar(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			pos = p1.clip(0, 6);
			i0 = pos.floor;
			frac = pos - i0;
			bright = 0.35 + (0.65 * p2.clip(0, 1));                    // note-level rolloff base
			lvls = Array.fill(4, { arg k; bright ** k });
			norm = 0.9 / (lvls.sum + 0.4);
			notesL = Array.fill(4, { arg k;
				var a = Select.kr(i0, chords.collect({ arg ch; ch[k] }));
				var b = Select.kr((i0 + 1).min(6), chords.collect({ arg ch; ch[k] }));
				var ratio = a + ((b - a) * frac);
				var lift = ((p3.clip(0, 1) * 3) - k).clip(0, 1);       // rotate lower notes up octaves, continuous
				var cf = freq * ratio * ((lift * 0.6931).exp);
				var drift = 1 + (LFNoise1.kr(0.11 + (k * 0.04)) * 0.0015); // slow living detune
				PMOsc.ar(cf * detL[k] * drift, cf * mods[k], p3.clip(0, 1) * 2.2) * lvls[k]
			});
			notesR = Array.fill(4, { arg k;
				var a = Select.kr(i0, chords.collect({ arg ch; ch[k] }));
				var b = Select.kr((i0 + 1).min(6), chords.collect({ arg ch; ch[k] }));
				var ratio = a + ((b - a) * frac);
				var lift = ((p3.clip(0, 1) * 3) - k).clip(0, 1);
				var cf = freq * ratio * ((lift * 0.6931).exp);
				var drift = 1 + (LFNoise1.kr(0.13 + (k * 0.05)) * 0.0015);
				PMOsc.ar(cf * detR[k] * drift, cf * mods[k], p3.clip(0, 1) * 2.2) * lvls[k]
			});
			sigL = Mix.new(notesL) * norm;
			sigR = Mix.new(notesR) * norm;
			width = 0.25 + (p3.clip(0, 1) * 0.45);                     // stereo width opens with Z
			mid  = (sigL + sigR) * 0.5;
			side = (sigL - sigR) * 0.5 * width * 2;
			sigL = mid + side; sigR = mid - side;
			Out.ar(out, Balance2.ar(sigL * env * amp, sigR * env * amp, pan));
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

	trigVoice { arg name, msg;
		Synth(name, [
			\out, context.out_b,
			\freq, msg[1], \amp, msg[2], \atk, msg[3], \rel, msg[4],
			\curve, msg[5], \pan, msg[6], \p1, msg[7], \p2, msg[8], \p3, msg[9]
		], voiceGroup);
	}

	free { voiceGroup.free; }
}
