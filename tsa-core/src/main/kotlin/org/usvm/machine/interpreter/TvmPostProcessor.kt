package org.usvm.machine.interpreter

import io.ksmt.utils.uncheckedCast
import java.math.BigInteger
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField
import org.usvm.machine.TvmContext.Companion.sliceDataPosField
import org.usvm.machine.TvmContext.Companion.sliceRefPosField
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.state.DictId
import org.usvm.machine.state.TvmSignatureCheck
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.dictContainsKey
import org.usvm.machine.state.dictGetValue
import org.usvm.machine.state.dictKeyEntries
import org.usvm.machine.state.preloadDataBitsFromCellWithoutChecks
import org.usvm.machine.state.readCellRef
import org.usvm.test.resolver.truncateSliceCell
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
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
import kotlin.random.Random

class TvmPostProcessor(val ctx: TvmContext) {
    private val signatureKeySize = 32
    private val privateKey by lazy {
        PrivateKeyEd25519(Random(0).nextBytes(signatureKeySize))
    }
    private val publicKey by lazy { privateKey.publicKey() }
    private val publicKeyHex by lazy { publicKey.key.encodeHex() }

    fun postProcessState(scope: TvmStepScopeManager): Unit? = with(ctx) {
        assertConstraints(scope) { resolver ->
            mkAnd(
                generateHashConstraint(scope, resolver),
                generateDepthConstraint(scope, resolver)
            )
        } ?: return null

        // must be asserted separately since it relies on correct hash values
        return assertConstraints(scope) { resolver ->
            generateSignatureConstraints(scope, resolver)
        }
    }

    private inline fun assertConstraints(
        scope: TvmStepScopeManager,
        constraintsBuilder: (TvmTestStateResolver) -> UBoolExpr,
    ): Unit? {
        val resolver = scope.calcOnState { TvmTestStateResolver(ctx, models.first(), this) }
        val constraints = constraintsBuilder(resolver)

        return scope.assert(constraints)
    }

    private fun generateSignatureConstraints(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
    ): UBoolExpr = with(ctx) {
        val signatureChecks = scope.calcOnState { signatureChecks }

        signatureChecks.fold(trueExpr as UBoolExpr) { acc, signatureCheck ->
            val curConstraint = fixateSignatureCheck(signatureCheck, resolver)

            acc and curConstraint
        }
    }

    private fun generateDepthConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver
    ): UBoolExpr = with(ctx) {
        val addressToDepth = scope.calcOnState { addressToDepth }

        addressToDepth.entries.fold(trueExpr as UBoolExpr) { acc, (ref, depth) ->
            val curConstraint = fixateValueAndDepth(scope, ref, depth, resolver)
                ?: falseExpr
            acc and curConstraint
        }
    }

    private fun generateHashConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver
    ): UBoolExpr = with(ctx) {
        val addressToHash = scope.calcOnState { addressToHash }

        addressToHash.entries.fold(trueExpr as UBoolExpr) { acc, (ref, hash) ->
            val curConstraint = fixateValueAndHash(scope, ref, hash, resolver)
                ?: falseExpr
            acc and curConstraint
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun fixateSignatureCheck(
        signatureCheck: TvmSignatureCheck,
        resolver: TvmTestStateResolver
    ): UBoolExpr = with(ctx) {
        val hash = resolver.resolveInt257(signatureCheck.hash)
        val signatureHex = privateKey.sign(hash.value.toByteArray()).toHexString()
        val concreteHash = mkBv(hash.value, int257sort)
        val concreteKey = mkBvHex(publicKeyHex, int257sort.sizeBits)
        val concreteSignature = mkBvHex(signatureHex, signatureCheck.signature.sort.sizeBits)

        val fixateHashCond = concreteHash eq signatureCheck.hash
        val fixateKeyCond = concreteKey eq signatureCheck.publicKey.uncheckedCast()
        val fixateSignatureCond = concreteSignature eq signatureCheck.signature

        return fixateHashCond and fixateKeyCond and fixateSignatureCond
    }

    /**
     * Generate expression that fixates ref's value given by model, and its hash (which is originally a mock).
     * */
    private fun fixateValueAndHash(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        hash: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver
    ): UBoolExpr? = with(ctx) {
        val value = resolver.resolveRef(ref)
        val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = false)
        val fixateValueCond = fixator.fixateConcreteValue(scope, ref)
            ?: return@with null
        val concreteHash = calculateConcreteHash(value)
        val hashCond = hash eq concreteHash
        return fixateValueCond and hashCond
    }

    private fun calculateConcreteHash(value: TvmTestReferenceValue): UExpr<TvmInt257Sort> {
        return when (value) {
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
    }

    private fun calculateHashOfCell(cell: Cell): UExpr<TvmInt257Sort> {
        val hash = BigInteger(ByteArray(1) { 0 } + cell.hash().toByteArray())
        return ctx.mkBv(hash, ctx.int257sort)
    }

    private fun fixateValueAndDepth(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        depth: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver
    ): UBoolExpr? = with(ctx) {
        val value = resolver.resolveRef(ref)
        val fixator = TvmValueFixator(resolver, ctx, structuralConstraintsOnly = true)
        val fixateValueCond = fixator.fixateConcreteValue(scope, ref)
            ?: return@with null
        val concreteDepth = calculateConcreteDepth(value)
        val depthCond = depth eq concreteDepth
        return fixateValueCond and depthCond
    }

    private fun calculateConcreteDepth(value: TvmTestReferenceValue): UExpr<TvmInt257Sort> = with(ctx) {
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