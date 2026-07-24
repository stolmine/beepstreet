-- render_params.lua (luajit) — emit render params per voice using the REAL resolver.
-- Loads lib/voices.lua so the harness renders exactly what the instrument plays.
--   usage: luajit render_params.lua <repo_root>
local root = assert(arg[1], "need repo root")
local Voices = dofile(root .. "/lib/voices.lua")

local function note_hz(n) return 440 * 2 ^ ((n - 69) / 12) end

-- preset macro coordinate to audition each voice at, and a representative pitch
local PRESET = { x = 0.60, y = 0.60, z = 0.50 }
local PITCH = { click1 = 72, click2 = 60, beep = 57, additive = 45, noise = 60, kick = 31, bass = 33 }
local ORDER = { "click1", "click2", "beep", "additive", "noise", "kick", "bass" }

local parts = {}
for _, name in ipairs(ORDER) do
  local p = Voices.resolve(name, PRESET)
  parts[#parts + 1] = string.format(
    '{"voice":"%s","freq":%.3f,"amp":%.4f,"atk":%.5f,"rel":%.4f,"curve":%.3f,"p1":%.5f,"p2":%.5f,"p3":%.5f}',
    name, note_hz(PITCH[name] or 57), p.amp, p.atk, p.rel, p.curve, p.p1 or 0, p.p2 or 0, p.p3 or 0)
end
print("[" .. table.concat(parts, ",") .. "]")
