# Gallery

## HUD and elevation profile

The [HUD](hud.md) and the [elevation profile](elevation-profile.md) each have a page of their own.

<table>
  <tr>
    <td align="center">Elevation profile below a 3-column HUD on the map view</td>
    <td align="center">HUD config with the Power column tapped to set its smoothing and zone color</td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="screenshots/hud_sparkline.jpg" alt="Map page with a 3-column HUD, the elevation profile, and the route drawn as a grade-colored band with chevrons up the Molenberg"></td>
    <td align="center" valign="top"><img src="screenshots/hud_config.jpg" alt="HUD config screen with a 4-column preview, the Power column selected, and its data field, smoothing, and zone color options below"></td>
  </tr>
</table>

## Climbs mode

The profile stays hidden until a climb nears, flags it with a heads-up counter (`Climb 1/1` below: the first and only climb on that route), then frames it foot to summit until it clears at the top. The overlay appears earlier for harder climbs.

<table>
  <tr>
    <td align="center">Climbs mode flags the next climb before it arrives</td>
    <td align="center">Climbs mode frames the climb foot to summit</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/climbs_counter.jpg" alt="Climbs mode heads-up showing the next climb on the route"></td>
    <td align="center"><img src="screenshots/climbs_profile.jpg" alt="Climbs mode profile framing a climb foot to summit with the position dot partway up"></td>
  </tr>
</table>

## Grade

Grade colors its cell by the gradient palette, searches until it has 30 m of road, and greys out when the estimate is not reliable, holding the last reading instead of going blank ([how the estimate works](algorithms.md#grade)).

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

## Themes and the native comparison

Light and dark mode are both supported, with [each palette tuned per theme](color-palettes.md).

<table>
  <tr>
    <td align="center">Light mode with the HUD and route fields</td>
    <td align="center">Karoo native fields beside their Barberfish counterparts</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/light_mode.jpg" alt="Light mode data page with the HUD, Overview, Ride Remaining, HR Zone and Grade"></td>
    <td align="center"><img src="screenshots/karoo_vs_barberfish.jpg" alt="Karoo native fields next to Barberfish equivalents on a 5-row data page"></td>
  </tr>
</table>

## Config screens

Every option lives in the Barberfish app with live previews, and changes apply mid-ride.

<table>
  <tr>
    <td align="center"><a href="data-fields.md">Data fields</a>, grouped by category</td>
    <td align="center"><a href="color-palettes.md">Palette</a> pickers with live previews</td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="screenshots/config.jpg" alt="Main Barberfish config screen with its seven collapsed sections"></td>
    <td align="center" valign="top"><img src="screenshots/palette_config.jpg" alt="Palettes section with power, HR, and grade palette pickers and their previews"></td>
  </tr>
  <tr>
    <td align="center"><a href="matching-karoo.md">Data field design</a>, set to match the Karoo's own</td>
    <td align="center"><a href="data-fields.md#thresholds">Threshold</a> controls on the Speed field</td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="screenshots/design_barberfish.jpg" alt="Barberfish Data Field Design section with icons on and label size small"></td>
    <td align="center" valign="top"><img src="screenshots/threshold_controls.jpg" alt="The Speed field's threshold controls: a source picker offering Fixed, Avg total and Avg moving, a target of 30 km/h, and under and over margins of 10 percent"></td>
  </tr>
</table>
