package org.usvm.machine

import java.io.File

/**
 * A sequence of TVM instruction physical locations (cell hash + offset)
 * that uniquely identifies a single execution trace through the analyzed contract.
 *
 * When [TvmOptions.followTrace] is set, the symbolic execution explores only the states
 * whose path is a prefix of (or equal to) [locations].
 */
data class FollowTrace(
    val locations: List<Pair<String, Int>>,
) {
    companion object {
        /**
         * Reads a trace from a file produced by [save].
         * Each non-blank line must contain a hex cell hash and an integer offset separated by whitespace.
         */
        fun load(file: File): FollowTrace {
            val locations =
                file
                    .readLines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.trim().split(WHITESPACE_REGEX)
                        require(parts.size == 2) { "Malformed trace line: \"$line\"" }
                        parts[0] to parts[1].toInt()
                    }
            return FollowTrace(locations)
        }

        /**
         * Serializes [locations] into [file], one location per line in `<cellHashHex> <offset>` format.
         */
        fun save(file: File, locations: List<Pair<String, Int>>) {
            val text = locations.joinToString(separator = "\n") { (hash, offset) -> "$hash $offset" }
            file.writeText(text)
        }

        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
