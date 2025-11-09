@file:OptIn(kotlin.time.ExperimentalTime::class)
package util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import model.LogEntry
import kotlin.time.Clock

object Logging {
    fun log(current: List<LogEntry>, level: String, message: String): List<LogEntry> {
        val entry = LogEntry(level.uppercase(), message, timestamp())
        return (current + entry).takeLast(300)
    }

    private fun timestamp(): String {
        val now = Clock.System.now()
        val ldt = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${ldt.date} ${ldt.hour.toString().padStart(2,'0')}:${ldt.minute.toString().padStart(2,'0')}:${ldt.second.toString().padStart(2,'0')}"
    }
}
