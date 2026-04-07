package org.usvm.machine.types

import io.ksmt.utils.uncheckedCast
import org.ton.bytecode.TvmCodeBlock
import org.usvm.UExpr
import org.usvm.UIndexedMocker
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USort
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmTrackedLiteral
import org.usvm.model.UModelBase

class TvmModel(
    ctx: TvmContext,
    underlyingModel: UModelBase<TvmType>,
) : UModelBase<TvmType>(
        ctx,
        underlyingModel.stack,
        underlyingModel.types,
        TvmMockEvaluator(underlyingModel.mocker),
        underlyingModel.regions,
        underlyingModel.nullRef,
    ) {
    override val mocker: TvmMockEvaluator
        get() =
            super.mocker as? TvmMockEvaluator
                ?: error("mocker must be TvmMockEvaluator")

    fun addCustomValue(
        state: TvmState,
        literal: TvmTrackedLiteral,
        value: UExpr<*>,
    ) {
        val stateMocker =
            state.memory.mocker as? UIndexedMocker<TvmCodeBlock>
                ?: error("mocker must be UIndexedMocker")
        val expr =
            stateMocker.getTrackedExpression(literal) as? UMockSymbol
                ?: error("trackedExpression must be UMockSymbol")
        mocker.customValues[expr] = value
    }
}

fun UModelBase<TvmType>.wrap(ctx: TvmContext) = TvmModel(ctx, this)

class TvmMockEvaluator(
    private val underlyingMockEvaluator: UMockEvaluator,
) : UMockEvaluator {
    val customValues: MutableMap<UMockSymbol<*>, UExpr<*>> = mutableMapOf()

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> =
        customValues[symbol]?.uncheckedCast()
            ?: underlyingMockEvaluator.eval(symbol)
}
