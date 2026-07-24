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

		// beep — two detuned sines + a touch of cross-FM (Ikeda-bass DNA).
		//   p1 = detune (cents), p2 = fmIndex
		SynthDef(\beep, { arg out, freq=440, amp=0.3, atk=0.001, rel=0.2, curve= -4, pan=0, p1=0, p2=0, p3=0;
			var s1, s2, sig, env, f2;
			f2 = freq * (2 ** (p1 / 1200));
			s2 = SinOsc.ar(f2);
			s1 = SinOsc.ar(freq + (s2 * p2 * freq));
			sig = (s1 + s2) * 0.5;
			env = EnvGen.kr(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// bass — clean sine fundamental + sub octave + optional saw harmonics, GATED.
		//   p1 = cutoff/brightness (Hz), p2 = sub level (0..1), p3 = detune (cents)
		//   env is a gate/window (Env.linen): rel = flat sustain length, shaped by Y.
		SynthDef(\bass, { arg out, freq=110, amp=0.34, atk=0.01, rel=0.5, curve= -4, pan=0, p1=800, p2=0.5, p3=0;
			var fund, sub, harm, sig, env;
			fund = SinOsc.ar(freq);
			sub  = SinOsc.ar(freq * 0.5) * p2.clip(0, 1);
			harm = RLPF.ar(Saw.ar(freq) + Saw.ar(freq * (2 ** (p3 / 1200))), p1.clip(50, 16000), 0.5) * 0.3;
			sig  = (fund * 0.5) + (sub * 0.8) + harm;
			sig  = (sig * 0.7).tanh;
			env  = EnvGen.kr(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// click1 — bright/ultrasonic: impulse<->noise excitation into a Ringz, HPF'd.
		//   p1 = noise amount (tonal->noise), p2 = ring/center freq
		SynthDef(\click1, { arg out, freq=440, amp=0.3, atk=0, rel=0.01, curve= -4, pan=0, p1=0, p2=6000, p3=0;
			var exc, sig, env;
			env = EnvGen.kr(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			exc = (Impulse.ar(0) * (1 - p1)) + (WhiteNoise.ar * p1 * env);
			sig = Ringz.ar(exc, p2.clip(200, 18000), rel * 0.6);
			sig = HPF.ar(sig, 500);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// click2 — woody/dry: a resonant band-passed click, lower + more body.
		//   p1 = ring decay (damping->resonant), p2 = center freq
		SynthDef(\click2, { arg out, freq=440, amp=0.3, atk=0, rel=0.03, curve= -4, pan=0, p1=0.05, p2=1500, p3=0;
			var sig, env, c;
			env = EnvGen.kr(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			c = p2.clip(200, 8000);
			sig = Ringz.ar(Impulse.ar(0), c, p1.clip(0.005, 0.3));
			sig = BPF.ar(sig, c, 0.6);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// noise — white<->brown source through an LP/BP/HP morph.
		//   p1 = colour (white->brown), p2 = filter center, p3 = filter morph (0 LP..1 HP)
		SynthDef(\noise, { arg out, freq=440, amp=0.28, atk=0.005, rel=0.2, curve= -4, pan=0, p1=0, p2=4000, p3=0;
			var src, sig, env, c;
			env = EnvGen.kr(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			src = (WhiteNoise.ar * (1 - p1)) + (BrownNoise.ar * p1);
			c = p2.clip(40, 18000);
			sig = SelectX.ar(p3.clip(0, 1) * 2, [ RLPF.ar(src, c, 0.4), BPF.ar(src, c, 0.5), RHPF.ar(src, c, 0.4) ]);
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// kick — pitch-enveloped sine body + a noise click, soft-driven.
		//   p1 = click/drive, p2 = pitch-decay (tone)
		SynthDef(\kick, { arg out, freq=60, amp=0.34, atk=0, rel=0.3, curve= -4, pan=0, p1=0.3, p2=0.06, p3=0;
			var body, clk, sig, env, fenv;
			env = EnvGen.kr(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			fenv = EnvGen.kr(Env([freq * 6, freq], [p2.clip(0.005, 0.4)], \exp));
			body = SinOsc.ar(fenv);
			clk = HPF.ar(WhiteNoise.ar * EnvGen.kr(Env.perc(0, 0.006)), 1200) * p1;
			sig = ((body + clk) * (1 + (p1 * 3))).tanh;
			Out.ar(out, Pan2.ar(sig * env * amp, pan));
		}).add;

		// additive — 8 partials, inharmonic stretch + rolloff + count.
		//   p1 = dissonance (inharmonicity), p2 = partial count (1..8), p3 = rolloff
		//   env is a gate/window (Env.linen): rel = flat sustain length (drone), shaped by Y.
		SynthDef(\additive, { arg out, freq=220, amp=0.24, atk=0.01, rel=0.6, curve= -4, pan=0, p1=0, p2=8, p3=1;
			var sig, env, n = 8, partials;
			env = EnvGen.kr(Env.linen(atk, rel, 0.02, 1, curve), doneAction: Done.freeSelf);
			partials = Array.fill(n, { arg i;
				var k = i + 1;
				var ratio = k * (1 + (p1 * (k - 1) * 0.06));
				var gate = (p2 >= k);
				var a = gate * (1 / (k ** p3));
				SinOsc.ar(freq * ratio) * a;
			});
			sig = Mix.new(partials) * (2 / n);
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
