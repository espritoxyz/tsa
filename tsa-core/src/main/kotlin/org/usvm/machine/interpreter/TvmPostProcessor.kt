package org.usvm.machine.interpreter

import io.ksmt.expr.KBitVecValue
import io.ksmt.utils.BvUtils.toBigIntegerUnsigned
import io.ksmt.utils.uncheckedCast
import kotlinx.serialization.Serializable
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
import org.usvm.machine.state.DataSizeInfo
import org.usvm.machine.state.TsaAccountId
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmSignatureCheck
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.hash.HashCollector
import org.usvm.machine.state.hash.TvmDefaultTransformer
import org.usvm.machine.state.hash.TvmHashConstraintsResolver
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.calculateConcreteForwardFee
import org.usvm.machine.state.messages.calculateNumberOfBitsInUniqueCells
import org.usvm.machine.state.messages.calculateNumberOfCellRefsInUniqueCells
import org.usvm.machine.state.messages.calculateNumberOfUniqueCells
import org.usvm.machine.types.TvmModel
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.wrap
import org.usvm.solver.USatResult
import org.usvm.test.resolver.TvmTestAuthValue
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.test.resolver.endCell
import org.usvm.test.resolver.transformTestCellIntoCell
import org.usvm.test.resolver.transformTestDataCellIntoCell
import org.usvm.test.resolver.transformTestDictCellIntoCell
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
                    ?: return null
            if (newPathConstraints != state.pathConstraints) {
                if (newPathConstraints.isFalse) {
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
                            ?: return@with null
                    }

                val newState = state.clone(newPathConstraints)
                newState.models = listOf(newModel)
                newState.isExceptional = oldIsExceptional
                return postProcessState(newState)
            }

            state.isExceptional = oldIsExceptional

            // must be asserted first
            assertConstraints(scope) { resolver ->
                generateRandomAddressConstraint(scope, resolver)
                    ?: return@assertConstraints null
            } ?: return null

            // In some cases, public keys may be included in the hashed values
            assertConstraints(scope) { resolver ->
                generatePublicKeyConstraints(scope, resolver)
            } ?: return null

            // forward fees might depend on the hashes, so we must fixate the hashes first
            assertConstraints(scope) { resolver ->
                val hashConstraint =
                    generateHashConstraint(scope, resolver)
                        ?: return@assertConstraints null
                hashConstraint
            } ?: return null

            assertConstraints(scope) { resolver ->
                val sha256Constraint =
                    generateSha256Constraints(scope, resolver)
                        ?: return@assertConstraints null
                sha256Constraint
            } ?: return null

            assertConstraints(scope) { resolver ->
                val depthConstraint =
                    generateDepthConstraint(scope, resolver)
                        ?: return@assertConstraints null

                val fwdFeeConstraint =
                    generateFwdFeeConstraints(scope, resolver)
                        ?: return@assertConstraints null

                val datasizeConstraint =
                    generateDatasizeConstraints(scope, resolver)
                        ?: return@assertConstraints null

                depthConstraint and fwdFeeConstraint and datasizeConstraint
            } ?: return null

            // must be asserted separately since it relies on correct hash values
            assertConstraints(scope) { resolver ->
                generateSignatureConstraints(scope, resolver)
            } ?: return null

            val structuralConstraintsHolder = state.structuralConstraintsHolder
            structuralConstraintsHolder.applyTo(scope)
                ?: return null

            state.resolvedAuthValues = enumerateAuthValues(state)

            return state
        }

    private fun enumerateAuthValues(state: TvmState): AuthAnalysisResult =
        with(ctx) {
            val tsaAccountId =
                state.inputIdToTsaAccountId.values
                    .singleOrNull()
                    ?.symbol
                    ?: return AuthAnalysisResult.NotCollected
            val result = state.result
            if (result !is TvmResult.TvmFailure || result.exit.exitCode != 1000) {
                return AuthAnalysisResult.NotCollected
            }

            val visitor =
                object : TvmDefaultTransformer(ctx) {
                    var foundTsaAccountId = false

                    override fun transform(expr: TsaAccountId): UExpr<UBvSort> {
                        foundTsaAccountId = true
                        return expr
                    }
                }
            state.pathConstraints.tvmConstraintsSequence().forEach { visitor.apply(it) }
            if (visitor.foundTsaAccountId) {
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
                val model = scope.calcOnState { this.models.first() }
                val isStateInit = model.eval(tsaAccountId.isStateInit).isTrue
                if (isStateInit) {
                    val resolver = TvmTestStateResolver(ctx, model as TvmModel, state)
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
            return AuthAnalysisResult.Collected(values)
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

    private fun generateFwdFeeConstraints(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val forwardFees = scope.calcOnState { forwardFees }

            forwardFees.fold(trueExpr as UBoolExpr) { acc, fwdFeeInfo ->
                val curConstraint =
                    fixateValueAndFwdFee(scope, fwdFeeInfo, resolver)
                        ?: return@with null

                acc and curConstraint
            }
        }

    private fun generateDatasizeConstraints(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val datasizeInfos = scope.calcOnState { cdatasizeInfos }

            mkAnd(datasizeInfos.map { fixateCdatasizeInfo(scope, it, resolver) ?: return@with null })
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

    private fun generateDepthConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val addressToDepth = scope.calcOnState { refToDepth }

            addressToDepth.entries.fold(trueExpr as UBoolExpr) { acc, (ref, depth) ->
                val curConstraint =
                    fixateValueAndDepth(scope, mkConcreteHeapRef(ref), depth, resolver)
                        ?: return@with null
                acc and curConstraint
            }
        }

    private fun generateSha256Constraints(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val refToSha256 = scope.calcOnState { refToSha256 }

            refToSha256.entries.fold(trueExpr as UBoolExpr) { acc, (ref, depth) ->
                val curConstraint =
                    fixateValueAndSha256(scope, mkConcreteHeapRef(ref), depth, resolver)
                        ?: return@with null
                acc and curConstraint
            }
        }

    private fun generateHashConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val addressToHash = scope.calcOnState { refToHash }
            val hashCollector = HashCollector(ctx)
            scope.calcOnState {
                pathConstraints.tvmConstraintsSequence().forEach { hashCollector.apply(it) }
                signatureChecks.forEach { hashCollector.apply(it.hash) }
            }
            addressToHash.entries.fold(trueExpr as UBoolExpr) { acc, (ref, hash) ->
                val isHashInCs = hash in hashCollector.collectedHashes
                val curConstraint =
                    if (isHashInCs) {
                        val result =
                            fixateValueAndHash(
                                scope,
                                mkConcreteHeapRef(ref),
                                hash.zeroExtendToSort(int257sort),
                                resolver,
                            )
                                ?: return null
                        scope.calcOnState { fixatedHashes = fixatedHashes.add(hash) }
                        result
                    } else {
                        ctx.trueExpr
                    }
                acc and curConstraint
            }
        }

    private fun generateRandomAddressConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val addresses = scope.calcOnState { fixatedRandomAddresses }

            addresses.fold(trueExpr as UBoolExpr) { acc, ref ->
                val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
                val randomAddress = generateRandomAddress()

                val curConstraint =
                    fixator.fixateConcreteValueForSlice(scope, ref, randomAddress)
                        ?: return@with null

                acc and curConstraint
            }
        }

    private fun generateRandomAddress(): TvmTestSliceValue {
        val prefix = TvmContext.STD_ADDRESS_TAG + "0".repeat(TvmContext.STD_WORKCHAIN_BITS + 1)
        val mainPart = random.nextBytes(TvmContext.ADDRESS_BITS / 8).toBitString().toBinary()
        return TvmTestSliceValue(cell = TvmTestDataCellValue(prefix + mainPart))
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
    private fun fixateValueAndHash(
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

    private fun fixateValueAndSha256(
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

    private fun fixateValueAndDepth(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        depth: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver,
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

    private fun fixateValueAndFwdFee(
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

    private fun fixateCdatasizeInfo(
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

fun calculateConcreteHash(value: TvmTestReferenceValue): BigInteger =
    when (value) {
        is TvmTestDataCellValue -> {
            val cell = transformTestDataCellIntoCell(value)
            calculateHashOfCell(cell)
        }

        is TvmTestDictCellValue -> {
            val cell = transformTestDictCellIntoCell(value)
            calculateHashOfCell(cell)
        }

        is TvmTestBuilderValue -> {
            val cell = transformTestDataCellIntoCell(value.toCell())
            calculateHashOfCell(cell)
        }

        is TvmTestSliceValue -> {
            val restCell = truncateSliceCell(value)
            calculateConcreteHash(restCell)
        }
    }

fun calculateHashOfCell(cell: Cell): BigInteger = BigInteger(ByteArray(1) { 0 } + cell.hash().toByteArray())

@Serializable
sealed interface AuthAnalysisResult {
    data object Unknown : AuthAnalysisResult

    data object NotCollected : AuthAnalysisResult

    data class Collected(
        val authorizedEntities: List<TvmTestAuthValue>,
    ) : AuthAnalysisResult
}
