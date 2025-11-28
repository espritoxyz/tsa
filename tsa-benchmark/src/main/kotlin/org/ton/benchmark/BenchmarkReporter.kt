package org.ton.benchmark

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class BenchmarkReporter {
    fun generateComparisonMarkdown(
        leftResults: List<BenchmarkRunResult>,
        rightResults: List<BenchmarkRunResult>,
        printCoverage: Boolean = true,
    ): String {
        val markdownBuilder = StringBuilder()

        val leftMap = leftResults.associateBy { it.name }
        val rightMap = rightResults.associateBy { it.name }

        val allTestNames = (leftMap.keys + rightMap.keys).sorted()

        for (testName in allTestNames) {
            val left = leftMap[testName]
            val right = rightMap[testName]

            markdownBuilder.appendLine("## $testName")
            markdownBuilder.appendLine()

            appendOverallPerformanceTable(markdownBuilder, left, right)
            markdownBuilder.appendLine()

            if (printCoverage) {
                appendMethodsCoverageTable(markdownBuilder, left, right)
                markdownBuilder.appendLine()
            }
        }

        return markdownBuilder.toString()
    }

    private fun appendOverallPerformanceTable(
        builder: StringBuilder,
        left: BenchmarkRunResult?,
        right: BenchmarkRunResult?,
    ) {
        val leftTime = left?.fullTime?.let { formatDuration(it) } ?: "N/A"
        val rightTime = right?.fullTime?.let { formatDuration(it) } ?: "N/A"
        val change =
            if (left != null && right != null && left.fullTime.isPositive()) {
                val diff = right.fullTime.inWholeMilliseconds - left.fullTime.inWholeMilliseconds
                val percentage = (diff.toFloat() / left.fullTime.inWholeMilliseconds) * 100
                "%.2f%%".format(percentage)
            } else if (left?.fullTime == 0.milliseconds && right?.fullTime == 0.milliseconds) {
                "0.00%"
            } else {
                "N/A"
            }

        builder.appendLine("| Metric    | Left       | Right      | Change   |")
        builder.appendLine("|-----------|------------|------------|----------|")
        builder.appendLine("| Full Time | $leftTime | $rightTime | $change |")
    }

    private fun appendMethodsCoverageTable(
        builder: StringBuilder,
        left: BenchmarkRunResult?,
        right: BenchmarkRunResult?,
    ) {
        builder.appendLine("### Method Coverage Comparison")
        builder.appendLine()
        builder.appendLine("| Method ID | Type | Left | Right | Change |")
        builder.appendLine("|-----------|------|------|-------|--------|")

        val allMethodIds = (left?.methodsCoverage?.keys ?: emptySet()) + (right?.methodsCoverage?.keys ?: emptySet())

        for (methodId in allMethodIds.sorted()) {
            val leftCoverage = left?.methodsCoverage?.get(methodId)
            val rightCoverage = right?.methodsCoverage?.get(methodId)

            appendCoverageRow(builder, methodId.toString(), "Coverage", leftCoverage?.coverage, rightCoverage?.coverage)
            appendCoverageRow(
                builder,
                "",
                "Transitive",
                leftCoverage?.transitiveCoverage,
                rightCoverage?.transitiveCoverage,
            )
            appendCoverageRow(
                builder,
                "",
                "Main Method",
                leftCoverage?.coverageOfMainMethod,
                rightCoverage?.coverageOfMainMethod,
            )
        }
    }

    private fun appendCoverageRow(
        builder: StringBuilder,
        methodIdDisplay: String,
        type: String,
        leftVal: Float?,
        rightVal: Float?,
    ) {
        val leftStr = leftVal?.let { "%.2f".format(it) } ?: "N/A"
        val rightStr = rightVal?.let { "%.2f".format(it) } ?: "N/A"
        val change = calculatePercentageChange(leftVal, rightVal)

        builder.appendLine("| $methodIdDisplay | $type | $leftStr | $rightStr | $change |")
    }

    private fun calculatePercentageChange(
        left: Float?,
        right: Float?,
    ): String =
        if (left != null && right != null && left != 0f) {
            val diff = right - left
            val percentage = (diff / left) * 100
            "%.2f%%".format(percentage)
        } else if (left == 0f && right == 0f) {
            "0.00%"
        } else {
            "N/A"
        }

    private fun formatDuration(duration: Duration): String =
        duration.toComponents { minutes, seconds, _ ->
            if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        }
}
