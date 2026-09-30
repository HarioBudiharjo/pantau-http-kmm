package com.pantauhttp.format

/** Binary (1024-based) byte counts in the style of Foundation's ByteCountFormatter. */
public object ByteCount {
    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    public fun binary(bytes: Long): String = when {
        bytes <= 0L -> "Zero KB"
        bytes == 1L -> "1 byte"
        bytes < KB -> "$bytes bytes"
        bytes < MB -> "${roundDiv(bytes, KB)} KB"
        bytes < GB -> "${oneDecimal(bytes, MB)} MB"
        else -> "${twoDecimals(bytes, GB)} GB"
    }

    private fun roundDiv(value: Long, unit: Long): Long = (value + unit / 2) / unit

    private fun oneDecimal(value: Long, unit: Long): String {
        val tenths = (value * 10 + unit / 2) / unit
        return "${tenths / 10}.${tenths % 10}"
    }

    private fun twoDecimals(value: Long, unit: Long): String {
        val hundredths = (value * 100 + unit / 2) / unit
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
}
