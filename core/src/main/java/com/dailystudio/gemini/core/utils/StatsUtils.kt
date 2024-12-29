package com.dailystudio.gemini.core.utils

import com.dailystudio.devbricksx.development.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class Stats {

    var time: Long = 0
    var count: Long = 0
    var avg: Long = 0

    private var startTime: Long = 0

    fun markStart() {
        startTime = System.currentTimeMillis()
    }

    fun markEnd() {
        time = System.currentTimeMillis() - startTime
        avg =
            (avg * count + time) / (count + 1)

        count++
    }
}

object StatsUtils {

    private const val DEFAULT_TAG = "default"

    private val _allStats = MutableStateFlow<Map<String, Stats>>(emptyMap())
    val allStats = _allStats.asStateFlow()

    fun startMeasurement(tag: String): Stats {
        return _allStats.value[tag] ?: Stats().apply {
            markStart()
        }
    }

    fun endMeasurement(tag: String) {
        val stats = _allStats.value[tag] ?: return

        stats.markEnd()

        _allStats.update {
            it + (tag to stats)
        }

        Logger.debug("[STATS: $tag]: time = ${stats.time}, average = ${stats.avg} [count: ${stats.count}]")
    }

    fun <R> measure(tag: String = DEFAULT_TAG, block: () -> R): R {
        startMeasurement(tag)
        val result = block()
        endMeasurement(tag)

        return result
    }

    suspend fun <R> measureSuspend(tag: String = DEFAULT_TAG, block: suspend () -> R): R {
        startMeasurement(tag)
        val result = block()
        endMeasurement(tag)

        return result
    }

    fun getMeasuredTime(tag: String = DEFAULT_TAG): Stats {
        return _allStats.value[tag] ?: Stats()
    }

}