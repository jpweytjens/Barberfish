# Algorithms

Most GPS bike computer manufacturers, Hammerhead included, don't publish the
algorithms behind their built-in smoothing and ETA fields. Barberfish uses
explicit, documented ones so the field's behaviour is something you can
predict. This page holds the mechanisms behind the Grade field, the grade coloring shared by the elevation profile and the grade map, and ETA.

## Grade

Grade is the slope of a [least squares](https://en.wikipedia.org/wiki/Ordinary_least_squares) line fitted through the last 30 m of road, so it reads the same at any speed and stops moving when you do; an average over the last few seconds would instead jitter with every pedal stroke when short and smear the foot and crest of a climb when long. Before the first 30 m it shows "Searching…", and when you stop it holds the last reading in grey rather than going blank.

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

A route's elevation trace is a list of points a few metres apart, each a distance along the road and a height. Colored point by point, a rolling road flickers between colors and says nothing about the climb, so the elevation profile and the grade map color stretches instead. Two settings shape them, the same on the Profile field, the HUD strip and the grade map: Simplification decides where a stretch starts and ends, and Emphasis which stretches take a color.

### Simplification

The stretches are the trace with its least significant points dropped. The point whose removal moves the line least goes first, and removal continues until every remaining point marks a bump of at least a set size, its width along the road times its height: Off keeps every point, Mild drops sensor noise, Medium merges short wiggles, Max leaves blocks. The corner where flat turns to climb survives even at Max, because removing it would move the line a lot. This is [Visvalingam–Whyatt](https://en.wikipedia.org/wiki/Visvalingam%E2%80%93Whyatt_algorithm) simplification. Each stretch between two surviving points becomes one segment colored by its average grade, so the color shows the trend of the road rather than the number on the Grade field; on rolling terrain a gentle descent broken by short rises can average uphill and stay unfilled while the field reads negative. Lower Simplification narrows that gap, at the cost of a profile that changes color more often.

### Emphasis

Every segment now has a grade, and Emphasis sets the grade at which color starts. The control is a handle on the palette bar. It snaps to the palette's band boundaries, so it colors whole bands: from the handle's band up on the climb side, and from its band down on the descent side. Gentler road stays quiet, unfilled on the profile and drawn in the map's neutral, so flat road stays flat and the climbs stand out. Parking a handle at the end of the bar turns that side off.

What a handle can reach depends on the palette, which is why the [palettes page](color-palettes.md#grade-palettes) lists two properties per palette. A descent handle exists only on a palette that colors descents (Barberfish, Surgeonfish, Turbo); on the others no descent takes a color anywhere, and the bar has a climb handle only. And on a palette whose flat band spans zero (Barberfish, Surgeonfish), the climb handle has one more stop, at the lower edge of that band, which colors the flat band too and puts every color in the palette on the road. A palette whose bands start at zero has no such stop, because its flat band is already the first climb band.

### One pipeline, three surfaces

The HUD strip, the Profile field and the grade map run the same segmentation and the same emphasis, so a color means the same grade on all three; the map takes its settings from the Profile field unless its Tuning is set to Independent. The same segments are the input to the next ETA, which prices each one by its gradient instead of coloring it.

## ETA

An arrival estimate that carries the speed so far to the finish is fine on the flat and wrong on hills: a descent pulls the ETA in just before the climb pushes it out. The current field blends a 5-minute and a 1-hour [DEWMA](https://en.wikipedia.org/wiki/Exponential_smoothing#Double_exponential_smoothing) of speed with a configurable prior, so the estimate sharpens over the ride and one descent no longer swings it, but it still cannot see the climb ahead. On hilly terrain the ride so far says less about the road than the route's profile does. [Godot](https://github.com/jpweytjens/godot), the planned replacement, takes the [same segments](#grade-coloring) as the grade coloring and prices each by its gradient, so a climb ahead pushes the arrival out before you reach it.
