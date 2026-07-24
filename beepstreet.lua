-- beepstreet — norns + grid instrument for microsound / glitch composition.
--
-- Thin entry point (bootstrap only). Real logic lives in lib/*.lua and
-- lib/Engine_Beepstreet.sc. See docs/macro-model.md and docs/sonic-targets.md.
--
-- Param-strips + hold-to-plock: three grid strips edit X/Y/Z (global, or locked to
-- a held step). The sequencer applies per-step plocks over the global macro.

engine.name = 'Beepstreet'

local musicutil = require 'musicutil'
local voices = include('beepstreet/lib/voices')
local Seq    = include('beepstreet/lib/seq')
local gridui = include('beepstreet/lib/gridui')

local g = grid.connect()          -- grid-safe: returns an object even with none attached
local xyz = { x = 0.30, y = 0.30, z = 0.00 }   -- GLOBAL macro coordinate
local voice = { vol = 1.0, pan = 0.0, prob = 1.0,   -- per-voice micro params (quadrant faders)
                pitch = 48, root = 24, scale = 'Natural Minor' }  -- pitch keyboard state
local sel = 'x'                   -- which axis E3 edits
local pattern = {}                -- 32 steps: false = off, table = on (may carry plocks)
local seq
local screen_dirty = true

local AXES = { 'x', 'y', 'z' }
local YROW = { x = 34, y = 44, z = 54 }

-- resolve + trigger: per-step plocks (x/y/z, vol/pan/prob) over global/per-voice
local function do_trig(st)
  local m = xyz
  local vol, pan, pitch = voice.vol, voice.pan, voice.pitch
  if type(st) == 'table' then
    local prob = st.prob or voice.prob                 -- probability gates sequenced steps only
    if prob < 1 and math.random() > prob then return end
    m = { x = st.x or xyz.x, y = st.y or xyz.y, z = st.z or xyz.z }
    if st.vol ~= nil then vol = st.vol end
    if st.pan ~= nil then pan = st.pan end
    if st.pitch ~= nil then pitch = st.pitch end
  end
  local p = voices.resolve('beep', m)
  engine.trig(musicutil.note_num_to_freq(pitch), p.amp * vol, p.atk, p.rel, p.curve, pan, p.detune, p.fmIndex)
end

function init()
  for i = 1, 32 do pattern[i] = false end   -- start empty; author on the grid

  seq = Seq.new{
    pattern = pattern,
    on_trig = function(st) do_trig(st) end,
    on_step = function(_) screen_dirty = true; gridui.redraw() end,
  }
  gridui.init(g, pattern, seq, xyz, voice)
  gridui.redraw()

  clock.run(function()
    while true do
      clock.sleep(1 / 15)
      if screen_dirty then redraw(); screen_dirty = false end
    end
  end)
  screen_dirty = true
end

-- transport callbacks — also fired by external MIDI / Ableton Link start/stop
function clock.transport.start() seq:start() end
function clock.transport.stop()  seq:stop() end
function clock.transport.reset() seq:reset() end

function key(n, z)
  if z == 1 then
    if n == 2 then seq:toggle()
    elseif n == 3 then do_trig(nil) end
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
    xyz[sel] = util.clamp(xyz[sel] + d / 50, 0, 1)
  end
  screen_dirty = true
end

g.key = function(x, y, z)
  if gridui.key(x, y, z) then screen_dirty = true end
end

function redraw()
  screen.clear()
  screen.level(15); screen.move(4, 10); screen.text('beepstreet')
  -- transport / mode line
  screen.level(seq and seq:is_running() and 15 or 3)
  screen.move(4, 21); screen.text(seq and seq:is_running() and '\u{25b6}' or '\u{25a0}')
  screen.level(3)
  screen.move(16, 21); screen.text(string.format('%.0f bpm', clock.get_tempo()))
  local held = gridui.held()
  screen.move(70, 21)
  if held then
    local st = pattern[held]
    screen.text('s' .. held .. ' ' .. musicutil.note_num_to_name((st and st.pitch) or voice.pitch, true))
  else
    screen.text(musicutil.note_num_to_name(voice.pitch, true))
  end
  -- macro axes (show what the strips are editing: held plock or global)
  for _, ax in ipairs(AXES) do
    local yy = YROW[ax]
    screen.level(sel == ax and 15 or 3)
    screen.move(4, yy);  screen.text(string.upper(ax))
    screen.move(20, yy); screen.text(string.format('%.2f', gridui.axis_value(ax)))
    screen.rect(48, yy - 4, 72 * gridui.axis_value(ax), 3); screen.fill()
  end
  screen.level(3); screen.move(4, 62); screen.text('steps · faders · strips · hold=plock')
  screen.update()
end

function cleanup()
  if seq then seq:stop() end
end
