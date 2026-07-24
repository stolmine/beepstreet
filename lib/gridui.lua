-- lib/gridui.lua — grid rendering + input for the voice page. [beep:param-lock]
--
-- Layout (rows top→bottom on a 16-wide grid):
--   rows 1-4  4x8 step block (rows=beats, cols=8 thirty-seconds/beat) = 32 steps
--   row  6    X strip   } three param-strips spanning the grid width; each encodes
--   row  7    Y strip   } a macro axis via lib/strip (coarse tap + fine re-tap).
--   row  8    Z strip   }
--
-- Hold-to-plock: hold a step pad and edit a strip → that edit locks to the step
-- (and turns it on). No step held → the strip edits the GLOBAL macro. A step press
-- with no strip touch toggles the step on release (tap). Steps carry their plocks
-- as st.x / st.y / st.z; the sequencer falls back to the global macro per-axis.

local Strip = include('beepstreet/lib/strip')

local GridUI = {}

local STEP_COLS, STEP_ROWS = 8, 4
local STRIP_ROW = { x = 6, y = 7, z = 8 }
local AXES = { 'x', 'y', 'z' }

local g, pattern, seq_ref, macro
local held_step = nil
local plock_touched = false

local HOLD_ZERO_S = 0.4          -- hold the lowest strip key this long -> value 0
local hold_id = 0                -- monotonic token so stale hold timers no-op
local zero_pending = {}          -- axis -> token of the in-flight hold

local function ncols() return (g and g.cols and g.cols > 0) and g.cols or 16 end
local function step_of(x, y) return (y - 1) * STEP_COLS + x end

function GridUI.init(grid_dev, pat, seq, macro_tbl)
  g, pattern, seq_ref, macro = grid_dev, pat, seq, macro_tbl
end

function GridUI.held() return held_step end

-- the value shown/edited for an axis: held step's plock, else the global macro
function GridUI.axis_value(ax)
  if held_step then
    local st = pattern[held_step]
    if st and st[ax] ~= nil then return st[ax] end
  end
  return macro[ax]
end

local function set_axis(ax, v)
  if held_step then
    local st = pattern[held_step]
    if not st then st = {}; pattern[held_step] = st end   -- plocking an off step turns it on
    st[ax] = v
    plock_touched = true
  else
    macro[ax] = v
  end
end

-- returns true if the press was consumed
function GridUI.key(x, y, z)
  local n = ncols()
  -- step block
  if x >= 1 and x <= STEP_COLS and y >= 1 and y <= STEP_ROWS then
    local s = step_of(x, y)
    if z == 1 then
      held_step, plock_touched = s, false
    else
      if held_step == s and not plock_touched then         -- tap → toggle
        if pattern[s] then pattern[s] = false else pattern[s] = {} end
      end
      held_step, plock_touched = nil, false
    end
    GridUI.redraw()
    return true
  end
  -- strips
  for _, ax in ipairs(AXES) do
    if y == STRIP_ROW[ax] and x >= 1 and x <= n then
      if x == 1 then
        -- lowest key: debounce tap vs hold. Defer the tap so a hold goes cleanly
        -- to zero without first flashing the coarse value (1/n ≈ 0.06).
        if z == 1 then
          hold_id = hold_id + 1
          local tok = hold_id
          zero_pending[ax] = tok
          clock.run(function()
            clock.sleep(HOLD_ZERO_S)
            if zero_pending[ax] == tok then
              zero_pending[ax] = nil
              set_axis(ax, 0)                    -- clean off, no intermediate
              GridUI.redraw()
            end
          end)
        else                                     -- release
          if zero_pending[ax] then               -- before threshold -> it was a tap
            zero_pending[ax] = nil
            set_axis(ax, Strip.tap(GridUI.axis_value(ax), n, 1))
          end                                    -- else: hold already fired zero
        end
      elseif z == 1 then
        set_axis(ax, Strip.tap(GridUI.axis_value(ax), n, x))
      end
      GridUI.redraw()
      return true
    end
  end
  return false
end

function GridUI.redraw()
  if not g then return end
  local n = ncols()
  g:all(0)
  local pos = seq_ref and seq_ref.pos or 0
  for yy = 1, STEP_ROWS do
    for xx = 1, STEP_COLS do
      local s = step_of(xx, yy)
      local lvl
      if pattern[s] then lvl = 8
      elseif (xx % 2 == 1) then lvl = 4 else lvl = 3 end
      if s == held_step then lvl = 12 end
      if s == pos then lvl = 15 end                         -- playhead wins
      g:led(xx, yy, lvl)
    end
  end
  for _, ax in ipairs(AXES) do
    Strip.render(g, STRIP_ROW[ax], n, GridUI.axis_value(ax))
  end
  g:refresh()
end

return GridUI
