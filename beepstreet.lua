-- beepstreet — norns + grid instrument for microsound / glitch composition.
--
-- Thin entry point (bootstrap only). Real logic lives in lib/*.lua and
-- lib/Engine_Beepstreet.sc. See docs/macro-model.md and docs/sonic-targets.md.
--
-- SCAFFOLD: proves the Mac -> rsync -> norns.script.load -> sound loop with one
-- test voice (beep) driven by the Lua-forward X/Y/Z macro map. [beep:script-scaffold]

engine.name = 'Beepstreet'

local voices = include('beepstreet/lib/voices')

local g = grid.connect()          -- grid-safe: returns an object even with none attached
local xyz = { x = 0.30, y = 0.30, z = 0.00 }   -- current macro coordinate (test voice)
local sel = 'x'                   -- which axis E3 edits
local screen_dirty = true

local AXES = { 'x', 'y', 'z' }
local YROW = { x = 34, y = 44, z = 54 }

local function trig()
  local p = voices.resolve('beep', xyz)
  engine.trig(p.freq, p.amp, p.atk, p.rel, p.curve, p.pan, p.detune, p.fmIndex)
end

function init()
  -- dirty-flag redraw loop at ~15fps (decouples paint from input)
  clock.run(function()
    while true do
      clock.sleep(1 / 15)
      if screen_dirty then redraw(); screen_dirty = false end
    end
  end)
  screen_dirty = true
end

function key(n, z)
  if z == 1 then
    if n == 3 then
      trig()
    elseif n == 2 then
      sel = (sel == 'x' and 'y') or (sel == 'y' and 'z') or 'x'
    end
    screen_dirty = true
  end
end

function enc(n, d)
  if n == 3 then
    xyz[sel] = util.clamp(xyz[sel] + d / 50, 0, 1)
    screen_dirty = true
  end
end

-- any grid press triggers the test voice (real grid UI comes later)
g.key = function(x, y, z)
  if z == 1 then trig() end
end

function redraw()
  screen.clear()
  screen.level(15); screen.move(4, 10); screen.text('beepstreet')
  screen.level(3);  screen.move(4, 20); screen.text('scaffold · voice: beep')
  for _, ax in ipairs(AXES) do
    local yy = YROW[ax]
    screen.level(sel == ax and 15 or 3)
    screen.move(4, yy);  screen.text(string.upper(ax))
    screen.move(20, yy); screen.text(string.format('%.2f', xyz[ax]))
    screen.rect(48, yy - 4, 72 * xyz[ax], 3); screen.fill()
  end
  screen.level(3); screen.move(4, 62); screen.text('K3 trig · E3 move · K2 axis')
  screen.update()
end

function cleanup()
  -- clock coroutines auto-cancel on unload; engine voices free themselves (doneAction)
end
