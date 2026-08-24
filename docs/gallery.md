# Gallery

Barberfish on the Karoo, including the states a short ride might not show. Where a screenshot has a deeper story, the line above it links there.

## HUD and elevation profile

Slots, columns, and the profile modes are set per field in the Barberfish app ([every field and its options](data-fields.md)). Tap the profile to cycle 5, 10, or 20 km of lookahead. The profile has [a page of its own](elevation-profile.md).

<table>
  <tr>
    <td align="center">Elevation profile below a 3-column HUD on the map view</td>
    <td align="center">4-column HUD config with fill-mode zone coloring</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/hud_sparkline.jpg" alt="3-column HUD with elevation profile over the map view"></td>
    <td align="center"><img src="screenshots/hud_config.jpg" alt="HUD config screen with 4-column layout and fill-mode zones"></td>
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
    <td align="center">Light mode with zone-colored HUD and field comparison</td>
    <td align="center">Karoo native fields beside their Barberfish counterparts</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/light_mode.jpg" alt="Light mode data page with zone-colored HUD and Karoo vs Barberfish comparison"></td>
    <td align="center"><img src="screenshots/karoo_vs_barberfish.jpg" alt="Karoo native fields next to Barberfish equivalents on a 5-row data page"></td>
  </tr>
</table>

## Config screens

[Every option](data-fields.md) lives in the Barberfish app with live previews; threshold coloring compares against [a target or range](data-fields.md#thresholds).

<table>
  <tr>
    <td align="center">Data field configuration grouped by category</td>
    <td align="center">Palette pickers with live previews</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/config.jpg" alt="Main Barberfish config screen with its seven collapsed sections"></td>
    <td align="center"><img src="screenshots/palette_config.jpg" alt="Palettes section with power, HR, and grade palette pickers and their previews"></td>
  </tr>
</table>
