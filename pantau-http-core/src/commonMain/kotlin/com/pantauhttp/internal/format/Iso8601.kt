package com.pantauhttp.internal.format

/** UTC ISO-8601 without fractional seconds, e.g. `2025-07-29T08:00:00Z`. */
internal object Iso8601 {

    fun format(epochMillis: Long): String {
        val totalSeconds = Math.floorDiv(epochMillis, 1000L)
        val days = Math.floorDiv(totalSeconds, 86_400L)
        val secondsOfDay = Math.floorMod(totalSeconds, 86_400L).toInt()
        val (year, month, day) = civilFromDays(days)
        val hour = secondsOfDay / 3600
        val minute = (secondsOfDay % 3600) / 60
        val second = secondsOfDay % 60
        return "${year.pad(4)}-${month.pad(2)}-${day.pad(2)}T${hour.pad(2)}:${minute.pad(2)}:${second.pad(2)}Z"
    }

    /** Howard Hinnant's days-to-civil algorithm. */
    private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = (z - era * 146_097).toInt()
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val year = if (m <= 2) y + 1 else y
        return Triple(year.toInt(), m, d)
    }

    private fun Int.pad(width: Int): String = toString().padStart(width, '0')

    private object Math {
        fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q }
        fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
    }
}
