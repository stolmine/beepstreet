-- lib/keyboard.lua — scale-quantized pitch keyboard over the step block. [beep:pitch-select-grid]
--
-- While a step is held, the 4x8 step block becomes a keyboard: 32 pads map to a
-- 32-note window into a per-voice scale (musicutil). Bottom-left = lowest, top-right
-- = highest (scale-degrees per row). Octave -/+ shifts the window; the window can
-- auto-center on a step's existing note. Pitch is stored ABSOLUTE (MIDI note) on the
-- step; the held pad is the anchor (drawn by gridui).

local musicutil = require 'musicutil'

local Keyboard = {}
local COLS, ROWS = 8, 4
local WIN = COLS * ROWS   -- 32

local function clamp(x, lo, hi) if x < lo then return lo elseif x > hi then return hi else return x end end

function Keyboard.build(root, scale_type)
  return musicutil.generate_scale(root, scale_type, 8) or {}
end

function Keyboard.per_octave(scale)
  if #scale == 0 then return 7 end
  local n, first = 0, scale[1]
  for _, v in ipairs(scale) do
    if v < first + 12 then n = n + 1 else break end
  end
  return math.max(n, 1)
end

function Keyboard.max_base(scale) return math.max(1, #scale - WIN + 1) end

-- window base that centers `note` (falls back to the middle of the scale)
function Keyboard.center_base(scale, note)
  local idx
  if note then for i, v in ipairs(scale) do if v == note then idx = i break end end end
  idx = idx or math.floor(#scale / 2)
  return clamp(idx - math.floor(WIN / 2), 1, Keyboard.max_base(scale))
end

-- pad (x,y) 1-based -> window position 1..32 (bottom-left low, top-right high)
local function pos_of(x, y) return (ROWS - y) * COLS + x end

function Keyboard.note_at(scale, base, x, y)
  return scale[base + pos_of(x, y) - 1]
end

-- draw the keyboard onto the step block; gridui overlays the held anchor after
function Keyboard.render(g, scale, base, sel_note, root_pc)
  for y = 1, ROWS do
    for x = 1, COLS do
      local note = scale[base + pos_of(x, y) - 1]
      local lvl = 0
      if note then
        if note == sel_note then lvl = 15
        elseif (note % 12) == root_pc then lvl = 8    -- octave landmarks
        else lvl = 3 end
      end
      g:led(x, y, lvl)
    end
  end
end

return Keyboard
