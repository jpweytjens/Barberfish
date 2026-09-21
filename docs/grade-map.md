# Grade map

The Karoo's map shows where the route goes. With the grade map on, it also shows how steep. The route is drawn as a band colored by grade, in the same palette as the Grade field and the elevation profile, with chevrons on top for direction. A glance at the map ahead tells you whether the next bend hides a climb and how hard it bites, without swiping to the profile page.

The grade map is one switch in the Barberfish app, under Climbing. It draws whenever you follow a route, and switching it off hands the map back to the Karoo's own route line. The card's preview shows the band on a sample route and, with the map off, the Karoo's line in its place, so the toggle compares the two looks in place.

<img src="screenshots/grade_map_config.jpg" alt="Grade map config card with the enabled toggle, the band preview, tuning, emphasis handles, simplification, and chevron spacing" width="480">

## Reading it

The band covers the whole route, but only grades past the Emphasis handles take a color. Gentler road draws in a neutral at the same width, so the band never thins and flat stretches stay quiet while the climbs stand out. Before coloring, small elevation wiggles are merged into longer stretches (Simplification), each colored by its average grade, the same way the [profile](elevation-profile.md#reading-it) does it. By default the map takes both settings from the elevation profile so the two agree; set Tuning to Independent to give the map its own.

Chevrons carry direction only, and grade stays in the band. Each palette draws its chevron in its own yellow climb color with a black outline, so on the yellow band the outline alone shows, as the Karoo's chevron does on its yellow line. How tightly they bunch is the Chevron spacing setting: Gradient packs them on the steepest ramps, Changes where the gradient shifts, and Balanced splits the difference.

## Behind you, and out and back

As you ride, the band clears behind you and the Karoo's grey ridden trace shows through, so the map keeps its usual sense of where you have been. On a route that covers the same road twice, an out-and-back or a lap course, the first pass draws on top. Once ridden, that pass clears and the return leg's colors take its place, so the road ahead is always the one colored: the hill you just climbed shows its descent colors for the way back. Laps in the same direction look identical on every pass.

## Off route

Leave the route and the Karoo plots a path back. The grade map draws that path as a second band in the map's rerouting red, with red chevrons at a steady spacing since there is no grade to vary them with, and clears it once you rejoin.

## What to expect

The band is drawn over the Karoo's own route line rather than replacing it, in layers the map applies in its own time. On the first draw, and again after a zoom on a long route or one that repeats ground, the layers land one after another, so the band can take a second or two to settle and the Karoo's line may show through until it does. Nothing needs restarting; the band catches up on its own.

## Settings

| Setting | Options | Effect |
| --- | --- | --- |
| Enabled | On / Off | Draw the band and chevrons over the route. Off shows the Karoo's own line. |
| Tuning | Sync / Independent | Take Emphasis and Simplification from the elevation profile, or set them here. |
| Emphasis | Handles on the palette bar | Color starts at each handle's grade; gentler road draws in the neutral. Climb and descent handles are separate when the palette colors descents. |
| Simplification | Off / Mild / Medium / Max | Merges small elevation wiggles into longer same-color stretches. |
| Chevron spacing | Gradient / Balanced / Changes | Bunch chevrons on the steepest ramps, where the gradient shifts, or in between. |
