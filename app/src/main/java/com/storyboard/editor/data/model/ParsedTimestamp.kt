package com.storyboard.editor.data.model

data class ParsedTimestamp(
    val index: Int,
    val startSeconds: Double,
    val endSeconds: Double,
    val rawLine: String
) {
    val durationSeconds: Double get() = endSeconds - startSeconds
    val durationMs: Long get() = (durationSeconds * 1000).toLong()
    val startMs: Long get() = (startSeconds * 1000).toLong()
}

data class TimestampParseResult(
    val timestamps: List<ParsedTimestamp>,
    val errors: List<TimestampError>
)

data class TimestampError(
    val lineNumber: Int,
    val rawLine: String,
    val message: String
)
