package com.storyboard.editor.data.converter

import com.storyboard.editor.data.model.ParsedTimestamp
import com.storyboard.editor.data.model.TimestampError
import com.storyboard.editor.data.model.TimestampParseResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimestampParser @Inject constructor() {

    companion object {
        private val TIMESTAMP_REGEX = Regex(
            """^\s*([\d:.]+)\s*[-–]\s*([\d:.]+)s?\s*$"""
        )
    }

    fun parse(raw: String): TimestampParseResult {
        val lines = raw.split("\n")
        val timestamps = mutableListOf<ParsedTimestamp>()
        val errors = mutableListOf<TimestampError>()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val match = TIMESTAMP_REGEX.find(trimmed)
            if (match == null) {
                errors.add(
                    TimestampError(
                        lineNumber = index + 1,
                        rawLine = trimmed,
                        message = "Invalid format. Expected format: 'start-end' (e.g., '0-5s' or '00:00-00:05')"
                    )
                )
                return@forEachIndexed
            }

            val startStr = match.groupValues[1]
            val endStr = match.groupValues[2]

            val startSeconds = parseToSeconds(startStr)
            val endSeconds = parseToSeconds(endStr)

            if (startSeconds == null) {
                errors.add(
                    TimestampError(
                        lineNumber = index + 1,
                        rawLine = trimmed,
                        message = "Invalid start time format: '$startStr'"
                    )
                )
                return@forEachIndexed
            }

            if (endSeconds == null) {
                errors.add(
                    TimestampError(
                        lineNumber = index + 1,
                        rawLine = trimmed,
                        message = "Invalid end time format: '$endStr'"
                    )
                )
                return@forEachIndexed
            }

            timestamps.add(
                ParsedTimestamp(
                    index = timestamps.size,
                    startSeconds = startSeconds,
                    endSeconds = endSeconds,
                    rawLine = trimmed
                )
            )
        }

        return TimestampParseResult(
            timestamps = timestamps,
            errors = errors
        )
    }

    private fun parseToSeconds(time: String): Double? {
        return when {
            time.contains(":") -> {
                val parts = time.split(":")
                if (parts.size == 2) {
                    val minutes = parts[0].toDoubleOrNull()
                    val seconds = parts[1].toDoubleOrNull()
                    if (minutes != null && seconds != null && seconds < 60)
                        minutes * 60 + seconds
                    else null
                } else null
            }
            else -> time.toDoubleOrNull()?.coerceAtLeast(0.0)
        }
    }
}
