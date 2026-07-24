-- lib/strip.lua — value model for a grid param-strip. [beep:param-lock]
--
-- A horizontal strip of n keys encodes a value in [0,1] at n*15 resolution, using
-- the grid's 4-bit LED brightness for sub-cell precision: full keys below a
-- fractional-brightness top key (a bar-graph fader).
--
--   coarse : tap key k        -> fill k keys (value = k/n, top key full).
--   fine   : re-tap the top key -> cycle its brightness 15..1 (down, then wrap).
--            key 1 also reaches 0, so the value can drop to true zero.
--
-- units are the integer currency: units in [0, n*15]; value = units/(n*15).

local Strip = {}
local LEVELS = 15

local function clamp(x, lo, hi) if x < lo then return lo elseif x > hi then return hi else return x end end

function Strip.units_of(v, n) return clamp(math.floor(v * n * LEVELS + 0.5), 0, n * LEVELS) end
function Strip.value_of(units, n) return units / (n * LEVELS) end

-- the highest lit key = the fine-cycle cursor (key 1 when empty)
local function top_key(units)
  if units <= 0 then return 1 end
  local fk, rem = math.floor(units / LEVELS), units % LEVELS
  if rem > 0 then return fk + 1 else return fk end
end

-- current value v + tapped key k -> new value
function Strip.tap(v, n, k)
  local units = Strip.units_of(v, n)
  if k == top_key(units) then
    local lo = (k == 1) and 0 or ((k - 1) * LEVELS + 1)   -- key 1 may reach 0
    units = units - 1
    if units < lo then units = k * LEVELS end             -- wrap back to full
  else
    units = k * LEVELS                                     -- coarse: k keys full
  end
  return Strip.value_of(units, n)
end

-- draw the strip for value v on grid row `row`
function Strip.render(g, row, n, v)
  local units = Strip.units_of(v, n)
  local fk, rem = math.floor(units / LEVELS), units % LEVELS
  for x = 1, n do
    local lvl = 0
    if x <= fk then lvl = 15
    elseif x == fk + 1 and rem > 0 then lvl = rem end
    g:led(x, row, lvl)
  end
end

return Strip
