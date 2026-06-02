package com.jpweytjens.barberfish.datatype.shared

sealed interface GradeReading {
    /** No value yet — initial warm-up or stream not Streaming and never has been. */
    data object Unavailable : GradeReading
    /** Last known value, but the smoother is currently greyed. */
    data class Stale(val percent: Float) : GradeReading
    /** Current value from the smoother. */
    data class Fresh(val percent: Float) : GradeReading
}

/** Pure scan reducer used by `gradeOlsFlow`. Visible for testing. */
internal fun gradeReadingReducer(acc: GradeReading, fresh: Float?): GradeReading =
    when {
        fresh != null -> GradeReading.Fresh(fresh)
        acc is GradeReading.Fresh -> GradeReading.Stale(acc.percent)
        else -> acc
    }
