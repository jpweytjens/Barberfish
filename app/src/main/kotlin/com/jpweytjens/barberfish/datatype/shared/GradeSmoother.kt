package com.jpweytjens.barberfish.datatype.shared

const val GRADE_BASELINE_M = 30.0
const val GRADE_SPEED_THRESHOLD_MS = 0.5
const val GRADE_RING_CAPACITY = 64

private class GradeRingBuffer(private val capacity: Int) {
    private val dist = FloatArray(capacity)
    private val elev = FloatArray(capacity)
    private var head = 0
    private var tail = 0
    var size: Int = 0
        private set

    fun push(distM: Float, elevM: Float) {
        require(size < capacity) { "GradeRingBuffer overflow ($capacity)" }
        dist[head] = distM
        elev[head] = elevM
        head = (head + 1) % capacity
        size++
    }

    fun popOldest(): Pair<Float, Float> {
        require(size > 0) { "GradeRingBuffer empty" }
        val s = dist[tail] to elev[tail]
        tail = (tail + 1) % capacity
        size--
        return s
    }

    fun oldestDist(): Float = dist[tail]

    fun peekDist(indexFromOldest: Int): Float {
        require(indexFromOldest in 0 until size)
        return dist[(tail + indexFromOldest) % capacity]
    }
}

class GradeSmoother(
    private val baselineM: Double = GRADE_BASELINE_M,
    private val speedThresholdMs: Double = GRADE_SPEED_THRESHOLD_MS,
    capacity: Int = GRADE_RING_CAPACITY,
) {
    private val buf = GradeRingBuffer(capacity)
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumXY = 0.0
    private var sumXX = 0.0
    private var minAnchorD: Float = 0.0f

    // Moving-state tracking for the snapshot/barrier mechanism (see update()).
    private var elevAtPause: Float? = null
    private var wasMoving = false
    private var lastMovingElev: Float = 0.0f
    private var initialised = false

    fun update(elevM: Float, distM: Float, speedMs: Float): Float? {
        if (!initialised) {
            lastMovingElev = elevM
            initialised = true
        }
        val moving = speedMs >= speedThresholdMs

        // Track the elevation at the last moving sample. This is what we snapshot at
        // the moving → not-moving transition, so the snapshot represents the elev BEFORE
        // any gap, deadband fire, or sensor drop that occurs across the boundary.
        if (moving) lastMovingElev = elevM

        if (!moving && wasMoving) {
            // Just stopped — snapshot the elev from the LAST moving sample.
            elevAtPause = lastMovingElev
        } else if (moving && !wasMoving) {
            // Just resumed — if elev differs from snapshot, the buffer is no longer
            // continuous with the road. Insert a barrier; the eviction loop below
            // will pop every pre-barrier sample on this tick.
            val snap = elevAtPause
            if (snap != null && elevM != snap) {
                minAnchorD = distM
            }
            elevAtPause = null
        }
        wasMoving = moving

        if (!moving) return null

        // Push current sample.
        buf.push(distM, elevM)
        val xD = distM.toDouble()
        val yD = elevM.toDouble()
        sumX += xD
        sumY += yD
        sumXY += xD * yD
        sumXX += xD * xD

        // Evict any sample whose distance is below the current anchor floor.
        while (buf.size > 0 && buf.oldestDist() < minAnchorD) {
            removeOldestFromSums()
        }
        // Evict samples beyond the baseline window — keep the OLDEST sample for which
        // (distM - sample.distM) >= baselineM. A second-oldest may take its place only if
        // it too satisfies the baseline-length condition.
        while (buf.size > 1) {
            val secondOldest = buf.peekDist(1)
            if (distM - secondOldest < baselineM) break
            removeOldestFromSums()
        }

        if (buf.size < 2) return null
        val run = distM - buf.oldestDist()
        if (run < baselineM) return null

        val n = buf.size
        val denom = n * sumXX - sumX * sumX
        if (denom <= 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denom
        return (100.0 * slope).toFloat()
    }

    private fun removeOldestFromSums() {
        val (d, e) = buf.popOldest()
        val xD = d.toDouble()
        val yD = e.toDouble()
        sumX -= xD
        sumY -= yD
        sumXY -= xD * yD
        sumXX -= xD * xD
    }
}
