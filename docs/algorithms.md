# Algorithms

Most GPS bike computer manufacturers, Hammerhead included, don't publish the
algorithms behind their built-in smoothing and ETA fields. Barberfish uses
explicit, documented ones so the field's behaviour is something you can
predict. This page holds the mechanisms behind the Grade field, the grade coloring shared by the elevation profile and the grade map, and ETA.

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

## Grade coloring

The elevation profile and the grade map color stretches of road, not points. A route's elevation trace is jagged, from sensor noise and from real bumps a few metres long, and coloring each sample by its own grade turns a rolling road into a flicker of colors that says nothing about the climb. Two settings decide what gets colored, and they are the same on the Profile field, the HUD strip and the grade map: Simplification cuts the road into stretches, and Emphasis decides which stretches take a color.

### Simplification

The trace is thinned to the points that shape the road. Each interior point forms a triangle with its two neighbours; the point whose triangle is smallest is dropped, and the step repeats until every remaining triangle is at least a set area. The area is distance times elevation, so it is a bump's width times its height, and the setting is a floor on the smallest bump the profile keeps: Off keeps every point, Mild drops sensor noise, Medium merges most short wiggles, Max leaves abstract blocks. The foot of a climb survives even at Max, since a corner between flat and steep makes a large triangle however short the segments around it. This is [Visvalingam–Whyatt](https://en.wikipedia.org/wiki/Visvalingam%E2%80%93Whyatt_algorithm) simplification, run on the elevation trace rather than on a map outline. Each stretch between two surviving points is then one segment, colored by its average grade.

The color therefore reflects the trend of the road, not the number on the Grade field, and on rolling terrain the two disagree: a gentle descent broken by short rises can average slightly uphill and stay uncolored while the Grade field reads negative most of the way. Turning Simplification down narrows the gap by keeping more of the small detail, at the cost of a profile that changes color more often.

The Overview field, which draws the whole route in one strip, keeps a fixed number of points instead of an area floor, so a 30 km and a 300 km route look equally simplified at full-route zoom. The grade map scales the floor with the map zoom, since zooming out makes each pixel cover more road but the same elevation.

### Emphasis

Every segment now has a grade, and Emphasis sets the grade at which color starts. The control is a handle on the palette bar. It snaps to the palette's band boundaries, so it colors whole bands: from the handle's band up on the climb side, and from its band down on the descent side. Gentler road stays quiet, unfilled on the profile and drawn in the map's neutral, so flat road stays flat and the climbs stand out. Parking a handle at the end of the bar turns that side off.

What a handle can reach depends on the palette, which is why the [palettes page](color-palettes.md#grade-palettes) lists two properties per palette. A descent handle exists only on a palette that colors descents (Barberfish, Surgeonfish, Turbo); on the others no descent takes a color anywhere, and the bar has a climb handle only. And on a palette whose flat band spans zero (Barberfish, Surgeonfish), the climb handle has one more stop, at the lower edge of that band, which colors the flat band too and puts every color in the palette on the road. A palette whose bands start at zero has no such stop, because its flat band is already the first climb band.

### One pipeline, three surfaces

The HUD strip, the Profile field and the grade map run the same segmentation and the same emphasis, so a color means the same grade on all three; the map takes its settings from the Profile field unless its Tuning is set to Independent. The same segments are the input to the next ETA, which prices each one by its gradient instead of coloring it.

## ETA

An arrival estimate that carries the speed so far to the finish is fine on the flat and wrong on hills: a descent pulls the ETA in just before the climb pushes it out. The current field blends a 5-minute and a 1-hour [DEWMA](https://en.wikipedia.org/wiki/Exponential_smoothing#Double_exponential_smoothing) of speed with a configurable prior, so the estimate sharpens over the ride and one descent no longer swings it, but it still cannot see the climb ahead. On hilly terrain the ride so far says less about the road than the route's profile does. [Godot](https://github.com/jpweytjens/godot), the planned replacement, takes the [same segments](#grade-coloring) as the grade coloring and prices each by its gradient, so a climb ahead pushes the arrival out before you reach it.
