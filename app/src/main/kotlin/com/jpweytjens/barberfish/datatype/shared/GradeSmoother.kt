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
    fun oldestElev(): Float = elev[tail]

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

    fun update(elevM: Float, distM: Float, speedMs: Float): Float? {
        // Stub: never returns a value yet.
        return null
    }
}
