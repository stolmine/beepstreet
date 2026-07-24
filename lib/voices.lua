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

local function clamp01(v) return math.max(0, math.min(1, v)) end

local resolvers = {
  -- sine ping (Ikeda): X = pure -> detune-beat -> clangy partner tone, Z = cross-FM grit
  beep = function(m)
    local s = shape(m.y, 0.02, 1.20)
    local partner = clamp01((m.x - 0.45) / 0.55)      -- partner tone arrives past mid-X
    return { amp = 0.30, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = 30 * m.x ^ 1.5,                     -- detune cents: slow onset, beats by mid-X
             p2 = 1.8 * m.z ^ 1.7,                    -- FM grit: clean low register, edge arrives late
             p3 = partner ^ 1.2 }
  end,
  -- clinical sub bass, gated: pure sine + integer-ratio FM (no filter), Z = sub level
  bass = function(m)
    local s = shape(m.y, 0.03, 3.00)   -- rel = gate/window length (drone, not pluck)
    return { amp = 0.34, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = lerp(0, 3, m.x),  -- FM index (subtle harmonic edge; keep sub-dominant)
             p2 = lerp(0, 1, m.z),   -- sub-octave level
             p3 = 0 }
  end,
  -- bright ultrasonic click (Ikeda): X = tick -> pitched ping -> noise burst (morph),
  -- Z = center 2-12k. Y (rel) is both window and ping ring length.
  click1 = function(m)
    local s = shape(m.y, 0.004, 0.06)
    return { amp = 0.40, atk = 0.0, rel = s.rel, curve = s.curve,
             p1 = m.x, p2 = xlerp(2000, 12000, m.z), p3 = 0 }
  end,
  -- woody/dry modal click (SND): X = dead thud -> woodblock ring, Z = center 400-4k
  click2 = function(m)
    local s = shape(m.y, 0.003, 0.12)
    return { amp = 0.30, atk = 0.0, rel = s.rel, curve = s.curve,
             p1 = m.x, p2 = xlerp(400, 4000, m.z), p3 = 0 }
  end,
  -- noise (Rauschen model morph): X = white -> crush -> crackle -> velvet -> particle
  -- (tent-weight stations), Z = center + LP/BP/HP morph. Texture rates track pitch.
  noise = function(m)
    local s = shape(m.y, 0.005, 1.00)
    return { amp = 0.28, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = m.x * 4, p2 = xlerp(200, 12000, m.z), p3 = m.z }
  end,
  -- kick (Ikeda bare stab -> Tessera clang): X = modal lattice/fold (pure 40Hz-style
  -- sub stab -> compound inharmonic clang), Z = pitch-decay. Y = body length (the
  -- Ikeda reference stab is ~350ms: y ~0.7).
  kick = function(m)
    local s = shape(m.y, 0.04, 0.90)
    return { amp = 0.34, atk = 0.001, rel = s.rel, curve = s.curve,
             p1 = m.x, p2 = xlerp(0.008, 0.12, m.z), p3 = 0 }
  end,
  -- additive chord engine (Plaits-style): X = continuous chord structure
  -- (OCT-P5-sus4-m-m7-m9-m11, ratio-interpolated glide), Z = brightness rolloff +
  -- voicing rotation + FM sidebands + stereo width (dark close chord -> bright
  -- rotated rough). Micro-detune/drift quaver is built into the SynthDef.
  additive = function(m)
    local s = shape(m.y, 0.02, 3.00)   -- rel = gate/window length (drone, not pluck)
    return { amp = 0.26, atk = s.atk, rel = s.rel, curve = s.curve,
             p1 = 6 * m.x,             -- chord position (0..6 across the 7-chord table)
             p2 = m.z ^ 0.8,           -- brightness arrives early in Z
             p3 = m.z ^ 1.4 }          -- rotation/FM/width arrive later in Z
  end,
}

function Voices.resolve(name, macro)
  return (resolvers[name] or resolvers.beep)(macro)
end

return Voices
