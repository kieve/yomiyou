package ca.kieve.yomiyou.narration

import java.util.Locale
import kotlin.math.roundToInt

/** Narration speed, kept once for the whole app: the same audio played faster or slower. */
object Speed {
    const val MIN = 0.5f
    const val MAX = 2f
    const val STEP = 0.1f
    const val DEFAULT = 1f

    fun of(stored: Float?): Float = (stored ?: DEFAULT).coerceIn(MIN, MAX)

    fun step(speed: Float, steps: Int): Float =
        (((speed / STEP).roundToInt() + steps) * STEP).coerceIn(MIN, MAX).let { (it * 10).roundToInt() / 10f }

    fun label(speed: Float): String = String.format(Locale.ROOT, "%.1f×", speed)
}
