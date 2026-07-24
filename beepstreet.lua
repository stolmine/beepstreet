-- beepstreet — norns + grid instrument for microsound / glitch composition.
--
-- Thin entry point (bootstrap only). Real logic lives in lib/*.lua and
-- lib/Engine_Beepstreet.sc. See docs/macro-model.md, docs/sonic-targets.md,
-- docs/grid-layout.md.
--
-- Multi-voice: seven independent voices sequence at once; the grid page edits the
-- current one (switch on the global row). All types trigger the \beep engine for
-- now — real per-type SynthDefs are the voice-* items.

engine.name = 'Beepstreet'

local musicutil = require 'musicutil'
local synth  = include('beepstreet/lib/voices')   -- macro -> engine-param resolvers
local Seq    = include('beepstreet/lib/seq')
local gridui = include('beepstreet/lib/gridui')

local g = grid.connect()

local NV = 7
local VTYPE = { 'click1', 'click2', 'beep', 'additive', 'noise', 'kick', 'bass' }
local VDEF_PITCH = { 60, 55, 48, 51, 43, 36, 31 }   -- spread so lines are distinguishable
local voices = {}
local cur = 1
local sel = 'x'                   -- which macro axis E3 edits
local seq
local screen_dirty = true

local AXES = { 'x', 'y', 'z' }
local YROW = { x = 34, y = 44, z = 54 }

local function new_voice(vtype, pitch)
  local v = { type = vtype, vol = 1.0, pan = 0.0, prob = 1.0,
              pitch = pitch, root = 24, scale = 'Natural Minor',
              macro = { x = 0.30, y = 0.30, z = 0.0 }, pattern = {} }
  for i = 1, 32 do v.pattern[i] = false end
  return v
end

local function V() return voices[cur] end

-- trigger one voice's step (st = step table, or nil for a manual hit)
local function trig_voice(v, st)
  local m = v.macro
  local vol, pan, pitch = v.vol, v.pan, v.pitch
  if type(st) == 'table' then
    local prob = st.prob or v.prob
    if prob < 1 and math.random() > prob then return end
    m = { x = st.x or v.macro.x, y = st.y or v.macro.y, z = st.z or v.macro.z }
    if st.vol ~= nil then vol = st.vol end
    if st.pan ~= nil then pan = st.pan end
    if st.pitch ~= nil then pitch = st.pitch end
  end
  local p = synth.resolve(v.type, m)
  local fn = engine[v.type]                          -- one command per voice type
  if fn then
    fn(musicutil.note_num_to_freq(pitch), p.amp * vol, p.atk, p.rel, p.curve, pan,
       p.p1 or 0, p.p2 or 0, p.p3 or 0)
  end
end

function init()
  for i = 1, NV do voices[i] = new_voice(VTYPE[i], VDEF_PITCH[i]) end

  seq = Seq.new{
    on_step = function(pos)
      for i = 1, NV do
        local st = voices[i].pattern[pos]
        if st then trig_voice(voices[i], st) end
      end
      screen_dirty = true
      gridui.redraw()
    end,
  }
  gridui.init(g, seq, V, function(i) cur = i end, NV)
  gridui.redraw()

  clock.run(function()
    while true do
      clock.sleep(1 / 15)
      if screen_dirty then redraw(); screen_dirty = false end
    end
  end)
  screen_dirty = true
end

function clock.transport.start() seq:start() end
function clock.transport.stop()  seq:stop() end
function clock.transport.reset() seq:reset() end

function key(n, z)
  if z == 1 then
    if n == 2 then seq:toggle()
    elseif n == 3 then trig_voice(V(), nil) end
    screen_dirty = true
  end
end

function enc(n, d)
  if n == 1 then
    params:delta('clock_tempo', d)
  elseif n == 2 then
    local i = 1
    for k, v in ipairs(AXES) do if v == sel then i = k end end
    sel = AXES[util.clamp(i + d, 1, #AXES)]
  elseif n == 3 then
    local v = V()
    v.macro[sel] = util.clamp(v.macro[sel] + d / 50, 0, 1)
  end
  screen_dirty = true
end

g.key = function(x, y, z)
  if gridui.key(x, y, z) then screen_dirty = true end
end

function redraw()
  screen.clear()
  screen.level(15); screen.move(4, 10); screen.text('beepstreet')
  screen.level(3);  screen.move(66, 10); screen.text('v' .. cur .. ' ' .. V().type)
  -- transport line
  screen.level(seq and seq:is_running() and 15 or 3)
  screen.move(4, 21); screen.text(seq and seq:is_running() and '\u{25b6}' or '\u{25a0}')
  screen.level(3)
  screen.move(16, 21); screen.text(string.format('%.0f bpm', clock.get_tempo()))
  local held = gridui.held()
  screen.move(70, 21)
  if held then
    local st = V().pattern[held]
    screen.text('s' .. held .. ' ' .. musicutil.note_num_to_name((st and st.pitch) or V().pitch, true))
  else
    screen.text(musicutil.note_num_to_name(V().pitch, true))
  end
  -- macro axes (of the current voice)
  for _, ax in ipairs(AXES) do
    local yy = YROW[ax]
    screen.level(sel == ax and 15 or 3)
    screen.move(4, yy);  screen.text(string.upper(ax))
    screen.move(20, yy); screen.text(string.format('%.2f', gridui.axis_value(ax)))
    screen.rect(48, yy - 4, 72 * gridui.axis_value(ax), 3); screen.fill()
  end
  screen.level(3); screen.move(4, 62); screen.text('row 8: play · reset · voices')
  screen.update()
end

function cleanup()
  if seq then seq:stop() end
end
