-- lib/fader.lua — vertical 4-key column fader value model. [beep:adsr-mix-microgrid]
--
-- Occupies one grid column across rows 1..4 (top..bottom). Coarse by design — for
-- per-voice mix/perf params (vol/pan/prob/…), a few musical stops beat 240 values.
--   unipolar : value 0..1, filled from the bottom (5 levels).
--   bipolar  : value -1..1, center detent at the rows 2/3 boundary (5 levels;
--              two middle keys are the 0 zone, outer keys the ±extremes).

local Fader = {}
local ROWS = 4

-- ── unipolar ────────────────────────────────────────────────────────────────
function Fader.uni_render(g, col, v)
  local lit = math.floor(v * ROWS + 0.5)            -- 0..4 keys
  for r = 1, ROWS do
    local from_bottom = ROWS - r + 1                -- row4→1 … row1→4
    g:led(col, r, from_bottom <= lit and 15 or 2)
  end
end

function Fader.uni_tap(v, row)
  local pos = ROWS - row + 1                         -- bottom-origin 1..4
  local cur = math.floor(v * ROWS + 0.5)
  if pos == cur then return (pos - 1) / ROWS         -- tap top of bar → dial down
  else return pos / ROWS end
end

-- ── bipolar ─────────────────────────────────────────────────────────────────
function Fader.bi_render(g, col, v)
  local lvl = math.floor(v * 2 + 0.5)                -- -2..2
  for r = 1, ROWS do g:led(col, r, 0) end
  if lvl == 2 then g:led(col, 1, 15); g:led(col, 2, 15)
  elseif lvl == 1 then g:led(col, 2, 15)
  elseif lvl == 0 then g:led(col, 2, 7); g:led(col, 3, 7)   -- center: visible glow when selected
  elseif lvl == -1 then g:led(col, 3, 15)
  elseif lvl == -2 then g:led(col, 3, 15); g:led(col, 4, 15) end
end

function Fader.bi_tap(v, row)
  local lvl = math.floor(v * 2 + 0.5)
  local new
  if row == 1 then new = 2
  elseif row == 2 then new = (lvl == 1) and 0 or 1   -- tap lit center → 0
  elseif row == 3 then new = (lvl == -1) and 0 or -1
  else new = -2 end
  return new / 2
end

return Fader
