# Barberfish

![Barberfish](app/src/main/res/drawable/ic_extension.png)

[Barberfishes](https://en.wikipedia.org/wiki/Johnrandallia) keeps Hammerheads sharp, on your handlebars and in the ocean.
Native-feeling data field enhancements for the Hammerhead Karoo.

## Data field enhancements

| Feature             | Default Karoo                                                                                 | Barberfish                                                                   |
| ------------------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 3-column HUD        | Not available                                                                                 | Speed, HR, and power side by side with zone coloring                         |
| Zone color palettes | Hammerhead only                                                                               | Adds Wahoo, Zwift, and intervals.icu palettes                                |
| Time formatting     | `hh:mm` or `mm:ss` depending on duration                                                      | `xxh xxm xxs`, `xxh xx' xx"`, or `hh:mm:ss`                                  |
| Average speed       | No threshold coloring                                                                         | Total and moving-time variants, colored by configurable single threshold or min/max zone |
| Time fields         | [Built-in](https://support.hammerhead.io/hc/en-us/articles/35533240795419-Data-Fields-Legend) | Reimplemented with Barberfish formatting options                             |

## Data fields

Complete list of data fields provided by Barberfish.

### HUD

- HUD — three-column speed, heart rate, and power with zone coloring

### Power & Heart Rate

- Power (Instant, 3s, 5s, 10s, 30s smoothing)
- Heart rate

### Speed

- Speed (Instant, 3s, 5s, 10s smoothing)
- Average speed including paused time
- Aver speed excluding paused time
### Time

- Elapsed time
- Moving time
- Paused time
### Navigation

- Time to destination
- Time of arrival

### Daylight

- Time to sunrise
- Time to sunset
- Time to civil dawn
- Time to civil dusk

## Use cases

### ACP randonneuring

[ACP randonneuring](https://www.audax-club-parisien.com/en/welcomepage/) events impose checkpoint cutoff speeds: a minimum of 15 km/h and a maximum of 30 km/h. Riding below 15 km/h risks missing a checkpoint closing time; riding above 30 km/h disqualifies the attempt.

Configure the average speed field with **Min: 15 km/h** and **Max: 30 km/h**. The color signals your position relative to the target zone:

| Color  | Meaning |
|--------|---------|
| Green  | Within the target speed zone |
| Orange | Approaching a boundary (width configurable via range %) |
| Red    | Outside the zone — too slow or too fast |

The field also supports simpler configurations:

- **Single threshold** — green above, red below. Useful for a minimum pace target.
- **Min only** — red when below the minimum, neutral otherwise.
- **Max only** — red when above the maximum, neutral otherwise.


## Installation

1. Find the APK link on the [latest release page](https://github.com/jpweytjens/barberfish/releases/latest).
2. Sideload the APK into your Karoo following [Hammerhead's sideloading instructions](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Karoo-Extension-Sideloading).
