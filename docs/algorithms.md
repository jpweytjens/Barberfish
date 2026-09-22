# Algorithms

Most GPS bike computer manufacturers, Hammerhead included, don't publish the
algorithms behind their built-in smoothing and ETA fields. Barberfish uses
explicit, documented ones so the field's behaviour is something you can
predict. This page holds the mechanisms behind the Grade and ETA fields.

## Grade

Grade is smoothed over distance rather than time, fitting an [ordinary least squares](https://en.wikipedia.org/wiki/Ordinary_least_squares) line through the last 30 m of elevation. A fixed-window time average has to pick between jittering with every cadence stroke (short window) and smearing the start and end of a climb (long window). The OLS-over-distance variant sidesteps the trade by following the road instead of the clock: it holds steady at any speed and stops moving when you do. At the start of a ride, before it has 30 m of road to fit, it shows "Searching…"; when you stop, it holds the last reading in grey rather than going blank.

<table>
  <tr>
    <td align="center">Searching during the first 30 m</td>
    <td align="center">Fill colored by the gradient palette</td>
    <td align="center">Grey hold when you stop</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/grade_searching.jpg" alt="Grade data field showing Searching before it has 30 m of road to fit"></td>
    <td align="center"><img src="screenshots/grade_color.jpg" alt="Grade data field in fill mode, orange cell at 13 percent"></td>
    <td align="center"><img src="screenshots/grade_stale.jpg" alt="Grade data field in fill mode holding the last reading in grey when no reliable estimate is available"></td>
  </tr>
</table>

## ETA

An arrival estimate that assumes the rest of the ride goes as fast as the part behind you is fine on flat roads and wrong on hilly ones: a fast descent pulls the arrival time in just before the climb pushes it back out. The current field narrows that error without removing it. It blends a 5-minute and a 1-hour [DEWMA](https://en.wikipedia.org/wiki/Exponential_smoothing#Double_exponential_smoothing) of recent speed with a configurable prior, so the estimate sharpens as the ride goes on rather than starting from a generic guess, and the slow average damps the swing a single descent would otherwise cause. It still only looks backward, so a climb you can see coming does not move the estimate until you are on it.

Riding with it made the limit clear: on hilly terrain, the history of the ride says less about the road ahead than the route's elevation profile does. The next ETA, [Godot](https://github.com/jpweytjens/godot), weights the distance still to ride by its gradient, so a climb ahead pushes the arrival time out before you reach it. It is the planned replacement for this field.
