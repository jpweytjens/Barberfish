package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.buildRouteIndex
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Times one grade map rebuild against route length, the way the map rebuilds on a zoom band or
 * route change (route index already cached). Not part of the normal suite: run it with
 * `BARBERFISH_BENCH=1 ./gradlew testDebugUnitTest --tests '*GradeMapRebuildBenchmark*' -i`.
 *
 * Prints, per length, the vertex count N, the drawn pieces P, the chevrons C and the median time,
 * then the log-log slope of time against N: about 1 for linear work, about 2 for quadratic. The
 * checksum line lets two builds be compared for identical output.
 */
class GradeMapRebuildBenchmark {

    private val tuning =
        resolveGradeMapTuning(
            GradeMapConfig(enabled = true, syncWithSparkline = false),
            SparklineConfig(),
            GradePalette.KAROO,
        )

    /**
     * A straight route with a vertex every 20 m and rolling terrain, so the colour changes often.
     */
    private fun route(lengthKm: Int): Pair<String, String> {
        val lengthM = lengthKm * 1000.0
        val gps = RouteFixtures.encoded(RouteFixtures.straight(lengthM, stepM = 20.0))
        val elevation =
            (0..(lengthM / 100).toInt()).map { i ->
                val d = i * 100.0
                val e = 200 + 40 * sin(2 * PI * d / 2000) + 15 * sin(2 * PI * d / 370)
                d.toFloat() to e.toFloat()
            }
        return gps to RouteFixtures.encodeElevation(elevation)
    }

    @Test
    fun rebuildTimeAgainstRouteLength() {
        assumeTrue(System.getenv("BARBERFISH_BENCH") != null)
        val rows =
            listOf(25, 50, 100, 200, 400).map { km ->
                val (gps, elevation) = route(km)
                val index = buildRouteIndex(gps, reversed = false)
                val rebuild = {
                    buildGradeMapSpecs(
                        routePolyline = gps,
                        routeElevationPolyline = elevation,
                        palette = GradePalette.KAROO,
                        readable = false,
                        tuning = tuning,
                        includeChevrons = true,
                        chevronSpacingMaxM = 60.0,
                        chevronSpacingMinM = 60.0,
                        routeIndex = index,
                    )
                }
                val specs = rebuild()
                val timesMs =
                    (1..5).map {
                        val start = System.nanoTime()
                        rebuild()
                        (System.nanoTime() - start) / 1e6
                    }
                val median = timesMs.sorted()[2]
                val checksum = specs.polylines.hashCode() * 31 + specs.chevrons.hashCode()
                println(
                    "bench km=$km N=${index.gps.size} P=${specs.polylines.size} " +
                        "C=${specs.chevrons.size} median=${"%.1f".format(median)} ms " +
                        "checksum=$checksum"
                )
                index.gps.size to median
            }
        val (n0, t0) = rows.first()
        val (n1, t1) = rows.last()
        println("bench slope=${"%.2f".format(ln(t1 / t0) / ln(n1.toDouble() / n0))}")
    }
}
