-- lib/seq.lua — clock/transport + step advance at 32nd-note resolution. [beep:clock-transport]
--
-- A step sequencer driven by a clock coroutine synced to the global tempo:
--   div = 1/8 beat = one 32nd note (every other tick is a 16th; 32 steps = one bar).
-- Tempo tracks whatever the CLOCK source is (internal / MIDI / Link) automatically,
-- because clock.sync quantizes to the shared beat grid.
--
-- Real grid-authored patterns arrive with step-sequencer; this carries a stand-in
-- pattern so we can hear the grid resolution + tempo-sync working now.

local Seq = {}
Seq.__index = Seq

local STEPS = 32   -- one bar of 4/4 at 32nd resolution

-- stand-in: 16th-note pulse (every other 32nd), accent on each beat's downbeat.
local function default_pattern()
  local p = {}
  for i = 1, STEPS do
    local on = ((i - 1) % 2 == 0)          -- 16th pulse
    if on then p[i] = { accent = ((i - 1) % 8 == 0) } else p[i] = false end
  end
  return p
end

function Seq.new(opts)
  opts = opts or {}
  local s = setmetatable({}, Seq)
  s.steps   = opts.pattern or default_pattern()
  s.div     = opts.div or (1 / 8)          -- 32nd note
  s.pos     = 0
  s.id      = nil
  s.on_trig = opts.on_trig or function() end
  s.on_step = opts.on_step or function() end
  return s
end

function Seq:is_running() return self.id ~= nil end

function Seq:_loop()
  while true do
    clock.sync(self.div)                    -- quantize to the 32nd grid
    self.pos = (self.pos % #self.steps) + 1
    local st = self.steps[self.pos]
    if st then self.on_trig(st) end
    self.on_step(self.pos)
  end
end

function Seq:start()
  if self.id then return end
  self.id = clock.run(function() self:_loop() end)
end

function Seq:stop()
  if self.id then clock.cancel(self.id); self.id = nil end
end

function Seq:reset() self.pos = 0 end

function Seq:toggle()
  if self.id then self:stop() else self:start() end
end

return Seq
