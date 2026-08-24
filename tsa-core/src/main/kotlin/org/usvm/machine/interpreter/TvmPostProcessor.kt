package org.usvm.machine.interpreter

import io.ksmt.expr.KBitVecValue
import io.ksmt.utils.BvUtils.toBigIntegerUnsigned
import io.ksmt.utils.uncheckedCast
import mu.KLogging
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.bitstring.toBitString
import org.ton.cell.Cell
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intblast.TvmBvTransformer
import org.usvm.machine.state.DataSizeInfo
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.state.TvmSignatureCheck
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.hash.DefaultUExprTransformer
import org.usvm.machine.state.hash.TvmConstantHashSymbol
import org.usvm.machine.state.hash.TvmHashConstraintsResolver
import org.usvm.machine.state.hash.TvmSymbolicHashSymbol
import org.usvm.machine.state.hash.calculateConcreteHash
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.calculateConcreteForwardFee
import org.usvm.machine.state.messages.calculateNumberOfBitsInUniqueCells
import org.usvm.machine.state.messages.calculateNumberOfCellRefsInUniqueCells
import org.usvm.machine.state.messages.calculateNumberOfUniqueCells
import org.usvm.machine.state.slicesDataBitsAreEqual
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.wrap
import org.usvm.solver.USatResult
import org.usvm.test.resolver.TvmTestAuthValue
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.test.resolver.endCell
import org.usvm.test.resolver.toTvmCell
import org.usvm.test.resolver.transformTestCellIntoCell
import org.usvm.test.resolver.truncateSliceCell
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.random.Random

class TvmPostProcessor(
    val ctx: TvmContext,
    private val random: Random = Random(0),
) {
    private val signatureKeySize = 32
    private val privateKey by lazy {
        PrivateKeyEd25519(Random(0).nextBytes(signatureKeySize))
    }
    private val publicKey by lazy { privateKey.publicKey() }
    private val publicKeyHex by lazy { publicKey.key.encodeHex() }

    companion object {
        private val logger = object : KLogging() {}.logger
    }

    fun postProcessState(state: TvmState): TvmState? =
        with(ctx) {
            // hack
            val oldIsExceptional = state.isExceptional
            state.isExceptional = false

            val scope =
                TvmStepScopeManager(
                    state,
                    UForkBlackList.createDefault(),
                    allowFailuresOnCurrentStep = true,
                )

            val hashEqualityTransformer = TvmHashConstraintsResolver(scope)
            val newPathConstraints =
                hashEqualityTransformer.generateNewPathConstraints()
                    ?: run {
                        logger.debug("Death in hashEqualityTransformer")
                        return null
                    }
            if (newPathConstraints != state.pathConstraints) {
                if (newPathConstraints.isFalse) {
                    logger.debug("Contradicting constraints after hashEqualityTransformer")
                    return null
                }
                val someCurrentModel = state.models.first()
                val newModel =
                    if (newPathConstraints.tvmConstraintsSequence().all {
                            someCurrentModel.eval(it).isTrue
                        }
                    ) {
                        someCurrentModel
                    } else {
                        val solverResult = solver<TvmType>().check(newPathConstraints)
                        (solverResult as? USatResult)?.model?.wrap(ctx)
                            ?: run {
                                logger.debug {
                                    "Cannot get model after hashEqualityTransformer (solver result: $solverResult)"
                                }
                                return@with null
                            }
                    }

                val newState = state.clone(newPathConstraints)
                newState.models = listOf(newModel)
                newState.isExceptional = oldIsExceptional
                logger.debug("Changing state after solving constraints from hashEqualityTransformer")
                return postProcessState(newState)
            }

            state.isExceptional = oldIsExceptional

            // must be asserted first
            assertConstraints(scope) { resolver ->
                val generateRandomAddressConstraint =
                    generateRandomAddressConstraint(scope)
                        ?: return@assertConstraints null
                generateRandomAddressConstraint
            } ?: run {
                logger.debug("Cannot assert random address constraints")
                return null
            }

            // In some cases, public keys may be included in the hashed values
            assertConstraints(scope) { resolver ->
                generatePublicKeyConstraints(scope, resolver)
            } ?: run {
                logger.debug("Cannot assert public key constraints")
                return null
            }

            postprocessDeferredEvalSymbols(state, scope)
                ?: return null

            // must be asserted separately since it relies on correct hash values
            assertConstraints(scope) { resolver ->
                generateSignatureConstraints(scope, resolver)
            } ?: run {
                logger.debug("Cannot assert signature constraints")
                return null
            }

            val structuralConstraintsHolder = state.structuralConstraintsHolder
            structuralConstraintsHolder.applyTo(scope)
                ?: run {
                    logger.debug("Cannot assert structural constraints")
                    return null
                }

            state.resolvedAuthValues = enumerateAuthValues(state)

            return state
        }

    data class CollectDepRefResult(
        val dependentRefs: List<UHeapRef>,
        val refStructureConstraints: List<UBoolExpr>,
    )

    /**
     * In this section, we say that expression A *depends* on expression B
     * iff we have to fixate the value of B before evaluating/fixating the value of A.
     *
     * The plan is to construct the dependency graph on the deferred evaluation symbols
     * (the symbols that require the computation on the resolved values) and using the said graph separate the symbols into optimal
     * batches such that a batch does not contain dependent symbols.
     *
     * We construct the dependency graph via the intermediate cells:  if `h = hash(c)`, then `h` depends on `c`, and if
     * `c.data` (or `c.refs[0].refs[1].data`) contain symbol s in subexpression, `c` depends on `s`, so, by transitivity,
     * `h` depends on `s`
     *
     */
    private fun postprocessDeferredEvalSymbols(
        state: TvmState,
        scope: TvmStepScopeManager,
    ): Unit? {
        val deferredEvalSymbols = collectDeferredEvalSymbols(state)

        /*
            In this section, we say that expression A *depends* on expression B
            iff we have to fixate the value of B before evaluating/fixating the value of A.

            We want to establish a dependency graph on the deferred evaluation symbols
         */

        lateinit var deferredEvaluationSymbolsToDependentRefs: Map<DeferredEvaluationSymbol, List<UHeapRef>>
        assertConstraints(scope) { resolver ->
            val collectionResults =
                deferredEvalSymbols.associateWith { symbol ->
                    symbol.collectDependentRefsAndCreateReferencesStructureConstraints(resolver)
                }
            deferredEvaluationSymbolsToDependentRefs =
                collectionResults.mapValues { it.value.dependentRefs }
            ctx.mkAnd(collectionResults.values.flatMap { it.refStructureConstraints })
        } ?: return null
        val refsToConsider = deferredEvaluationSymbolsToDependentRefs.values.flatten().toHashSet()

        val refsToDependentSymbols =
            collectDeferredEvalSymbolsDependentOnRefs(scope, deferredEvalSymbols, refsToConsider)
                ?: return null
        val deferredEvalSymbolDependency =
            deferredEvaluationSymbolsToDependentRefs.mapValues { entry ->
                entry.value.flatMap { refsToDependentSymbols.getOrDefault(it, listOf()) }
            }

        val processOrder = splitIntoLayers(deferredEvalSymbols, deferredEvalSymbolDependency)
        for (layer in processOrder) {
            val resolver =
                scope.calcOnState {
                    TvmTestStateResolver(ctx, tvmModels.first(), state)
                }
            val toAssert = mutableListOf<UBoolExpr>()
            for (expr in layer) {
                val constraint =
                    expr.createFixationConstraint(scope, resolver, this)
                        ?: return null
                toAssert.add(constraint)
            }
            scope.assert(ctx.mkAnd(toAssert))
                ?: return null
        }
        return Unit
    }

    /**
     * Takes in a graph and returns the minimal amount of batches that disjointly cover all the nodes and
     * such that there are no edges that belong to a single batch.
     * @param deferredEvalSymbols are the graph nodes
     * @param deferredEvalSymbolDependency are the map from the nodes to its list of neighbors.
     *
     * The function assumes that the graph is acyclic, which is normally the case for dependencies which are based on
     * the relations "A is contained in the data of B"
     */
    private fun splitIntoLayers(
        deferredEvalSymbols: List<DeferredEvaluationSymbol>,
        deferredEvalSymbolDependency: Map<DeferredEvaluationSymbol, List<DeferredEvaluationSymbol>>,
    ): List<HashSet<DeferredEvaluationSymbol>> {
        // TODO: the algorithm here is quadratic, but I am pretty sure that this can be done in linear time
        val result = mutableListOf<HashSet<DeferredEvaluationSymbol>>()
        val prevLayers = hashSetOf<DeferredEvaluationSymbol>()
        val unprocessedSymbols = mutableSetOf<DeferredEvaluationSymbol>()
        unprocessedSymbols.addAll(deferredEvalSymbols)
        var depth = 0
        while (unprocessedSymbols.isNotEmpty()) {
            if (depth > deferredEvalSymbols.size) {
                error("infinite loop")
            }
            depth += 1
            val nextLayer = hashSetOf<DeferredEvaluationSymbol>()
            for (symbol in unprocessedSymbols) {
                val refDeps = deferredEvalSymbolDependency[symbol] ?: hashSetOf()
                if (refDeps.all { prevLayers.contains(it) }) {
                    nextLayer.add(symbol)
                }
            }
            result.add(nextLayer)
            prevLayers.addAll(nextLayer)
            unprocessedSymbols.removeAll(nextLayer)
        }
        return result
    }

    private fun enumerateAuthValues(state: TvmState): AuthAnalysisResult =
        with(ctx) {
            val tsaAccountId =
                state.inputIdToTsaAccountId.values
                    .singleOrNull()
                    ?.symbol
                    ?: return AuthAnalysisResult.NotCollected
            val visitor =
                object : DefaultUExprTransformer(ctx), TvmBvTransformer {
                    var foundTsaAccountId = false

                    override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> {
                        foundTsaAccountId = true
                        return expr
                    }

                    override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> = expr

                    override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> = expr
                }
            state.pathConstraints.tvmConstraintsSequence().forEach { visitor.apply(it) }
            if (visitor.foundTsaAccountId) {
                // we failed to eliminate the equalities with tsaAccountId, so we cannot extract any information
                return AuthAnalysisResult.Unknown
            }

            val scope =
                TvmStepScopeManager(
                    state.clone(),
                    UForkBlackList.Companion.createDefault(),
                    allowFailuresOnCurrentStep = false,
                )
            val values = mutableListOf<TvmTestAuthValue>()
            val modelsCountLimit = ctx.tvmOptions.enumeratingModelsCountLimit
            while (true) {
                val model = scope.calcOnState { tvmModels.first() }
                val isStateInit = model.eval(tsaAccountId.isStateInit).isTrue
                if (isStateInit) {
                    val resolver = TvmTestStateResolver(ctx, model, state)
                    val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
                    val code =
                        resolver.resolveRef(tsaAccountId.code) as? TvmTestCellValue
                            ?: break
                    val codeEqCs =
                        fixator.fixateConcreteValue(scope, tsaAccountId.code)
                            ?: break
                    values.add(TvmTestAuthValue.AuthorizedCode(code))
                    scope.assert(tsaAccountId.isStateInit.not() or (tsaAccountId.isStateInit and codeEqCs.not()))
                        ?: break
                } else {
                    val accountIdValue =
                        (model.eval(tsaAccountId.symbolicAccountId) as KBitVecValue<*>).toBigIntegerUnsigned()
                    values.add(TvmTestAuthValue.AuthorizedOwner(TvmTestIntegerValue(accountIdValue)))
                    val accountIdEqCs =
                        mkEq(
                            tsaAccountId.symbolicAccountId,
                            mkBv(accountIdValue, tsaAccountId.symbolicAccountId.sort.sizeBits),
                        )
                    scope.assert(tsaAccountId.isStateInit or (tsaAccountId.isStateInit.not() and accountIdEqCs.not()))
                        ?: break
                }
                if (values.size > modelsCountLimit) {
                    break
                }
            }
            return AuthAnalysisResult.Collected(values, modelsCountLimit)
        }

    private fun assertConstraints(
        scope: TvmStepScopeManager,
        constraintsBuilder: (TvmTestStateResolver) -> UBoolExpr?,
    ): Unit? {
        val resolver =
            scope.calcOnState {
                TvmTestStateResolver(ctx, tvmModels.first(), this)
            }
        val constraints =
            constraintsBuilder(resolver)
                ?: return null

        return scope.assert(constraints)
    }

    private fun generatePublicKeyConstraints(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr =
        with(ctx) {
            val signatureChecks = scope.calcOnState { signatureChecks }

            signatureChecks.fold(trueExpr as UBoolExpr) { acc, signatureCheck ->
                val curConstraint = fixatePublicKey(signatureCheck, resolver)

                acc and curConstraint
            }
        }

    private fun generateSignatureConstraints(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr =
        with(ctx) {
            val signatureChecks = scope.calcOnState { signatureChecks }

            signatureChecks.fold(trueExpr as UBoolExpr) { acc, signatureCheck ->
                val curConstraint = fixateSignatureCheck(signatureCheck, resolver)

                acc and curConstraint
            }
        }

    private fun generateRandomAddressConstraint(scope: TvmStepScopeManager): UBoolExpr? =
        with(ctx) {
            val addresses = scope.calcOnState { fixatedRandomAddresses }

            addresses.fold(trueExpr as UBoolExpr) { acc, ref ->
                val randomAddress = generateRandomAddressCell()
                val randomConcreteSlice = scope.calcOnState { allocSliceFromCell(randomAddress.toTvmCell()) }

                val curConstraint =
                    scope.slicesDataBitsAreEqual(ref, randomConcreteSlice)
                        ?: return@with null

                acc and curConstraint
            }
        }

    private fun generateRandomAddressCell(): TvmTestDataCellValue {
        val prefix = TvmContext.STD_ADDRESS_TAG + "0".repeat(TvmContext.STD_WORKCHAIN_BITS + 1)
        val mainPart = random.nextBytes(TvmContext.ADDRESS_BITS / 8).toBitString().toBinary()
        val cell = TvmTestDataCellValue(prefix + mainPart)
        return cell
    }

    private fun fixatePublicKey(
        signatureCheck: TvmSignatureCheck,
        resolver: TvmTestStateResolver,
    ): UBoolExpr =
        with(ctx) {
            val concreteKey = mkBvHex(publicKeyHex, int257sort.sizeBits)
            concreteKey eq signatureCheck.publicKey.uncheckedCast()
        }

    @OptIn(ExperimentalStdlibApi::class)
    private fun fixateSignatureCheck(
        signatureCheck: TvmSignatureCheck,
        resolver: TvmTestStateResolver,
    ): UBoolExpr =
        with(ctx) {
            val hash = resolver.resolveInt257(signatureCheck.hash)
            val hashAsByteArray =
                hash.value
                    .toString(16)
                    .padStart(64, '0')
                    .hexToByteArray()
            val signatureHex = privateKey.sign(hashAsByteArray).toHexString()
            val concreteHash = mkBv(hash.value, int257sort)
            val concreteSignature = mkBvHex(signatureHex, signatureCheck.signature.sort.sizeBits)

            val fixateHashCond = concreteHash eq signatureCheck.hash

            val signatureCond =
                if (signatureCheck.checkPassed) {
                    concreteSignature eq signatureCheck.signature
                } else {
                    concreteSignature neq signatureCheck.signature
                }

            return fixateHashCond and signatureCond
        }

    fun fixateValue(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        ref: UHeapRef,
        compareRecursively: Boolean = true,
    ): UBoolExpr? {
        val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
        val fixateValueCond =
            fixator.fixateConcreteValue(scope, ref, compareRecursively)
                ?: return null
        return fixateValueCond
    }

    /**
     * Generate expression that fixates ref's value given by model, and its hash (which is originally a mock).
     * */
    internal fun fixateValueAndHash(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        hash: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val fixateValueCond =
                fixateValue(scope, resolver, ref)
                    ?: return@with null
            val concreteHash = calculateConcreteHash(value)
            val hashCond = hash eq concreteHash.toBv257()
            return fixateValueCond and hashCond
        }

    internal fun fixateValueAndSha256(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        sha256: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val fixateValueCond =
                fixateValue(scope, resolver, ref, compareRecursively = false)
                    ?: return@with null
            val actualSha256 = calculateConcreteSha256(value)
            val sha256Cs = sha256 eq actualSha256
            return fixateValueCond and sha256Cs
        }

    internal fun fixateValueAndDepth(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        ref: UHeapRef,
        depth: UExpr<TvmInt257Sort>,
    ): UBoolExpr? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val fixateValueCond =
                fixateValue(scope, resolver, ref)
                    ?: return@with null
            val concreteDepth = calculateConcreteDepth(value)
            val depthCond = depth eq concreteDepth
            return fixateValueCond and depthCond
        }

    fun fixateValueAndFwdFee(
        scope: TvmStepScopeManager,
        fwdFeeInfo: FwdFeeInfo,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val stateInitValue =
                fwdFeeInfo.stateInitRef?.let {
                    resolver.resolveRef(it) as? TvmTestCellValue
                        ?: error("Unexpected state init value")
                }
            val msgBodyValue =
                fwdFeeInfo.msgBodyRef?.let {
                    resolver.resolveRef(it) as? TvmTestCellValue
                        ?: error("Unexpected msg body value")
                }

            val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
            val fixateStateInitCond =
                if (fwdFeeInfo.stateInitRef != null) {
                    fixator.fixateConcreteValue(scope, fwdFeeInfo.stateInitRef)
                        ?: return@with null
                } else {
                    trueExpr
                }

            val fixateMsgBodyCond =
                if (fwdFeeInfo.msgBodyRef != null) {
                    fixator.fixateConcreteValue(scope, fwdFeeInfo.msgBodyRef)
                        ?: return@with null
                } else {
                    trueExpr
                }

            val concreteFwdFee = calculateConcreteForwardFee(stateInitValue, msgBodyValue)
            val fwdFeeCond = fwdFeeInfo.symbolicFwdFee eq concreteFwdFee.toBv257()

            return fixateStateInitCond and fixateMsgBodyCond and fwdFeeCond
        }

    fun fixateCdatasizeInfo(
        scope: TvmStepScopeManager,
        cdatasizeInfo: DataSizeInfo,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
            val analyzedCellFixationCondition =
                fixator.fixateConcreteValue(scope, cdatasizeInfo.analyzedCell)
                    ?: return@with null
            val analyzedCell =
                cdatasizeInfo.analyzedCell.let {
                    resolver.resolveRef(it) as? TvmTestCellValue
                        ?: error("Unexpected not-cell")
                }
            val restriction = resolver.resolveInt257(cdatasizeInfo.maximumCellCount)

            val cells = listOf(transformTestCellIntoCell(analyzedCell))
            val bits = calculateNumberOfBitsInUniqueCells(cells)
            val uniqueCells = calculateNumberOfUniqueCells(cells)
            val refs = calculateNumberOfCellRefsInUniqueCells(cells)
            val cond =
                if (cdatasizeInfo.hasEnoughMaxCellCount) {
                    if (uniqueCells > restriction.value.toInt()) {
                        ctx.falseExpr
                    } else {
                        mkAnd(
                            cdatasizeInfo.distinctCells eq uniqueCells.toBv257(),
                            cdatasizeInfo.cellRefs eq refs.toBv257(),
                            cdatasizeInfo.dataBits eq bits.toBv257(),
                        )
                    }
                } else {
                    if (uniqueCells > restriction.value.toInt()) {
                        ctx.trueExpr
                    } else {
                        ctx.falseExpr
                    }
                }
            cond and analyzedCellFixationCondition and (cdatasizeInfo.maximumCellCount eq restriction.value.toBv257())
        }

    private fun calculateConcreteSha256(value: TvmTestReferenceValue): UExpr<TvmInt257Sort> =
        with(ctx) {
            when (value) {
                is TvmTestSliceValue -> {
                    val databits = value.cell.data.drop(value.dataPos)
                    val sha256 =
                        run {
                            val bytes = BitString(databits.map { it == '1' }).toByteArray()
                            shaFromBytes(bytes)
                        }
                    with(ctx) { mkBv(sha256, int257sort) }
                }

                is TvmTestCellValue -> {
                    error("Bad type; slice expected")
                }

                is TvmTestBuilderValue -> {
                    val databits = value.data
                    val sha256 =
                        run {
                            val bytes = BitString(databits.map { it == '1' }).toByteArray()
                            shaFromBytes(bytes)
                        }
                    with(ctx) { mkBv(sha256, int257sort) }
                }
            }
        }

    private fun shaFromBytes(bytes: ByteArray): BigInteger =
        BigInteger(
            // signum =
            1,
            // magnitude =
            MessageDigest.getInstance("SHA-256").digest(bytes),
        )

    private fun calculateConcreteDepth(value: TvmTestReferenceValue): UExpr<TvmInt257Sort> =
        with(ctx) {
            when (value) {
                is TvmTestCellValue -> {
                    val cell = transformTestCellIntoCell(value)
                    calculateCellDepth(cell).toBv257()
                }

                is TvmTestSliceValue -> {
                    calculateConcreteDepth(truncateSliceCell(value))
                }

                is TvmTestBuilderValue -> {
                    calculateConcreteDepth(value.endCell())
                }
            }
        }

    private fun calculateCellDepth(cell: Cell): Int {
        if (cell.refs.isEmpty()) {
            return 0
        }

        return 1 + cell.refs.maxOf { calculateCellDepth(it) }
    }
}
