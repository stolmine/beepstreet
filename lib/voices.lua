-- lib/voices.lua — voice definitions + X/Y/Z macro -> engine-param mappings.
--
-- Lua-forward (docs/macro-model.md): each resolver maps a macro coordinate {x,y,z}
-- in 0..1 to concrete engine params. The engine stays a dumb DSP host — every voice
-- command shares the signature (freq, amp, atk, rel, curve, pan, p1, p2, p3); freq
-- comes from pitch and vol/pan from the mix (added by the host), so a resolver only
-- returns { amp, atk, rel, curve, p1, p2, p3 }. p1/p2/p3 are the type-specific
-- scalars each SynthDef interprets. Targets: docs/sonic-targets.md.
--
-- Axis convention: X = complexity/timbre, Y = duration/shape, Z = register/motion.

local Voices = {}

local function lerp(a, b, t) return a + (b - a) * t end
local function xlerp(a, b, t) return a * (b / a) ^ t end
-- Y -> envelope shape (steep-log defined -> smooth), rel range per voice
local function shape(y, rlo, rhi)
  return { atk = xlerp(0.0005, 0.05, y), rel = xlerp(rlo, rhi, y), curve = lerp(-8, 0, y) }
end

local resolvers = {
  -- sine ping / Ikeda-bass DNA: X = detune width, Z = cross-FM grit
  beep = function(m)
    local s = shape(m.y, 0.02, 1.20)
    return { amp = 0.30, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = lerp(0, 18, m.x), p2 = lerp(0, 0.30, m.z), p3 = 0 }
  end,
  -- clinical sub bass, gated: pure sine + integer-ratio FM (no filter), Z = sub level
  bass = function(m)
    local s = shape(m.y, 0.03, 3.00)   -- rel = gate/window length (drone, not pluck)
    return { amp = 0.34, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = lerp(0, 3, m.x),  -- FM index (subtle harmonic edge; keep sub-dominant)
             p2 = lerp(0, 1, m.z),   -- sub-octave level
             p3 = 0 }
  end,
  -- bright ultrasonic click (Ikeda): X = tonal->noise, Z = ring/center 2-10k
  click1 = function(m)
    local s = shape(m.y, 0.0008, 0.05)
    return { amp = 0.30, atk = 0.0, rel = s.rel, curve = s.curve,
             p1 = m.x, p2 = xlerp(2000, 10000, m.z), p3 = 0 }
  end,
  -- woody/dry click (SND): X = damping->resonance (ring decay), Z = center 400-4k
  click2 = function(m)
    local s = shape(m.y, 0.002, 0.08)
    return { amp = 0.30, atk = 0.0, rel = s.rel, curve = s.curve,
             p1 = xlerp(0.01, 0.20, m.x), p2 = xlerp(400, 4000, m.z), p3 = 0 }
  end,
  -- filterable noise: X = colour white->brown, Z = center + LP/BP/HP morph
  noise = function(m)
    local s = shape(m.y, 0.005, 1.00)
    return { amp = 0.28, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = m.x, p2 = xlerp(200, 12000, m.z), p3 = m.z }
  end,
  -- kick: X = click/drive, Z = pitch-decay (tone)
  kick = function(m)
    local s = shape(m.y, 0.05, 0.60)
    return { amp = 0.34, atk = 0.0, rel = s.rel, curve = s.curve,
             p1 = lerp(0, 1, m.x), p2 = xlerp(0.02, 0.20, m.z), p3 = 0 }
  end,
  -- additive/FM chord drone (Fell): stacked FM voices, irrational modulator ratios.
  -- X = FM index (consonant sine chord -> dense inharmonic clang), Z = brightness tilt
  additive = function(m)
    local s = shape(m.y, 0.02, 3.00)   -- rel = gate/window length (drone, not pluck)
    return { amp = 0.22, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = lerp(0, 8, m.x),  -- FM index
             p2 = m.z,               -- brightness tilt (upper-voice level)
             p3 = 0 }
  end,
}

function Voices.resolve(name, macro)
  return (resolvers[name] or resolvers.beep)(macro)
end

return Voices
