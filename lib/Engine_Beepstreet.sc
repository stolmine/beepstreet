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

		// click1 — bright/ultrasonic Ikeda click: morphs impulse-tick -> resonant ping -> noise burst.
		//   p1 = morph 0..1 (tick->ping->burst), p2 = center freq (2k..12k from resolver)
		SynthDef(\click1, { arg out, freq=440, amp=0.3, atk=0, rel=0.01, curve= -4, pan=0, p1=0, p2=6000, p3=0;
			var env, c, w, tickw, pingw, burstw, ping, burst, sig;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			c = p2.clip(1000, 16000);
			w = p1.clip(0, 1) * 2;                                             // 0 tick .. 1 ping .. 2 burst
			tickw  = (1 - w).clip(0, 1);
			pingw  = (1 - (w - 1).abs).clip(0, 1);
			burstw = (w - 1).clip(0, 1);
			ping  = Ringz.ar(Impulse.ar(0), c, rel.clip(0.004, 0.08)) * 0.9;   // pitched ping, ring tracks Y (rel)
			burst = BPF.ar(WhiteNoise.ar, c, 1.2) * 2.0;                       // colored noise burst
			sig = (ping * pingw) + (burst * burstw) + (Ringz.ar(Impulse.ar(0), c, 0.004) * 0.6 * tickw);
			sig = HPF.ar(sig * env, 300);
			sig = sig + (Impulse.ar(0) * 0.55 * tickw);                        // raw one-sample impulse bypasses the env window
			sig = sig + (Ringz.ar(Impulse.ar(0), (c * 0.125).clip(200, 2000), 0.012) * 0.05 * env); // faint sub-partial so ultrasonic reads on small speakers
			Out.ar(out, Pan2.ar(sig * amp, pan));
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
			c = p2.clip(40, 18000);
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(sig, c, 0.55), BPF.ar(sig, c, 0.5), RHPF.ar(sig, c, 0.55) ]);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// kick — deep sine kick; drive-harmonics removed post-tanh so it stays deep.
		//   p1 = punch 0..1 (sweep depth + click + drive together), p2 = pitch-decay seconds
		SynthDef(\kick, { arg out, freq=60, amp=0.34, atk=0, rel=0.3, curve= -4, pan=0, p1=0.3, p2=0.06, p3=0;
			var env, fenv, body, clk, sig;
			env = EnvGen.ar(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			fenv = EnvGen.ar(Env([freq * (1.5 + (p1 * 4.5)), freq], [p2.clip(0.005, 0.3)], \exp));
			body = SinOsc.ar(fenv);
			body = LPF.ar((body * (1.2 + (p1 * 1.8))).tanh, (freq * 5).clip(200, 500)); // post-tanh LPF tracks pitch: drive can't brighten it
			clk = HPF.ar(WhiteNoise.ar * EnvGen.ar(Env.perc(0.0001, 0.004)), 3000) * p1 * 0.5;
			sig = body + clk;
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// additive — 8 partials, inharmonic stretch + rolloff + count.
		//   p1 = dissonance (inharmonicity), p2 = partial count (1..8), p3 = rolloff
		// additive/FM chord (Fell): four stacked FM voices at a chord voicing, each with
		// an IRRATIONAL modulator ratio so the sidebands are inharmonic. X = FM index,
		// walking a consonant sine chord (index 0) into dense metallic clang. Dissonance
		// is real inharmonic partials, not a stretched harmonic series.
		//   env is a gate/window (Env.linen): rel = flat sustain length (drone).
		//   p1 = FM index (0..8), p2 = brightness tilt (upper-voice level)
		SynthDef(\additive, { arg out, freq=220, amp=0.22, atk=0.01, rel=0.6, curve= -4, pan=0, p1=0, p2=0.5, p3=0;
			var sig, env, voices;
			var ratios = [1, 1.5, 2.0, 3.0];        // chord: root, fifth, octave, twelfth
			var mods   = [1.41, 1.73, 2.76, 3.16];  // irrational modulator ratios -> inharmonic
			env = EnvGen.ar(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			voices = Array.fill(4, { arg i;
				var c = freq * ratios[i];
				var lvl = (1 / (i + 1)) * (1 + (p2 * i * 0.6));   // Z lifts the upper voices
				PMOsc.ar(c, c * mods[i], p1.clip(0, 8)) * lvl;
			});
			sig = Mix.new(voices) * 0.35;
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
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
