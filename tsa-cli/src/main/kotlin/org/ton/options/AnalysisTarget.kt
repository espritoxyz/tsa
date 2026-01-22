package org.ton.options

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.help
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.ton.bigint.BigInt
import java.math.BigInteger
import java.nio.file.Path
import kotlin.system.exitProcess

sealed interface AnalysisTarget

data object AllMethods : AnalysisTarget

data object Receivers : AnalysisTarget

data class SpecificMethod(
    val methodId: Int,
) : AnalysisTarget

fun CliktCommand.analysisTargetOption() =
    mutuallyExclusiveOptions(
        option("--method").int().help("Id of the method to analyze").convert { SpecificMethod(it) },
        option(
            "--analyze-receivers",
        ).flag().help("Analyze recv_internal and recv_external (default)").convert { Receivers },
        option(
            "--analyze-all-methods",
        ).flag().help("Analyze all methods (applicable only for contracts with default main method)").convert {
            AllMethods
        },
    ).single()
        .default(Receivers)
        .help(
            "Analysis target",
            "What to analyze. By default, only receivers (recv_interval and recv_external) are analyzed.",
        )

@JvmInline
value class NullablePath(
    val path: Path?,
)

sealed interface CustomOption<out T> {
    sealed interface Value<out T> : CustomOption<T>

    object None : Value<Nothing>

    data class Parsed<T>(
        val value: T,
    ) : Value<T>

    data class Unparsed(
        val original: String,
    ) : CustomOption<Nothing>
}

typealias BalanceOption = CustomOption<BigInt>
typealias StringOption = CustomOption<String>

private fun <T> String.parseSafely(block: String.() -> CustomOption<T>): CustomOption<T> =
    try {
        block()
    } catch (_: Exception) {
        CustomOption.Unparsed(this)
    }

fun String.parseBalance(): BalanceOption =
    if (this == "-") {
        CustomOption.None
    } else {
        when (this.toBigIntegerOrNull()) {
            is BigInteger -> CustomOption.Parsed(this.toBigInteger())
            else -> CustomOption.Unparsed(this)
        }
    }

fun String.parseAddress(): StringOption =
    parseSafely {
        if (this == "-") {
            CustomOption.None
        } else {
            val parts = split(":")
            if (parts.size != 2) {
                CustomOption.Unparsed(this)
            } else {
                val (workchainId, addressBits) = parts
                if (workchainId.toInt() != 0) {
                    CustomOption.Unparsed(this)
                } else {
                    val hexPart = addressBits
                    CustomOption.Parsed("1" + "0".repeat(10) + BigInteger(hexPart, 16).toString(2).padStart(256, '0'))
                }
            }
        }
    }

fun <T> CustomOption.Value<T>.get(): T? =
    when (this) {
        is CustomOption.Parsed<T> -> this.value
        CustomOption.None -> null
    }

fun <T> List<CustomOption<T>>.validateData(dataKind: String): List<CustomOption.Value<T>> =
    mapNotNull {
        when (it) {
            is CustomOption.Value -> {
                it
            }

            is CustomOption.Unparsed -> {
                System.err.println("Failed to parse property of kind $dataKind of value '${it.original}'")
                exitProcess(-1)
            }
        }
    }
