package com.copilka.app.engine

/** Один готовый файл результата. */
data class OutputArtifact(
    val path: String,
    val name: String,
    val bytes: Long,
)

/** Результат конвертации: файлы, размеры, статистика. */
data class ConversionResult(
    val requestId: String,
    val tool: ToolKind,
    val outputFormat: String,
    val artifacts: List<OutputArtifact>,
    val inputBytes: Long,
    val durationMillis: Long,
) {
    val outputBytes: Long get() = artifacts.sumOf { it.bytes }
    val fileCount: Int get() = artifacts.size
    val primary: OutputArtifact? get() = artifacts.firstOrNull()

    /** Сколько байт удалось сэкономить (может быть отрицательным, если файл вырос). */
    val savedBytes: Long get() = inputBytes - outputBytes
}
