-- lib/gridui.lua — grid rendering + input for the step page. [beep:step-sequencer]
--
-- 4x8 step block (the brief's pattern grid): rows = beats (1-4), cols = the eight
-- 32nds within a beat (1-8). step index = (row-1)*8 + col, 1..32 = one bar.
-- Brightness convention (from gladiola): empty=dim, on=med, playhead=full; the
-- 16th columns (odd) are a touch brighter than the in-between 32nds for meter.
--
-- Event-driven redraw (on step-advance + on key), not a free-running refresh loop:
-- the sequencer's on_step moves the playhead, a key toggles a step.

local GridUI = {}

local COLS, ROWS = 8, 4
local L = { off = 0, dim = 3, dim16 = 4, on = 8, play = 15 }

local g, pattern, seq_ref

function GridUI.init(grid_dev, pat, seq)
  g, pattern, seq_ref = grid_dev, pat, seq
end

local function step_of(x, y) return (y - 1) * COLS + x end

-- returns true if the press was inside the step block (consumed)
function GridUI.key(x, y, z)
  if z ~= 1 then return false end
  if x >= 1 and x <= COLS and y >= 1 and y <= ROWS then
    local s = step_of(x, y)
    -- toggle on/off (NOT `a and false or b` — that always yields b in Lua)
    if pattern[s] then pattern[s] = false else pattern[s] = {} end  -- {} = on (room for plocks)
    GridUI.redraw()
    return true
  end
  return false
end

function GridUI.redraw()
  if not g then return end
  g:all(0)
  local pos = seq_ref and seq_ref.pos or 0
  for y = 1, ROWS do
    for x = 1, COLS do
      local s = step_of(x, y)
      local lvl
      if pattern[s] then lvl = L.on
      elseif (x % 2 == 1) then lvl = L.dim16       -- 16th columns slightly brighter
      else lvl = L.dim end
      if s == pos then lvl = L.play end            -- playhead wins
      g:led(x, y, lvl)
    end
  end
  g:refresh()
end

return GridUI
