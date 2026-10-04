#!/usr/bin/env python3
"""Generate a fully synthetic ClimbPreviewFixture.kt for the config preview.

The preview is illustrative, not a real place: an up-and-over alpine pass whose
hairpin climb crests a col and descends to a lake, with a grade profile that walks
both sides of every palette. Deterministic texture tiers make the simplification
dial visible: each dial step peels one tier. No GPX, no map capture, no
registration. Tune WAYPOINTS (road shape), GRADE_PROFILE (colours), and the
texture tier constants below, then rerun.

Usage:
    python scripts/synth_climb_fixture.py \
        > app/src/main/kotlin/com/jpweytjens/barberfish/datatype/shared/ClimbPreviewFixture.kt
"""

import math
import sys

from gpx_to_climb_fixture import encode_polyline, square_bounds

# Road shape as (east_m, north_m) waypoints. Catmull-Rom rounds them into the road.
# An up-and-over alpine pass in one square viewport: an organic hairpin climb up the
# left flank, a col at top-center, and a varied descent down the right flank ending
# at a lake. Not a real place.
WAYPOINTS = [
    (330, 55),
    (215, 150),
    # lower cluster: two tightish hairpins, short arms
    (445, 235),
    (205, 305),
    (425, 380),
    # long traverse ramp up-left, then a wide-armed hairpin
    (245, 455),
    (575, 555),
    (285, 630),
    (480, 695),
    # big sweeping hairpin, then a quick tight pair with uneven rises
    (195, 780),
    (590, 880),
    (330, 945),
    (510, 1000),
    (295, 1085),
    # exit ramp toward the col
    (505, 1175),
    (700, 1250),
    (795, 1365),  # col
    (940, 1385),
    # descent: medium bend, tighter pair, long open sweep, kinks to the valley
    (1105, 1295),
    (1030, 1140),
    (1215, 1015),
    (1145, 870),
    (1290, 690),
    (1085, 540),
    (1235, 410),
    (1140, 295),
    (1235, 210),
]

# Index into WAYPOINTS of the col (the 0% crossing between climb and descent).
COL_WAYPOINT_INDEX = 16  # (795, 1365)

# Grade as (distance_fraction, grade_pct) control points, linearly interpolated.
# Two-sided: the climb walks 2..22% (every climb band incl. 20%+ purple), the
# descent walks -1..-15% (every descent band of the two-sided palettes). The
# fraction axis assumes the col sits at MOCKUP_COL_FRAC; main() remaps it onto
# the col's actual cumulative-distance fraction after smoothing, so the 21-22%
# peak always sits just below the col and the profile crests 0% exactly there.
MOCKUP_COL_FRAC = 0.515
GRADE_PROFILE = [
    (0.00, 2.5),
    (0.05, 3.5),
    (0.12, 6.0),
    (0.20, 9.0),
    (0.28, 12.0),
    (0.35, 16.0),
    (0.41, 21.0),
    (0.455, 22.0),
    (0.49, 10.0),
    (0.515, 0.0),
    (0.55, -4.0),
    (0.62, -7.5),
    (0.70, -12.0),
    (0.755, -15.0),
    (0.81, -11.0),
    (0.87, -7.0),
    (0.93, -4.0),
    (1.00, -1.0),
]

# Texture tiers layered on the base profile so the simplification dial is visible:
# each tier's Visvalingam triangle area (~0.5 * length * relief) dies at exactly one
# dial step (MILD 25 m2, MEDIUM 60 m2, HEAVY 120 m2; preview metresPerPixel = 0, so
# base thresholds apply unscaled and the cell floor is 30 m). Deterministic, no
# randomness; each bump is (centre_frac, length_m, relief_m), negative relief = dip.
# Every bump spans more than the 30 m cell and pushes its cells' mean grade across
# a band edge, so removing it reads as a colour change, not just geometry.

# Fine ripple, area ~5 m2: visible at NONE only — the noise the dial removes.
RIPPLE_WAVELENGTH_M = 30.0
RIPPLE_RELIEF_M = 0.35

# Small blips, area ~40 m2: survive MILD, merge at MEDIUM.
SMALL_BLIPS = [
    (0.16, 80.0, 1.0),  # 8%+ kick inside the 6-9% stretch
    (0.31, 80.0, 1.0),  # kick inside the 12% pitch
    (0.57, 80.0, -1.0),  # dip inside the -4..-7% run
]

# Medium features, area ~105 m2: survive MEDIUM, merge at HEAVY.
MEDIUM_FEATURES = [
    (0.315, 150.0, 1.4),  # ~14% step inside the 12% pitch
    (0.645, 150.0, -1.4),  # ~-12% ramp inside the -7.5..-12% stretch
]

# Anchor the synthetic metres onto real lat/lng (alpine-ish) so web-mercator
# projection and the squared bounds behave exactly like a captured climb.
LAT0 = 45.20
LNG0 = 6.50
SAMPLES_PER_SEGMENT = 22


def catmull_rom(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return (
        0.5
        * (
            (2 * p1[0])
            + (-p0[0] + p2[0]) * t
            + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
            + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3
        ),
        0.5
        * (
            (2 * p1[1])
            + (-p0[1] + p2[1]) * t
            + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
            + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3
        ),
    )


def smooth_path(pts):
    padded = [pts[0]] + pts + [pts[-1]]
    out = []
    for i in range(1, len(padded) - 2):
        for s in range(SAMPLES_PER_SEGMENT):
            out.append(
                catmull_rom(
                    padded[i - 1],
                    padded[i],
                    padded[i + 1],
                    padded[i + 2],
                    s / SAMPLES_PER_SEGMENT,
                )
            )
    out.append(pts[-1])
    return out


def grade_at(profile, frac):
    for (f0, g0), (f1, g1) in zip(profile, profile[1:]):
        if f0 <= frac <= f1:
            w = 0.0 if f1 == f0 else (frac - f0) / (f1 - f0)
            return g0 + (g1 - g0) * w
    return profile[-1][1]


def remap_frac(f, col_frac):
    """Remaps the mockup fraction axis so MOCKUP_COL_FRAC lands on col_frac."""
    if f <= MOCKUP_COL_FRAC:
        return f * col_frac / MOCKUP_COL_FRAC
    return col_frac + (f - MOCKUP_COL_FRAC) * (1.0 - col_frac) / (1.0 - MOCKUP_COL_FRAC)


def bump(dist_m, centre_m, length_m, relief_m):
    """Hann bump: relief at the centre, zero outside [centre - L/2, centre + L/2]."""
    x = (dist_m - centre_m) / (length_m / 2.0)
    if abs(x) >= 1.0:
        return 0.0
    return relief_m * 0.5 * (1.0 + math.cos(math.pi * x))


def main():
    path = smooth_path(WAYPOINTS)
    coslat = math.cos(math.radians(LAT0))
    latlng = [
        (LAT0 + north / 111320.0, LNG0 + east / (111320.0 * coslat))
        for (east, north) in path
    ]

    # Cumulative ground distance for the elevation channel.
    dists = [0.0]
    for (e0, n0), (e1, n1) in zip(path, path[1:]):
        dists.append(dists[-1] + math.hypot(e1 - e0, n1 - n0))
    total = dists[-1]

    # Col fraction: nearest smoothed sample to the col waypoint.
    cx, cy = WAYPOINTS[COL_WAYPOINT_INDEX]
    col_i = min(
        range(len(path)), key=lambda k: math.hypot(path[k][0] - cx, path[k][1] - cy)
    )
    col_frac = dists[col_i] / total
    profile = [(remap_frac(f, col_frac), g) for f, g in GRADE_PROFILE]

    elev = [0.0]
    for prev, cur in zip(dists, dists[1:]):
        g = grade_at(profile, ((prev + cur) / 2) / total)
        elev.append(elev[-1] + (cur - prev) * g / 100.0)

    # Texture: ripple everywhere, blips and features at remapped centres.
    bumps = [
        (remap_frac(f, col_frac) * total, length, relief)
        for f, length, relief in SMALL_BLIPS + MEDIUM_FEATURES
    ]
    elev = [
        e
        + RIPPLE_RELIEF_M * math.sin(2 * math.pi * d / RIPPLE_WAVELENGTH_M)
        + sum(bump(d, c, length, relief) for c, length, relief in bumps)
        for e, d in zip(elev, dists)
    ]

    route_poly = encode_polyline(latlng, 1e5)
    elev_poly = encode_polyline(list(zip(dists, elev)), 10.0)

    lats = [p[0] for p in latlng]
    lngs = [p[1] for p in latlng]
    min_lat, max_lat, min_lng, max_lng = square_bounds(
        min(lats), max(lats), min(lngs), max(lngs), margin=0.06
    )

    print("// Generated by scripts/synth_climb_fixture.py - do not edit by hand.")
    print("package com.jpweytjens.barberfish.datatype.shared")
    print()
    print(
        "/** Synthetic route fixture for the grade map config preview: an up-and-over"
    )
    print(" *  pass that climbs to a col and descends to a lake, its grade walking the")
    print(" *  full palette both ways. Not a real place. */")
    print("internal object ClimbPreviewFixture {")
    esc = chr(92) + chr(92)
    print(f'    const val routePolyline = "{route_poly.replace(chr(92), esc)}"')
    print(f'    const val elevationPolyline = "{elev_poly.replace(chr(92), esc)}"')
    print("    val bounds = LatLngBounds(")
    print(f"        minLat = {min_lat}, maxLat = {max_lat},")
    print(f"        minLng = {min_lng}, maxLng = {max_lng},")
    print("    )")
    print(f"    val climbRanges = listOf<Pair<Double, Double>>(0.0 to {total})")
    print("}")

    print(f"# total {total:.0f} m, col at fraction {col_frac:.3f}", file=sys.stderr)
    for f, length, relief in SMALL_BLIPS + MEDIUM_FEATURES:
        area = 0.5 * length * abs(relief)
        print(
            f"# bump at {f:.3f}: L={length:.0f} m, relief={relief:+.1f} m, VW area ~{area:.0f} m2",
            file=sys.stderr,
        )


if __name__ == "__main__":
    main()
