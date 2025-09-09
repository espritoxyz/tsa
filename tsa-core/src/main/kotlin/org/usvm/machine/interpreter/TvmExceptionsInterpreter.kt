package org.usvm.machine.interpreter

import org.ton.bytecode.TvmExceptionsInst
import org.ton.bytecode.TvmExceptionsThrowInst
import org.ton.bytecode.TvmExceptionsThrowShortInst
import org.ton.bytecode.TvmExceptionsThrowanyInst
import org.ton.bytecode.TvmExceptionsThrowanyifInst
import org.ton.bytecode.TvmExceptionsThrowanyifnotInst
import org.ton.bytecode.TvmExceptionsThrowargInst
import org.ton.bytecode.TvmExceptionsThrowarganyInst
import org.ton.bytecode.TvmExceptionsThrowarganyifInst
import org.ton.bytecode.TvmExceptionsThrowarganyifnotInst
import org.ton.bytecode.TvmExceptionsThrowargifInst
import org.ton.bytecode.TvmExceptionsThrowargifnotInst
import org.ton.bytecode.TvmExceptionsThrowifInst
import org.ton.bytecode.TvmExceptionsThrowifShortInst
import org.ton.bytecode.TvmExceptionsThrowifnotInst
import org.ton.bytecode.TvmExceptionsThrowifnotShortInst
import org.ton.bytecode.TvmExceptionsTryInst
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.C0Register
import org.usvm.machine.state.C2Register
import org.usvm.machine.state.TvmFailureType
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmUserDefinedFailure
import org.usvm.machine.state.consumeConstantGas
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.consumeGas
import org.usvm.machine.state.defineC0
import org.usvm.machine.state.defineC2
import org.usvm.machine.state.extractCurrentContinuation
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.setFailure
import org.usvm.machine.state.takeLastContinuation
import org.usvm.machine.state.takeLastIntOrNull
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.utils.intValueOrNull

class TvmExceptionsInterpreter(
    private val ctx: TvmContext
) {
    fun visitExceptionInst(
        scope: TvmStepScopeManager,
        stmt: TvmExceptionsInst
    ) {
        when (stmt) {
            is TvmExceptionsThrowargInst ->
                scope.doWithState {
                    scope.consumeDefaultGas(stmt)

                    val param = takeLastIntOrNull() ?: return@doWithState
                    throwException(code = stmt.n, param = param)
                }

            is TvmExceptionsThrowShortInst ->
                scope.doWithState {
                    scope.consumeDefaultGas(stmt)

                    throwException(code = stmt.n)
                }

            is TvmExceptionsThrowifInst -> {
                scope.consumeConstantGas(34)

                doThrowIfInst(scope, stmt, EmbeddedCodeExtractor(stmt.n), invertCondition = false)
            }

            is TvmExceptionsThrowifShortInst -> {
                scope.consumeConstantGas(26)

                doThrowIfInst(scope, stmt, EmbeddedCodeExtractor(stmt.n), invertCondition = false)
            }

            is TvmExceptionsThrowifnotInst -> {
                scope.consumeConstantGas(34)

                doThrowIfInst(scope, stmt, EmbeddedCodeExtractor(stmt.n), invertCondition = true)
            }

            is TvmExceptionsThrowifnotShortInst -> {
                scope.consumeConstantGas(26)

                doThrowIfInst(scope, stmt, EmbeddedCodeExtractor(stmt.n), invertCondition = true)
            }

            is TvmExceptionsThrowanyifInst -> {
                scope.consumeConstantGas(26)

                doThrowIfInst(scope, stmt, StackCodeExtractor, invertCondition = false)
            }

            is TvmExceptionsThrowanyifnotInst -> {
                scope.consumeConstantGas(26)

                doThrowIfInst(scope, stmt, StackCodeExtractor, invertCondition = true)
            }

            is TvmExceptionsThrowInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doWithState {
                    throwException(stmt.n)
                }
            }

            is TvmExceptionsThrowanyInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doWithState {
                    val code =
                        takeLastIntOrNull()?.intValueOrNull
                            ?: error("Cannot extract concrete code exception from the stack")

                    throwException(code)
                }
            }

            is TvmExceptionsTryInst -> {
                scope.consumeDefaultGas(stmt)

                val cont =
                    scope.calcOnState { stack.takeLastContinuation() }
                        ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.calcOnState {
                    val registers = registersOfCurrentContract
                    val oldC2 = registers.c2.value
                    val cc = extractCurrentContinuation(stmt, saveC0 = true, saveC1 = true, saveC2 = true)
                    val handler = cont.defineC2(oldC2).defineC0(cc)

                    registers.c0 = C0Register(cc)
                    registers.c2 = C2Register(handler)
                }

                val body =
                    scope.calcOnState { stack.takeLastContinuation() }
                        ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.jumpToContinuation(body)
            }

            is TvmExceptionsThrowarganyInst -> {
                scope.consumeDefaultGas(stmt)
                scope.doWithState {
                    val code =
                        takeLastIntOrNull()?.intValueOrNull
                            ?: error("Cannot extract concrete code exception from the stack")

                    val param = takeLastIntOrNull() ?: error("Cannot extract parameter from the stack")
                    throwException(code, param = param)
                }
            }

            is TvmExceptionsThrowargifnotInst -> {
                scope.consumeConstantGas(34)
                doThrowIfInst(
                    scope,
                    stmt,
                    EmbeddedCodeExtractor(stmt.n),
                    invertCondition = true,
                    takeParameterFromStack = true
                )
            }

            is TvmExceptionsThrowarganyifInst -> {
                scope.consumeConstantGas(26)
                doThrowIfInst(scope, stmt, StackCodeExtractor, invertCondition = false, takeParameterFromStack = true)
            }

            is TvmExceptionsThrowarganyifnotInst -> {
                scope.consumeDefaultGas(stmt)
                doThrowIfInst(scope, stmt, StackCodeExtractor, invertCondition = true)
            }

            is TvmExceptionsThrowargifInst -> {
                scope.consumeConstantGas(34)
                doThrowIfInst(
                    scope,
                    stmt,
                    EmbeddedCodeExtractor(stmt.n),
                    invertCondition = false,
                    takeParameterFromStack = true
                )
            }

            else -> TODO("Unknown stmt: $stmt")
        }
    }

    private fun TvmState.throwException(
        code: Int,
        level: TvmFailureType = TvmFailureType.UnknownError,
        param: UExpr<TvmInt257Sort> = ctx.zeroValue
    ) = ctx.setFailure(TvmUserDefinedFailure(code), level, param, implicitThrow = false)(this)

    private fun doThrowIfInst(
        scope: TvmStepScopeManager,
        stmt: TvmExceptionsInst,
        exceptionCodeExtractor: ExceptionCodeExtractor,
        invertCondition: Boolean,
        takeParameterFromStack: Boolean = false
    ) {
        with(ctx) {
            val flag = scope.takeLastIntOrThrowTypeError() ?: return
            val throwCondition =
                (flag eq zeroValue).let {
                    if (invertCondition) it.not() else it
                }
            val exceptionCode = scope.calcOnState { exceptionCodeExtractor.code(this) }
            val param =
                if (takeParameterFromStack) {
                    scope.calcOnState { takeLastIntOrThrowTypeError() } // what to do on null&
                } else {
                    null
                }
            scope.fork(
                throwCondition,
                falseStateIsExceptional = true,
                blockOnFalseState = {
                    throwException(exceptionCode, param = param ?: ctx.zeroValue)
                    consumeGas(50)
                }
            ) ?: return

            scope.doWithState { newStmt(stmt.nextStmt()) }
        }
    }

    private sealed interface ExceptionCodeExtractor {
        fun code(state: TvmState): Int
    }

    private data class EmbeddedCodeExtractor(
        val code: Int
    ) : ExceptionCodeExtractor {
        override fun code(state: TvmState): Int = code
    }

    private data object StackCodeExtractor : ExceptionCodeExtractor {
        override fun code(state: TvmState): Int =
            state.takeLastIntOrNull()?.intValueOrNull
                ?: error("Cannot extract concrete code exception from the stack")
    }
}
