package org.usvm.machine.interpreter

import io.ksmt.utils.uncheckedCast
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmSignatureCheck
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.calculateConcreteForwardFee
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

    fun postProcessState(scope: TvmStepScopeManager): Unit? =
        with(ctx) {
            assertConstraints(scope) { resolver ->
                val hashConstraint =
                    generateHashConstraint(scope, resolver)
                        ?: return@assertConstraints null

                val depthConstraint =
                    generateDepthConstraint(scope, resolver)
                        ?: return@assertConstraints null
                hashConstraint and depthConstraint
            } ?: return null

            // must be asserted separately since it relies on correct hash values
            assertConstraints(scope) { resolver ->
                generateSignatureConstraints(scope, resolver)
            } ?: return null

            assertConstraints(scope) { resolver ->
                val fwdFeeConstraint =
                    generateFwdFeeConstraints(scope, resolver)
                        ?: return@assertConstraints null
                fwdFeeConstraint
            } ?: return null
        }

    private inline fun assertConstraints(
        scope: TvmStepScopeManager,
        constraintsBuilder: (TvmTestStateResolver) -> UBoolExpr?,
    ): Unit? {
        val resolver = scope.calcOnState { TvmTestStateResolver(ctx, models.first(), this) }
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

    private fun generateHashConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        with(ctx) {
            val addressToHash = scope.calcOnState { refToHash }

            addressToHash.entries.fold(trueExpr as UBoolExpr) { acc, (ref, hash) ->
                val curConstraint =
                    fixateValueAndHash(scope, mkConcreteHeapRef(ref), hash, resolver)
                        ?: return null
                acc and curConstraint
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
    ): UBoolExpr? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
            val fixateValueCond =
                fixator.fixateConcreteValue(scope, ref)
                    ?: return@with null
            val concreteHash = calculateConcreteHash(value)
            val hashCond = hash eq concreteHash
            return fixateValueCond and hashCond
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
    ): UBoolExpr? =
        with(ctx) {
            val value = resolver.resolveRef(ref)
            val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = true)
            val fixateValueCond =
                fixator.fixateConcreteValue(scope, ref)
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
