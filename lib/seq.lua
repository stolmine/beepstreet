-- lib/seq.lua — clock/transport position driver at 32nd resolution. [beep:clock-transport]
--
-- Advances one shared step position, synced to the global tempo (clock.sync(1/8) =
-- a 32nd note). The host triggers each voice's step in the on_step callback, so the
-- sequencer stays voice-agnostic. Per-voice clock-division / length (polymeter) is a
-- later step (micro-faders-perf) and would give each voice its own position.

local Seq = {}
Seq.__index = Seq
local STEPS = 32

function Seq.new(opts)
  opts = opts or {}
  local s = setmetatable({}, Seq)
  s.len     = opts.len or STEPS
  s.div     = opts.div or (1 / 8)
  s.pos     = 0
  s.id      = nil
  s.on_step = opts.on_step or function() end
  return s
end

function Seq:is_running() return self.id ~= nil end

function Seq:_loop()
  while true do
    clock.sync(self.div)
    self.pos = (self.pos % self.len) + 1
    self.on_step(self.pos)
  end
end

function Seq:start() if not self.id then self.id = clock.run(function() self:_loop() end) end end
function Seq:stop()  if self.id then clock.cancel(self.id); self.id = nil end end
function Seq:reset() self.pos = 0 end
function Seq:toggle() if self.id then self:stop() else self:start() end end

return Seq
