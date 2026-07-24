-- beepstreet — norns + grid instrument for microsound / glitch composition.
--
-- Thin entry point (bootstrap only). Real logic lives in lib/*.lua and
-- lib/Engine_Beepstreet.sc. See docs/macro-model.md and docs/sonic-targets.md.
--
-- Clock/transport milestone: the sequencer plays itself at 32nd resolution,
-- tempo-synced, triggering the beep voice through the Lua-forward macro map.

engine.name = 'Beepstreet'

local voices = include('beepstreet/lib/voices')
local Seq    = include('beepstreet/lib/seq')

local g = grid.connect()          -- grid-safe: returns an object even with none attached
local xyz = { x = 0.30, y = 0.30, z = 0.00 }   -- live macro coordinate (test voice)
local sel = 'x'                   -- which axis E3 edits
local seq
local screen_dirty = true

local AXES = { 'x', 'y', 'z' }
local YROW = { x = 34, y = 44, z = 54 }

local function do_trig(accent)
  local p = voices.resolve('beep', xyz)
  local amp = p.amp * (accent and 1.0 or 0.6)
  engine.trig(p.freq, amp, p.atk, p.rel, p.curve, p.pan, p.detune, p.fmIndex)
end

function init()
  seq = Seq.new{
    on_trig = function(st) do_trig(st.accent) end,
    on_step = function(_) screen_dirty = true end,
  }
  -- dirty-flag redraw loop at ~15fps
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
    if n == 2 then
      seq:toggle()
    elseif n == 3 then
      do_trig(true)
    end
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

-- any grid press triggers the test voice (real grid UI comes later)
g.key = function(x, y, z)
  if z == 1 then do_trig(true) end
end

function redraw()
  screen.clear()
  screen.level(15); screen.move(4, 10); screen.text('beepstreet')
  -- transport line
  screen.level(seq and seq:is_running() and 15 or 3)
  screen.move(4, 21); screen.text(seq and seq:is_running() and '\u{25b6} play' or '\u{25a0} stop')
  screen.level(3)
  screen.move(52, 21);  screen.text(string.format('%.0f bpm', clock.get_tempo()))
  screen.move(100, 21); screen.text('s' .. ((seq and seq.pos) or 0))
  -- macro axes
  for _, ax in ipairs(AXES) do
    local yy = YROW[ax]
    screen.level(sel == ax and 15 or 3)
    screen.move(4, yy);  screen.text(string.upper(ax))
    screen.move(20, yy); screen.text(string.format('%.2f', xyz[ax]))
    screen.rect(48, yy - 4, 72 * xyz[ax], 3); screen.fill()
  end
  screen.level(3); screen.move(4, 62); screen.text('K2 play  E1 bpm  E2/3 axis  K3 trig')
  screen.update()
end

function cleanup()
  if seq then seq:stop() end
end
