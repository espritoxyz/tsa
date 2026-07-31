package org.usvm.machine.interpreter

import io.ksmt.expr.KBvZeroExtensionExpr
import org.ton.TvmParameterInfo
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTrackedSymbol
import org.usvm.isFalse
import org.usvm.isStatic
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.intblast.TvmBvTransformer
import org.usvm.machine.interpreter.TvmPostProcessor.CollectDepRefResult
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.assertType
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.state.hash.HashCollector
import org.usvm.machine.state.hash.TvmConstantHashSymbol
import org.usvm.machine.state.hash.TvmSymbolicHashSymbol
import org.usvm.machine.state.preloadDataBitsFromCellWithoutChecks
import org.usvm.machine.state.readCellData
import org.usvm.machine.state.readCellDataLength
import org.usvm.machine.state.readCellRef
import org.usvm.machine.state.readCellRefsCount
import org.usvm.machine.state.readSliceCell
import org.usvm.machine.state.readSliceDataPos
import org.usvm.machine.tctx
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.asCellRef
import org.usvm.machine.types.getPossibleTypes
import org.usvm.machine.types.memory.readInModelFromTlbFields
import org.usvm.mkSizeExpr
import org.usvm.solver.UExprTranslator
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.utils.flattenReferenceIte

fun List<CollectDepRefResult>.combine(): CollectDepRefResult =
    CollectDepRefResult(
        dependentRefs = flatMap { it.dependentRefs },
        refStructureConstraints = flatMap { it.refStructureConstraints },
    )

fun collectReachableCellsAndCreateRefStructureConstraints(
    resolver: TvmTestStateResolver,
    cellRef: UHeapRef,
): CollectDepRefResult {
    val ctx = resolver.state.ctx
    val state = resolver.state
    val flattenedInitial = cellRef.listLeaves()
    val result = hashSetOf<UHeapRef>(*flattenedInitial.toTypedArray())
    val visitingQueue = mutableListOf<UConcreteHeapRef>(*flattenedInitial.toTypedArray())
    val constraints = mutableListOf<UBoolExpr>()
    val visited = hashSetOf<UExpr<*>>()
    while (visitingQueue.isNotEmpty()) {
        val front = visitingQueue.removeAt(0)
        if (front in visited) continue
        visited.add(front)
        val possibleTypes = state.getPossibleTypes(front).toList()
        val actualType =
            if (possibleTypes.toSet() == setOf(TvmDataCellType, TvmDictCellType)) {
                // such an ambiguity in the postprocess means that the cell was not used in reads whatsoever, so we are free to assume
                // that it is, in fact, a cell
                state.assertType(front, TvmCellType)
                TvmDataCellType
            } else {
                possibleTypes.single()
            }

        if (actualType == TvmSliceType) {
            // here we overapproximate the actual number of cells to pin the form
            // (by ignore the dataPos field of the slice),
            // possibly lowering the completeness of an analysis
            state.readSliceCell(front).listLeaves().forEach { cell ->
                if (result.add(cell)) {
                    visitingQueue.add(cell)
                }
            }
            continue
        }
        if (actualType == TvmDictCellType) {
            // TODO: properly iterate over all the entries, probably ignoring the guards
            continue
        }
        check(
            actualType == TvmCellType || actualType == TvmDataCellType || actualType == TvmBuilderType,
        ) { "Unreachable" }

        val refCount = state.readCellRefsCount(front.asCellRef())
        val concreteRefCount =
            if (front.isStatic && front !in resolver.constraintVisitor.refs) {
                // does not occur in path constraints -> empty cell
                constraints.add(with(ctx) { refCount eq mkSizeExpr(0) })
                val dataBits = state.readCellDataLength(front.asCellRef())
                constraints.add(with(ctx) { dataBits eq mkSizeExpr(0) })
                val isExotic = state.fieldManagers.cellExoticFieldManager.readCellIsExotic(state, front)
                constraints.add(ctx.mkNot(isExotic))
                0
            } else {
                val modeledRefCount = resolver.eval(refCount)
                constraints.add(with(ctx) { refCount eq modeledRefCount })
                modeledRefCount.intValue()
            }
        if (concreteRefCount != 0) {
            for (i in 0 until concreteRefCount) {
                val nextChild = state.readCellRef(front, ctx.mkSizeExpr(i))
                for (leaf in nextChild.listLeaves()) {
                    if (result.add(leaf)) {
                        visitingQueue.add(leaf)
                    }
                }
            }
        }
    }
    return CollectDepRefResult(result.toList(), constraints)
}

fun UHeapRef.listLeaves(): List<UConcreteHeapRef> =
    with(tctx) {
        flattenReferenceIte(
            this@listLeaves,
            extractAllocated = true,
            extractStatic = true,
        )
    }.filter { !it.first.isFalse }.map { it.second }

fun collectDeferredEvalSymbolsDependentOnRefs(
    scope: TvmStepScopeManager,
    deferredEvalSymbols: List<DeferredEvaluationSymbol>,
    refsToConsider: HashSet<UHeapRef>,
): Map<UHeapRef, List<DeferredEvaluationSymbol>>? {
    val interestingSymbolVisitor =
        object : TvmBvTransformer, UExprTranslator<TvmType, TvmSizeSort>(scope.ctx.tctx()) {
            val found = hashSetOf<UExpr<*>>()

            override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>): UExpr<Sort> {
                if (deferredEvalSymbols.any { it.isConnectedTo(expr) }) {
                    found.add(expr)
                }
                return super<UExprTranslator>.transform(expr)
            }

            override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> {
                found.add(expr)
                return expr
            }

            override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> {
                found.add(expr)
                return expr
            }

            override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> {
                found.add(expr)
                return expr
            }
        }
    val refsToDependentSymbols = mutableMapOf<UHeapRef, List<DeferredEvaluationSymbol>>()
    for (ref in refsToConsider) {
        interestingSymbolVisitor.found.clear()
        //  TODO: maybe reuse TLb somehow?
        val possibleTypes =
            scope.calcOnState { getPossibleTypes(ref as UConcreteHeapRef).toSet() }
        if (possibleTypes == setOf(TvmDataCellType, TvmDictCellType)) {
            // such an ambiguity in the postprocess means that the cell was not used in reads whatsoever, so we are free to assume
            // that it is, in fact, a cell
            scope.calcOnState { assertType(ref, TvmCellType) }
        }

        val dataParts: List<UExpr<*>>? =
            when (possibleTypes) {
                setOf(TvmSliceType) -> {
                    scope.calcOnState {
                        val exprs = mutableListOf<UExpr<*>>()
                        val dataLeft = getSliceRemainingBitsCount(ref)
                        val dataPosition = readSliceDataPos(ref)
                        for (sliceConcreteRef in ref.listLeaves()) {
                            val state = this
                            val labelMapper = state.dataCellInfoStorage.sliceMapper
                            val stack =
                                labelMapper.getTlbStack(sliceConcreteRef)
                            if (stack == null) {
                                val cell = readSliceCell(sliceConcreteRef)
                                exprs.add(
                                    scope.preloadDataBitsFromCellWithoutChecks(cell, dataPosition, dataLeft)
                                        ?: return@calcOnState null,
                                )
                                continue
                            }
                            val cellRef = state.readSliceCell(sliceConcreteRef)

                            val resolver = TvmTestStateResolver(ctx, tvmModels.first(), this)
                            val sizeSymbolic =
                                state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(
                                    state,
                                    cellRef,
                                )
                            val position = state.readSliceDataPos(sliceConcreteRef)
                            val readCount = with(ctx) { sizeSymbolic bvSub position }
                            // note: we ignore missing slices, because they occur in `Tlb*ByRef`, which we process
                            // in children separately.
                            val (valueFromTlbFields, guard, _, symbolicExprs) =
                                readInModelFromTlbFields(
                                    cellRef,
                                    resolver,
                                    stack,
                                    readCount,
                                )
                            exprs.addAll(symbolicExprs)
                            exprs.add(guard)
                        }
                        // fallback for no-tlb case
                        if (exprs.isEmpty()) {
                            val dataLeft = getSliceRemainingBitsCount(ref)
                            val dataPosition = readSliceDataPos(ref)
                            val cell = readSliceCell(ref)
                            exprs.add(
                                scope.preloadDataBitsFromCellWithoutChecks(cell, dataPosition, dataLeft)
                                    ?: return@calcOnState null,
                            )
                        }
                        exprs
                    }
                }

                setOf(TvmCellType), setOf(TvmDataCellType), setOf(TvmBuilderType),
                setOf(TvmDataCellType, TvmDictCellType),
                -> {
                    val exprs = mutableListOf<UExpr<*>>()
                    for (concreteRef in ref.listLeaves()) {
                        val state = scope.calcOnState { this }
                        val labelMapper = state.dataCellInfoStorage.mapper
                        val possibleLabels =
                            labelMapper
                                .getLabelInfo(concreteRef)
                                ?.variants
                                ?.mapNotNull { it.key as? TvmParameterInfo.DataCellInfo }
                                ?: emptyList()

                        for (label in possibleLabels) {
                            val (valueFromTlbFields, guard, _, symbolicExprs) =
                                readInModelFromTlbFields(
                                    concreteRef,
                                    TvmTestStateResolver(scope.ctx, state.tvmModels.first(), state),
                                    label.dataCellStructure,
                                )
                            exprs.addAll(symbolicExprs)
                        }

                        if (exprs.isEmpty()) {
                            val data =
                                scope.readCellData(ref)
                                    ?: return null
                            exprs.add(data)
                        }
                    }
                    exprs
                }

                setOf(TvmDictCellType) -> {
                    // TODO: properly collect the dependent symbols on the dictionaries
                    continue
                }

                else -> {
                    error("Unsupported type in postprocessing: $possibleTypes")
                }
            }

        dataParts ?: continue
        dataParts.forEach { interestingSymbolVisitor.apply(it) }
        val foundSymbols = interestingSymbolVisitor.found
        refsToDependentSymbols[ref] =
            deferredEvalSymbols.filter { it.isConnectedToAny(foundSymbols) }
    }
    return refsToDependentSymbols
}

fun collectDeferredEvalSymbols(state: TvmState): List<DeferredEvaluationSymbol> {
    val ctx = state.ctx
    val deferredEvalSymbols = mutableListOf<DeferredEvaluationSymbol>()
    for ((ref, depth) in state.refToDepth) {
        val depthSymbol =
            (depth as? KBvZeroExtensionExpr)?.value
                ?: error("Expected zero-extension symbol, got $depth")
        deferredEvalSymbols.add(
            DepthSymbol(
                depthSymbol,
                listOf(ctx.mkConcreteHeapRef(ref)),
                depth,
            ),
        )
    }
    for (datasizeInfo in state.cdatasizeInfos) {
        deferredEvalSymbols.add(
            CDataSizeSymbol(
                listOf(
                    datasizeInfo.distinctCells,
                    datasizeInfo.cellRefs,
                    datasizeInfo.dataBits,
                ).map {
                    (it as? KBvZeroExtensionExpr)?.value
                        ?: error("expected zero-extension symbol, got $it")
                },
                listOf(datasizeInfo.analyzedCell),
                datasizeInfo,
            ),
        )
    }
    for ((ref, sha256) in state.refToSha256) {
        val symbol =
            (sha256 as? KBvZeroExtensionExpr)?.value
                ?: error("Expected zero-extension symbol, got $sha256")
        deferredEvalSymbols.add(
            Sha256Symbol(symbol, listOf(ctx.mkConcreteHeapRef(ref)), sha256),
        )
    }
    for (fwdFeeInfo in state.forwardFees) {
        val symbol =
            (fwdFeeInfo.symbolicFwdFee as? KBvZeroExtensionExpr)?.value
                ?: error("Expected zero-extension symbol, got ${fwdFeeInfo.symbolicFwdFee}")
        deferredEvalSymbols.add(
            FwdFeeSymbol(
                symbol,
                listOfNotNull(fwdFeeInfo.stateInitRef, fwdFeeInfo.msgBodyRef),
                fwdFeeInfo,
            ),
        )
    }
    val hashCollector = HashCollector(ctx)
    state.pathConstraints.tvmConstraintsSequence().forEach { hashCollector.apply(it) }
    /*
    note (metametamoon): We might miss some of the hashes values that are created in
    path constraints during the handling of the deferred evaluation symbols.
    However, we would like to avoid fixating the hashes if there is no need for such fixation
    to make the analysis more complete (even though we probably might not need this with such a smart
    default evaluation symbol postprocessing), so we really don't want to include the unwanted hashes here.
    If the debugging showed that the problems is here (e.g. by looking at UNSAT cores), one should consider
     **over**-approximating the number of possibly fixated hashes here.
     */
    state.signatureChecks.forEach { hashCollector.apply(it.hash) }
    for ((ref, hash) in state.refToHash) {
        if (hash in hashCollector.collectedHashes) {
            deferredEvalSymbols.add(
                HashSymbol(hash, listOf(ctx.mkConcreteHeapRef(ref))),
            )
        }
    }
    return deferredEvalSymbols
}
