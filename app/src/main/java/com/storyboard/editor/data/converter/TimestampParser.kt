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
            """^\s*(\d+(?:\.\d+)?(?::\d+(?:\.\d+)?)?)\s*[-–]\s*(\d+(?:\.\d+)?(?::\d+(?:\.\d+)?)?)s?\s*$"""
        )
    }

    fun parse(raw: String): TimestampParseResult {
        val timestamps = mutableListOf<ParsedTimestamp>()
        val errors = mutableListOf<TimestampError>()

        raw.split("\n").forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val match = TIMESTAMP_REGEX.find(trimmed)
            if (match == null) {
                errors += TimestampError(
                    lineNumber = index + 1,
                    rawLine = trimmed,
                    message = "Invalid format. Expected 'start-end' (e.g., '0-5s' or '00:00-00:05')."
                )
                return@forEachIndexed
            }

            val startStr = match.groupValues[1]
            val endStr = match.groupValues[2]
            val startSeconds = parseToSeconds(startStr)
            val endSeconds = parseToSeconds(endStr)

            if (startSeconds == null || endSeconds == null) {
                val which = if (startSeconds == null) "start" else "end"
                val value = if (startSeconds == null) startStr else endStr
                errors += TimestampError(
                    lineNumber = index + 1,
                    rawLine = trimmed,
                    message = "Invalid $which time: '$value'. Seconds must be below 60 in mm:ss notation."
                )
                return@forEachIndexed
            }

            if (endSeconds <= startSeconds) {
                errors += TimestampError(
                    lineNumber = index + 1,
                    rawLine = trimmed,
                    message = "End time must be greater than start time."
                )
                return@forEachIndexed
            }

            timestamps += ParsedTimestamp(
                index = timestamps.size,
                startSeconds = startSeconds,
                endSeconds = endSeconds,
                rawLine = trimmed
            )
        }

        return TimestampParseResult(timestamps = timestamps, errors = errors)
    }

    private fun parseToSeconds(time: String): Double? {
        if (!time.contains(":")) {
            return time.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        }

        val parts = time.split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val seconds = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it < 60.0 }
            ?: return null
        return minutes * 60.0 + seconds
    }
}
