# Barberfish

![Barberfish](app/src/main/res/drawable/ic_extension.png)

[Barberfishes](https://en.wikipedia.org/wiki/Johnrandallia) keeps Hammerheads sharp, in the ocean and on your bike.
Native-feeling data field enhancements for the [Hammerhead Karoo](https://www.hammerhead.io/).

## Data field enhancements

| Feature             | Default Karoo                                                                                 | Barberfish                                                                               |
| ------------------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| 3-column HUD        | Not available                                                                                 | Speed, HR, and power side by side with zone coloring                                     |
| Zone color palettes | Hammerhead only                                                                               | Adds Wahoo, Zwift, and intervals.icu palettes                                            |
| Time formatting     | `hh:mm` or `mm:ss` depending on duration                                                      | `xxh xxm xxs`, `xxh xx' xx"`, or `hh:mm:ss`                                              |
| Average speed       | No threshold coloring                                                                         | Total and moving-time variants, colored by configurable single threshold or min/max zone |
| Time fields         | [Built-in](https://support.hammerhead.io/hc/en-us/articles/35533240795419-Data-Fields-Legend) | Reimplemented with Barberfish formatting options                                         |

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

### Daylight

- Time to sunrise
- Time to sunset
- Time to civil dawn
- Time to civil dusk

## Use cases

### ACP randonneuring (min + max threshold)

[ACP randonneuring](https://www.audax-club-parisien.com/en/welcomepage/) events impose checkpoint cutoff speeds — 15 km/h minimum and 30 km/h maximum. Set Min: 15 and Max: 30 on the average speed field. The field colors green inside the zone, orange when approaching a boundary, and red when outside.

### Race with a goal pace (single threshold)

Racing a gran fondo with a target average? Set a single threshold at your goal pace. The field colors green above it and red below, so you know at a glance whether you're on track.


## Installation

1. Find the APK link on the [latest release page](https://github.com/jpweytjens/barberfish/releases/latest).
2. Sideload the APK into your Karoo following [Hammerhead's sideloading instructions](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Karoo-Extension-Sideloading).
