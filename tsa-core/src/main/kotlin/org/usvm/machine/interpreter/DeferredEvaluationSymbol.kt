package org.usvm.machine.interpreter

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.Int257Expr
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.interpreter.TvmPostProcessor.CollectDepRefResult
import org.usvm.machine.state.DataSizeInfo
import org.usvm.machine.state.hash.TvmHashSymbol
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.tctx
import org.usvm.test.resolver.TvmTestStateResolver

sealed interface DeferredEvaluationSymbol {
    val symbol: UExpr<*>
    val args: List<UHeapRef>

    fun collectDependentRefsAndCreateReferencesStructureConstraints(
        resolver: TvmTestStateResolver,
    ): CollectDepRefResult

    fun isConnectedTo(symbol: UExpr<*>): Boolean = this.symbol == symbol

    fun isConnectedToAny(symbols: Set<UExpr<*>>): Boolean = symbol in symbols

    fun createFixationConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        postprocessor: TvmPostProcessor,
    ): UBoolExpr?
}

class DepthSymbol(
    override val symbol: UExpr<*>,
    override val args: List<UHeapRef>,
    val depth: Int257Expr,
) : DeferredEvaluationSymbol {
    override fun collectDependentRefsAndCreateReferencesStructureConstraints(
        resolver: TvmTestStateResolver,
    ): CollectDepRefResult = args.map { collectReachableCellsAndCreateRefStructureConstraints(resolver, it) }.combine()

    override fun createFixationConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        postprocessor: TvmPostProcessor,
    ): UBoolExpr? =
        postprocessor.fixateValueAndDepth(
            scope,
            resolver,
            args.singleOrNull() as? UConcreteHeapRef
                ?: error("Expected UConcreteHeapRef, got ${args.singleOrNull()}"),
            depth,
        )
}

class Sha256Symbol(
    override val symbol: UExpr<*>,
    override val args: List<UHeapRef>,
    val sha256: Int257Expr,
) : DeferredEvaluationSymbol {
    // we do not go recursively to children here, as sha256 is taken from the data string
    override fun collectDependentRefsAndCreateReferencesStructureConstraints(
        resolver: TvmTestStateResolver,
    ): CollectDepRefResult = CollectDepRefResult(args.flatMap { it.listLeaves() }, emptyList())

    override fun createFixationConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        postprocessor: TvmPostProcessor,
    ): UBoolExpr? = postprocessor.fixateValueAndSha256(scope, args.single(), sha256, resolver)
}

class HashSymbol(
    override val symbol: TvmHashSymbol,
    override val args: List<UHeapRef>,
) : DeferredEvaluationSymbol {
    override fun collectDependentRefsAndCreateReferencesStructureConstraints(
        resolver: TvmTestStateResolver,
    ): CollectDepRefResult = args.map { collectReachableCellsAndCreateRefStructureConstraints(resolver, it) }.combine()

    override fun createFixationConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        postprocessor: TvmPostProcessor,
    ): UBoolExpr? {
        val constraint =
            postprocessor.fixateValueAndHash(
                scope,
                args.single(),
                with(scope.ctx) { symbol.zeroExtendToSort(int257sort) },
                resolver,
            ) ?: return null
        scope.calcOnState { fixatedHashes = fixatedHashes.add(symbol) }
        return constraint
    }
}

class FwdFeeSymbol(
    override val symbol: UExpr<*>,
    override val args: List<UHeapRef>,
    val fwdFeeInfo: FwdFeeInfo,
) : DeferredEvaluationSymbol {
    override fun collectDependentRefsAndCreateReferencesStructureConstraints(
        resolver: TvmTestStateResolver,
    ): CollectDepRefResult = args.map { collectReachableCellsAndCreateRefStructureConstraints(resolver, it) }.combine()

    override fun createFixationConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        postprocessor: TvmPostProcessor,
    ): UBoolExpr? = postprocessor.fixateValueAndFwdFee(scope, fwdFeeInfo, resolver)
}

class CDataSizeSymbol(
    val connectedSymbols: List<UExpr<*>>,
    override val args: List<UHeapRef>,
    val cdatasizeInfo: DataSizeInfo,
) : DeferredEvaluationSymbol {
    override val symbol: UExpr<*>
        get() = args.first().tctx.nullValue

    override fun collectDependentRefsAndCreateReferencesStructureConstraints(
        resolver: TvmTestStateResolver,
    ): CollectDepRefResult = args.map { collectReachableCellsAndCreateRefStructureConstraints(resolver, it) }.combine()

    override fun isConnectedTo(symbol: UExpr<*>): Boolean = symbol in connectedSymbols

    override fun isConnectedToAny(symbols: Set<UExpr<*>>): Boolean = connectedSymbols.any { it in symbols }

    override fun createFixationConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        postprocessor: TvmPostProcessor,
    ): UBoolExpr? = postprocessor.fixateCdatasizeInfo(scope, cdatasizeInfo, resolver)
}
