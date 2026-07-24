-- lib/gridui.lua — grid rendering + input for the voice page. [beep:grid-voice-page]
--
-- Edits the CURRENT voice, reached through an accessor (vfn) so voice-switching just
-- changes what every region shows. Regions (see docs/grid-layout.md):
--   rows 1-4 cols 1-8   step block — or the pitch keyboard (lib/keyboard) while held
--   rows 1-4 cols 9-16  per-voice micro faders (lib/fader): vol / pan / prob
--   rows 5-7            X / Y / Z macro strips (lib/strip)
--   row  8             transport (play/reset) + voice-select (cols 3+) ; octave while held
--
-- Modal law: holding a step retargets every region to that step's plock.

local Strip    = include('beepstreet/lib/strip')
local Fader    = include('beepstreet/lib/fader')
local Keyboard = include('beepstreet/lib/keyboard')

local GridUI = {}

local STEP_COLS, STEP_ROWS = 8, 4
local STRIP_ROW = { x = 5, y = 6, z = 7 }
local AXES = { 'x', 'y', 'z' }
local GLOBAL_ROW = 8
local VSEL_COL0 = 3            -- first voice-select key on row 8
local COPY_COL, PASTE_COL = 15, 16   -- while a step is held: copy / paste on the global bar

local QUAD = {
  { col = 9,  key = 'vol',  mode = 'uni', step = true },
  { col = 10, key = 'pan',  mode = 'bi',  step = true },
  { col = 11, key = 'prob', mode = 'uni', step = true },
}

local g, seq_ref, vfn, set_cur_cb, NV
local cur_local = 1
local held_step = nil
local plock_touched = false
local press_time = 0

local HOLD_ZERO_S = 0.4
local KB_HOLD_S = 0.28
local hold_id = 0
local zero_pending = {}
local kb_active = false
local kb_scale, kb_base = nil, nil
local clipboard = nil            -- copied step data (false = off, or a plock table)

-- current-voice accessors
local function P() return vfn().pattern end
local function M() return vfn().macro end
local function VV() return vfn() end

local function ncols() return (g and g.cols and g.cols > 0) and g.cols or 16 end
local function step_of(x, y) return (y - 1) * STEP_COLS + x end
local function step_xy(s) return ((s - 1) % STEP_COLS) + 1, math.floor((s - 1) / STEP_COLS) + 1 end

function GridUI.init(grid_dev, seq, vget, setcur, nvoices)
  g, seq_ref, vfn, set_cur_cb, NV = grid_dev, seq, vget, setcur, nvoices
end

function GridUI.held() return held_step end

-- ── value routing: held step's plock, else current voice ──────────────────────
function GridUI.axis_value(ax)
  if held_step then
    local st = P()[held_step]
    if st and st[ax] ~= nil then return st[ax] end
  end
  return M()[ax]
end

local function set_axis(ax, v)
  if held_step then
    local st = P()[held_step]
    if not st then st = {}; P()[held_step] = st end
    st[ax] = v; plock_touched = true
  else M()[ax] = v end
end

local function quad_value(p)
  if held_step and p.step then
    local st = P()[held_step]
    if st and st[p.key] ~= nil then return st[p.key] end
  end
  return VV()[p.key]
end

local function set_quad(p, v)
  if held_step and p.step then
    local st = P()[held_step]
    if not st then st = {}; P()[held_step] = st end
    st[p.key] = v; plock_touched = true
  else VV()[p.key] = v end
end

local function held_pitch()
  local st = P()[held_step]
  return (st and st.pitch) or VV().pitch
end

local function set_pitch(note)
  local st = P()[held_step]
  if not st then st = {}; P()[held_step] = st end
  st.pitch = note; plock_touched = true
end

local function clear_hold()
  held_step, plock_touched = nil, false
  kb_active, kb_scale, kb_base = false, nil, nil
end

-- deep-ish copy of a step (false = off, else a flat plock table)
local function copy_step(st)
  if type(st) ~= 'table' then return false end
  local c = {}; for k, v in pairs(st) do c[k] = v end
  return c
end

-- ── input ─────────────────────────────────────────────────────────────────────
function GridUI.key(x, y, z)
  local n = ncols()
  -- step block (or keyboard while held)
  if x >= 1 and x <= STEP_COLS and y >= 1 and y <= STEP_ROWS then
    if z == 1 then
      if held_step == nil then
        held_step, plock_touched, press_time = step_of(x, y), false, util.time()
        local s = held_step
        clock.run(function()
          clock.sleep(KB_HOLD_S)
          if held_step == s then
            kb_scale = Keyboard.build(VV().root, VV().scale)
            kb_base = Keyboard.center_base(kb_scale, held_pitch())
            kb_active = true; GridUI.redraw()
          end
        end)
      elseif kb_active then
        local note = Keyboard.note_at(kb_scale, kb_base, x, y)
        if note then set_pitch(note) end
      end
    else
      if step_of(x, y) == held_step then
        -- toggle only on a genuine quick tap; a deliberate hold never toggles
        if not plock_touched and (util.time() - press_time) < KB_HOLD_S then
          local s = held_step
          if P()[s] then P()[s] = false else P()[s] = {} end
        end
        clear_hold()
      end
    end
    GridUI.redraw(); return true
  end
  -- micro faders
  for _, p in ipairs(QUAD) do
    if z == 1 and x == p.col and y >= 1 and y <= STEP_ROWS then
      local v = quad_value(p)
      set_quad(p, (p.mode == 'bi') and Fader.bi_tap(v, y) or Fader.uni_tap(v, y))
      GridUI.redraw(); return true
    end
  end
  -- strips
  for _, ax in ipairs(AXES) do
    if y == STRIP_ROW[ax] and x >= 1 and x <= n then
      if x == 1 then
        if z == 1 then
          hold_id = hold_id + 1
          local tok = hold_id
          zero_pending[ax] = tok
          clock.run(function()
            clock.sleep(HOLD_ZERO_S)
            if zero_pending[ax] == tok then
              zero_pending[ax] = nil; set_axis(ax, 0); GridUI.redraw()
            end
          end)
        elseif zero_pending[ax] then
          zero_pending[ax] = nil
          set_axis(ax, Strip.tap(GridUI.axis_value(ax), n, 1))
        end
      elseif z == 1 then
        set_axis(ax, Strip.tap(GridUI.axis_value(ax), n, x))
      end
      GridUI.redraw(); return true
    end
  end
  -- global row
  if z == 1 and y == GLOBAL_ROW then
    if held_step and x == COPY_COL then
      clipboard = copy_step(P()[held_step]); plock_touched = true
    elseif held_step and x == PASTE_COL and clipboard ~= nil then
      P()[held_step] = copy_step(clipboard); plock_touched = true
    elseif kb_active then
      local po, mb = Keyboard.per_octave(kb_scale), Keyboard.max_base(kb_scale)
      if x == 1 then kb_base = util.clamp(kb_base - po, 1, mb)
      elseif x == 2 then kb_base = util.clamp(kb_base + po, 1, mb) end
    elseif x == 1 then seq_ref:toggle()
    elseif x == 2 then seq_ref:reset()
    elseif x >= VSEL_COL0 and x <= VSEL_COL0 + NV - 1 then
      cur_local = x - VSEL_COL0 + 1
      set_cur_cb(cur_local)
      clear_hold()
    end
    GridUI.redraw(); return true
  end
  return false
end

-- ── render ──────────────────────────────────────────────────────────────────
function GridUI.redraw()
  if not g then return end
  local n = ncols()
  g:all(0)
  -- step block or keyboard
  if kb_active then
    Keyboard.render(g, kb_scale, kb_base, held_pitch(), VV().root % 12)
    local hx, hy = step_xy(held_step)
    g:led(hx, hy, 15)
  else
    local pat, pos = P(), (seq_ref and seq_ref.pos or 0)
    for yy = 1, STEP_ROWS do
      for xx = 1, STEP_COLS do
        local s = step_of(xx, yy)
        local lvl = pat[s] and 12 or 2
        if s == held_step then lvl = 15 end
        if s == pos then lvl = 15 end
        g:led(xx, yy, lvl)
      end
    end
  end
  -- micro faders
  for _, p in ipairs(QUAD) do
    if p.col <= n then
      if p.mode == 'bi' then Fader.bi_render(g, p.col, quad_value(p))
      else Fader.uni_render(g, p.col, quad_value(p)) end
    end
  end
  -- strips
  for _, ax in ipairs(AXES) do Strip.render(g, STRIP_ROW[ax], n, GridUI.axis_value(ax)) end
  -- global row
  if kb_active then
    g:led(1, GLOBAL_ROW, 8); g:led(2, GLOBAL_ROW, 8)          -- octave -/+
  else
    g:led(1, GLOBAL_ROW, (seq_ref and seq_ref:is_running()) and 15 or 4)
    g:led(2, GLOBAL_ROW, 4)
    for i = 1, NV do
      local col = VSEL_COL0 + i - 1
      if col <= n then g:led(col, GLOBAL_ROW, i == cur_local and 12 or 4) end
    end
  end
  -- copy/paste while a step is held (works alongside octave or transport)
  if held_step and COPY_COL <= n then
    g:led(COPY_COL, GLOBAL_ROW, 8)
    g:led(PASTE_COL, GLOBAL_ROW, (clipboard ~= nil) and 12 or 3)
  end
  g:refresh()
end

return GridUI
