-- render_params.lua (luajit) — emit render params per voice using the REAL resolver.
-- Loads lib/voices.lua so the harness renders exactly what the instrument plays.
--   usage: luajit render_params.lua <repo_root> [points]
-- points = semicolon-separated "x,y,z" macro coordinates (default one preset).
-- Emits one JSON entry per (voice, point); "point" is a compact label.
local root = assert(arg[1], "need repo root")
local Voices = dofile(root .. "/lib/voices.lua")

local function note_hz(n) return 440 * 2 ^ ((n - 69) / 12) end

local PITCH = { click1 = 72, click2 = 60, beep = 57, additive = 45, noise = 60, kick = 31, bass = 33 }
local ORDER = { "click1", "click2", "beep", "additive", "noise", "kick", "bass" }

local points = {}
for spec in string.gmatch(arg[2] or "0.6,0.6,0.5", "[^;]+") do
  local x, y, z = string.match(spec, "([%d%.]+),([%d%.]+),([%d%.]+)")
  points[#points + 1] = { x = tonumber(x), y = tonumber(y), z = tonumber(z) }
end

local parts = {}
for _, name in ipairs(ORDER) do
  for _, m in ipairs(points) do
    local p = Voices.resolve(name, m)
    local label = string.format("x%.1fy%.1fz%.1f", m.x, m.y, m.z)
    parts[#parts + 1] = string.format(
      '{"voice":"%s","point":"%s","freq":%.3f,"amp":%.4f,"atk":%.5f,"rel":%.4f,"curve":%.3f,"p1":%.5f,"p2":%.5f,"p3":%.5f}',
      name, label, note_hz(PITCH[name] or 57), p.amp, p.atk, p.rel, p.curve, p.p1 or 0, p.p2 or 0, p.p3 or 0)
  end
end
print("[" .. table.concat(parts, ",") .. "]")
