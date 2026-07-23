// lib/Engine_Beepstreet.sc — norns (Crone) engine for beepstreet.
//
// SCAFFOLD: one test voice (\beep) — two sines with slight detune + a touch of
// cross-FM, one-shot VCA with a variable-curve envelope. This is the seed of the
// Ikeda-bass recipe (docs/macro-model.md). The engine is intentionally "dumb": it
// exposes RAW params and a trigger; all X/Y/Z macro mapping happens Lua-side
// (lib/voices.lua). Per-note synths spawn into voiceGroup and free themselves.

Engine_Beepstreet : CroneEngine {
	var voiceGroup;

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	alloc {
		voiceGroup = Group.new(context.xg);

		SynthDef(\beep, {
			arg out, freq = 440, amp = 0.3, atk = 0.001, rel = 0.2, curve = -4,
			    pan = 0, detune = 0, fmIndex = 0;
			var s1, s2, sig, env, f2;
			f2 = freq * (2 ** (detune / 1200));           // osc2 detuned by `detune` cents
			s2 = SinOsc.ar(f2);
			s1 = SinOsc.ar(freq + (s2 * fmIndex * freq)); // a touch of cross-FM
			sig = (s1 + s2) * 0.5;
			env = EnvGen.kr(Env.perc(atk, rel, 1, curve), doneAction: Done.freeSelf);
			sig = sig * env * amp;
			Out.ar(out, Pan2.ar(sig, pan));
		}).add;

		context.server.sync;

		// trig: freq amp atk rel curve pan detune fmIndex
		this.addCommand("trig", "ffffffff", { arg msg;
			Synth(\beep, [
				\out, context.out_b,
				\freq, msg[1], \amp, msg[2], \atk, msg[3], \rel, msg[4],
				\curve, msg[5], \pan, msg[6], \detune, msg[7], \fmIndex, msg[8]
			], voiceGroup);
		});
	}

	free {
		voiceGroup.free;
	}
}
