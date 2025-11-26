package org.usvm.machine.state.messages

/**
 * Used to represent the typical pattern found in the repository (when assert fails, return `null`),
 * but also works with nullable types.
 */
sealed interface Result<out V, out E>

inline fun <V, E> Result<V, E>.getOrReturn(returnExpr: () -> Nothing): V =
    when (this) {
        is Ok -> this.value
        is Err -> returnExpr()
    }

data class Ok<V>(
    val value: V,
) : Result<V, Nothing>

fun <V> V.ok(): Ok<V> = Ok(this)

data class Err<E>(
    val error: E,
) : Result<Nothing, E>

object ScopeDied

val scopeDied = Err(ScopeDied)

typealias ValueOrDeadScope<V> = Result<V, ScopeDied>
