package org.usvm.machine.interpreter

import io.ksmt.utils.uncheckedCast
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmFixationMemoryValues
import org.usvm.machine.state.TvmSignatureCheck
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.initializeConcreteDictKeys
import org.usvm.machine.types.TvmType
import org.usvm.solver.USatResult
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.test.resolver.endCell
import org.usvm.test.resolver.transformTestCellIntoCell
import org.usvm.test.resolver.transformTestDataCellIntoCell
import org.usvm.test.resolver.transformTestDictCellIntoCell
import org.usvm.test.resolver.truncateSliceCell
import java.math.BigInteger
import kotlin.random.Random

class TvmPostProcessor(
    val ctx: TvmContext,
) {
    private val signatureKeySize = 32
    private val privateKey by lazy {
        PrivateKeyEd25519(Random(0).nextBytes(signatureKeySize))
    }
    private val publicKey by lazy { privateKey.publicKey() }
    private val publicKeyHex by lazy { publicKey.key.encodeHex() }

    fun postProcessState(state: TvmState): TvmState? =
        with(ctx) {
            var result =
                assertConstraints(state) { resolver ->
                    val scope =
                        TvmStepScopeManager(
                            state,
                            forkBlackList = UForkBlackList.createDefault(),
                            allowFailuresOnCurrentStep = true,
                        )

                    val hashConstraint =
                        generateHashConstraint(scope, resolver)
                            ?: return@assertConstraints null

                    val depthConstraint =
                        generateDepthConstraint(scope, resolver)
                            ?: return@assertConstraints null

                    val fixValues = hashConstraint.second.union(depthConstraint.second)

                    scope.checkAliveAndNoForks()

                    (hashConstraint.first and depthConstraint.first) to fixValues
                } ?: return null

            // must be asserted separately since it relies on correct hash values
            result = assertConstraints(result) { resolver ->
                val scope =
                    TvmStepScopeManager(
                        state,
                        forkBlackList = UForkBlackList.createDefault(),
                        allowFailuresOnCurrentStep = true,
                    )

                (generateSignatureConstraints(scope, resolver) to null).also {
                    scope.checkAliveAndNoForks()
                }
            } ?: return null

            result
        }

    private inline fun assertConstraints(
        state: TvmState,
        constraintsBuilder: (TvmTestStateResolver) -> Pair<UBoolExpr, TvmFixationMemoryValues?>?,
    ): TvmState? {
        val resolver = TvmTestStateResolver(ctx, state.models.first(), state.pathConstraints.composers, state)
        val (guard, fixValues) =
            constraintsBuilder(resolver)
                ?: return null

        val newState =
            if (fixValues != null && !fixValues.isEmpty()) {
                val newConstraints = state.pathConstraints.addFixationMemory(resolver.model, fixValues)
                state.clone(newConstraints).also {
                    val model =
                        (ctx.solver<TvmType>().check(newConstraints) as? USatResult)?.model
                            ?: return null
                    it.models = listOf(model)
                    fixValues.sets.forEach { set ->
                        it.initializeConcreteDictKeys(
                            set.ref,
                            set.dictId,
                            set.elements,
                            ctx.mkBvSort(set.dictId.keyLength.toUInt()),
                        )
                    }
                }
            } else {
                state
            }

        val scope =
            TvmStepScopeManager(
                newState,
                forkBlackList = UForkBlackList.createDefault(),
                allowFailuresOnCurrentStep = true,
            )
        scope.assert(guard)
            ?: return null

        return newState
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
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val addressToDepth = scope.calcOnState { addressToDepth }

            val emptyFixValues =
                scope.calcOnState {
                    TvmFixationMemoryValues.empty(memory.nullRef())
                }

            addressToDepth.entries.fold((trueExpr as UBoolExpr) to emptyFixValues) { acc, (ref, depth) ->
                val curConstraint =
                    fixateValueAndDepth(scope, ref, depth, resolver)
                        ?: return null
                (acc.first and curConstraint.first) to (acc.second.union(curConstraint.second))
            }
        }

    private fun generateHashConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val addressToHash = scope.calcOnState { addressToHash }

            val emptyFixValues =
                scope.calcOnState {
                    TvmFixationMemoryValues.empty(memory.nullRef())
                }

            addressToHash.entries.fold((trueExpr as UBoolExpr) to emptyFixValues) { acc, (ref, hash) ->
                val curConstraint =
                    fixateValueAndHash(scope, ref, hash, resolver)
                        ?: return null

                (acc.first and curConstraint.first) to (acc.second.union(curConstraint.second))
            }
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
            val concreteKey = mkBvHex(publicKeyHex, int257sort.sizeBits)
            val concreteSignature = mkBvHex(signatureHex, signatureCheck.signature.sort.sizeBits)

            val fixateHashCond = concreteHash eq signatureCheck.hash
            val fixateKeyCond = concreteKey eq signatureCheck.publicKey.uncheckedCast()

            val signatureCond =
                if (signatureCheck.checkPassed) {
                    concreteSignature eq signatureCheck.signature
                } else {
                    concreteSignature neq signatureCheck.signature
                }

            return fixateHashCond and fixateKeyCond and signatureCond
        }

    /**
     * Generate expression that fixates ref's value given by model, and its hash (which is originally a mock).
     * */
    private fun fixateValueAndHash(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        hash: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver,
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val nullRef = scope.calcOnState { nullRef }
            val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false, nullRef)
            val fixateValueCond =
                fixator.fixateConcreteValue(scope, ref)
                    ?: return@with null
            val concreteHash = calculateConcreteHash(value)
            val hashCond = hash eq concreteHash
            return (fixateValueCond.first and hashCond) to fixateValueCond.second
        }

    private fun calculateConcreteHash(value: TvmTestReferenceValue): UExpr<TvmInt257Sort> =
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
                TODO()
            }
            is TvmTestSliceValue -> {
                val restCell = truncateSliceCell(value)
                calculateConcreteHash(restCell)
            }
        }

    private fun calculateHashOfCell(cell: Cell): UExpr<TvmInt257Sort> {
        val hash = BigInteger(ByteArray(1) { 0 } + cell.hash().toByteArray())
        return ctx.mkBv(hash, ctx.int257sort)
    }

    private fun fixateValueAndDepth(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        depth: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver,
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val nullRef = scope.calcOnState { nullRef }
            val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = true, nullRef)
            val fixateValueCond =
                fixator.fixateConcreteValue(scope, ref)
                    ?: return@with null
            val concreteDepth = calculateConcreteDepth(value)
            val depthCond = depth eq concreteDepth
            return (fixateValueCond.first and depthCond) to fixateValueCond.second
        }

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
