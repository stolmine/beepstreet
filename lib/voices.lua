-- lib/voices.lua — voice definitions + X/Y/Z macro -> engine-param mappings.
--
-- Lua-forward architecture (see docs/macro-model.md): each voice maps a normalized
-- macro coordinate {x,y,z} in 0..1 to a table of RESOLVED engine params. The curves
-- here ARE the "trajectory through parameter space" — edit them and reload instantly
-- (no SC recompile). The engine stays dumb: it exposes raw params + triggers only.
--
-- SCAFFOLD: one voice (beep), deliberately simple map along the uniform axes
--   X = complexity/timbre (weight -> grit: detune + a touch of cross-FM)
--   Y = duration/shape     (steep-log -> smooth: attack/release/curve)
--   Z = register/motion     (placeholder register sweep; real pitch comes from grid)

local Voices = {}

local function lerp(a, b, t) return a + (b - a) * t end
local function xlerp(a, b, t) return a * (b / a) ^ t end   -- exponential (times/freqs)

-- per-voice resolvers: (macro) -> engine param table
local resolvers = {
  beep = function(m)
    return {
      -- X: weight -> grit
      detune  = lerp(0, 18, m.x),        -- cents of osc2 detune
      fmIndex = lerp(0, 0.30, m.x),      -- touch of cross-FM
      -- Y: duration / shape (steep-log defined -> smooth)
      atk     = xlerp(0.0005, 0.05, m.y),-- 0.5 ms -> 50 ms
      rel     = xlerp(0.02, 1.20, m.y),  -- short -> long
      curve   = lerp(-8, 0, m.y),        -- steep log -> linear/smooth
      -- Z: register (real discrete pitch will replace this via the grid)
      freq    = xlerp(55, 220, m.z),
      -- fixed for now
      amp     = 0.30,
      pan     = 0,
    }
  end,
}

function Voices.resolve(name, macro)
  local r = resolvers[name] or resolvers.beep
  return r(macro)
end

return Voices
