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
| Average speed       | No threshold coloring                                                                         | Total and moving-time variants, colored above/below a configurable threshold |
| Time fields         | [Built-in](https://support.hammerhead.io/hc/en-us/articles/35533240795419-Data-Fields-Legend) | Reimplemented with Barberfish formatting options                             |

## Data fields

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

[ACP randonneuring](https://www.audax-club-parisien.com/en/welcomepage/) events impose checkpoint cutoff speeds: a minimum of 15 km/h and a maximum of 30 km/h. Riding below 15 km/h risks missing a checkpoint closing time; riding above 30 km/h disqualifies the attempt. Alternatively, replace these values with your event's thresholds.

The field grades from blue to red as your average speed drifts from your threshold. It turns redder the further you fall below, bluer the further you push above. How quickly the color appears can also be configured.


## Installation

1. Find the APK link on the [latest release page](https://github.com/jpweytjens/barberfish/releases/latest).
2. Sideload the APK into your Karoo following [Hammerhead's sideloading instructions](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Karoo-Extension-Sideloading).
