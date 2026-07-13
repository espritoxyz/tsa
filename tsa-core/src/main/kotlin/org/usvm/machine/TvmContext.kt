package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.KContext
import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvAddExpr
import io.ksmt.expr.KBvAndExpr
import io.ksmt.expr.KBvArithShiftRightExpr
import io.ksmt.expr.KBvConcatExpr
import io.ksmt.expr.KBvLogicalShiftRightExpr
import io.ksmt.expr.KBvOrExpr
import io.ksmt.expr.KBvShiftLeftExpr
import io.ksmt.expr.KBvSignExtensionExpr
import io.ksmt.expr.KBvZeroExtensionExpr
import io.ksmt.expr.KExpr
import io.ksmt.expr.KInterpretedValue
import io.ksmt.expr.KIteExpr
import io.ksmt.expr.rewrite.simplify.simplifyAnd
import io.ksmt.expr.rewrite.simplify.simplifyBoolIteConstBranches
import io.ksmt.expr.rewrite.simplify.simplifyBoolIteSameConditionBranch
import io.ksmt.expr.rewrite.simplify.simplifyIteBool
import io.ksmt.expr.rewrite.simplify.simplifyIteLight
import io.ksmt.expr.rewrite.simplify.simplifyIteNotCondition
import io.ksmt.expr.rewrite.simplify.simplifyIteSameBranches
import io.ksmt.expr.rewrite.simplify.simplifyNot
import io.ksmt.expr.rewrite.simplify.simplifyOr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvCustomSizeSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KSort
import io.ksmt.utils.BvUtils.shiftLeft
import io.ksmt.utils.BvUtils.toBigIntegerSigned
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import io.ksmt.utils.powerOfTwo
import io.ksmt.utils.toBigInteger
import io.ksmt.utils.uncheckedCast
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.ton.bytecode.TvmQuitContinuation
import org.ton.cell.Cell
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UBvSort
import org.usvm.UComponents
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UMockSymbol
import org.usvm.isTrue
import org.usvm.machine.intblast.TvmMultiplication
import org.usvm.machine.intblast.TvmSignedDivision
import org.usvm.machine.intblast.TvmSignedModulo
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.state.TvmBadDestinationAddress
import org.usvm.machine.state.TvmCellOverflowError
import org.usvm.machine.state.TvmCellUnderflowError
import org.usvm.machine.state.TvmDictError
import org.usvm.machine.state.TvmFailureType
import org.usvm.machine.state.TvmIntegerOutOfRangeError
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmStackOverflowError
import org.usvm.machine.state.TvmStackUnderflowError
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmTypeCheckError
import org.usvm.machine.state.bvMaxValueSignedExtended
import org.usvm.machine.state.bvMinValueSignedExtended
import org.usvm.machine.state.hash.TvmConstantHashSymbol
import org.usvm.machine.state.hash.TvmHashSymbol
import org.usvm.machine.state.hash.TvmSymbolicHashSymbol
import org.usvm.machine.state.setExit
import org.usvm.machine.state.setFailure
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.memory.stack.BadSizeContext
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.utils.tryTransformToIteWithConcreteLeaves
import java.math.BigInteger

// TODO: There is no size sort in TVM because of absence of arrays, but we need to represent cell data as boolean arrays
//  with size no more than 1023

// TODO make it Bv16
typealias TvmSizeSort = UBv32Sort
typealias SizeExpr = UExpr<TvmSizeSort>

typealias Int257Expr = UExpr<TvmContext.TvmInt257Sort>

class TvmContext(
    val tvmOptions: TvmOptions,
    components: UComponents<TvmType, TvmSizeSort>,
) : UContext<TvmSizeSort>(components) {
    private var simplificationDepth = 0

    /**
     * Execute [body] with incremented recursion depth.
     * Returns null if the depth limit is exceeded.
     */
    private inline fun <T> withSimplificationDepthGuard(body: () -> T): T? {
        if (simplificationDepth > MAX_SIMPLIFICATION_DEPTH) return null
        simplificationDepth++
        try {
            return body()
        } finally {
            simplificationDepth--
        }
    }

    /**
     * If both [lhs] and [rhs] are ITEs sharing the same condition, push [op] into the branches:
     * `op(ite(c, a, b), ite(c, p, q))` -> `ite(c, op(a, p), op(b, q))`.
     *
     * Operand sorts [L] and [R] may differ (e.g. bit-vector concatenation), as may the result
     * sort [Res].
     *
     * Returns null otherwise. [op] is supplied once and reused for every branch, so it is
     * impossible to apply a different operation to one of the branches by mistake.
     */
    private inline fun <L : KSort, R : KSort, Res : KSort> distributeOverMatchingIte(
        lhs: KExpr<L>,
        rhs: KExpr<R>,
        op: (KExpr<L>, KExpr<R>) -> KExpr<Res>,
    ): KExpr<Res>? {
        if (lhs is KIteExpr && rhs is KIteExpr && lhs.condition == rhs.condition) {
            return mkIte(lhs.condition, op(lhs.trueBranch, rhs.trueBranch), op(lhs.falseBranch, rhs.falseBranch))
        }
        return null
    }

    /**
     * Push a binary [op] through ITE arguments (the standard ITE-distribution simplification):
     *  - `op(ite(c, a, b), y)`            -> `ite(c, op(a, y), op(b, y))`
     *  - `op(x, ite(c, a, b))`            -> `ite(c, op(x, a), op(x, b))`
     *  - `op(ite(c, a, b), ite(c, p, q))` -> `ite(c, op(a, p), op(b, q))`
     *
     * Returns null when no distribution applies (neither argument is an ITE, or both are ITEs with
     * different conditions), so the caller can fall through to other simplifications.
     *
     * [op] is supplied once and reused for every branch, so it is impossible to apply a different
     * operation to one of the branches by mistake.
     */
    private inline fun <Sort : KSort, R : KSort> distributeOverIte(
        lhs: KExpr<Sort>,
        rhs: KExpr<Sort>,
        op: (KExpr<Sort>, KExpr<Sort>) -> KExpr<R>,
    ): KExpr<R>? {
        if (lhs is KIteExpr && rhs !is KIteExpr) {
            return mkIte(lhs.condition, op(lhs.trueBranch, rhs), op(lhs.falseBranch, rhs))
        }
        if (rhs is KIteExpr && lhs !is KIteExpr) {
            return mkIte(rhs.condition, op(lhs, rhs.trueBranch), op(lhs, rhs.falseBranch))
        }
        return distributeOverMatchingIte(lhs, rhs, op)
    }

    /**
     * Push a unary [op] through an ITE argument (the standard ITE-distribution simplification):
     * `op(ite(c, t, f))` -> `ite(c, op(t), op(f))`.
     *
     * Returns null when [value] is not an ITE, so the caller can fall through to other
     * simplifications. [op] is supplied once and reused for both branches, so it is impossible
     * to apply a different operation to one of the branches by mistake.
     */
    private inline fun <T : KSort, R : KSort> distributeUnaryOverIte(
        value: KExpr<T>,
        op: (KExpr<T>) -> KExpr<R>,
    ): KExpr<R>? {
        if (value is KIteExpr) {
            return mkIte(value.condition, op(value.trueBranch), op(value.falseBranch))
        }
        return null
    }

    private val tvmSignedDivCache = mkAstInterner<TvmSignedDivision<*>>()

    fun <Sort : UBvSort> mkTvmSignedDiv(
        lhs: UExpr<Sort>,
        rhs: UExpr<Sort>,
    ): UExpr<Sort> {
        withSimplificationDepthGuard {
            if (lhs is KInterpretedValue && rhs is KInterpretedValue) {
                return TvmSignedDivision.transformToBv(lhs, rhs)
            }
            distributeOverIte(lhs, rhs, ::mkTvmSignedDiv)?.let { return it }
        }
        return mkTvmSignedDivNoSimplify(lhs, rhs)
    }

    fun <Sort : UBvSort> mkTvmSignedDivNoSimplify(
        lhs: UExpr<Sort>,
        rhs: UExpr<Sort>,
    ): TvmSignedDivision<Sort> =
        tvmSignedDivCache
            .createIfContextActive {
                TvmSignedDivision(this, lhs, rhs, lhs.sort)
            }.cast()

    private val tvmMulCache = mkAstInterner<TvmMultiplication<*>>()

    fun <Sort : UBvSort> mkTvmMulNoSimplify(
        lhs: UExpr<Sort>,
        rhs: UExpr<Sort>,
    ): TvmMultiplication<Sort> =
        tvmMulCache
            .createIfContextActive {
                TvmMultiplication(this, lhs, rhs, lhs.sort)
            }.cast()

    fun <Sort : UBvSort> mkTvmMul(
        lhs: UExpr<Sort>,
        rhs: UExpr<Sort>,
    ): UExpr<Sort> {
        withSimplificationDepthGuard {
            if (rhs is KInterpretedValue && lhs !is KInterpretedValue) {
                return mkTvmMul(rhs, lhs)
            }

            if (lhs is KInterpretedValue && rhs is KInterpretedValue) {
                return mkBvMulExpr(lhs, rhs)
            }

            if (lhs is KInterpretedValue && lhs.bigIntValue() == BigInteger.ONE) {
                return rhs
            }

            if (lhs is KInterpretedValue && lhs.bigIntValue() == BigInteger.ZERO) {
                return lhs
            }

            distributeOverMatchingIte(lhs, rhs, ::mkTvmMul)?.let { return it }
        }

        return mkTvmMulNoSimplify(lhs, rhs)
    }

    private val tvmSignedModCache = mkAstInterner<TvmSignedModulo<*>>()

    fun <Sort : UBvSort> mkTvmSignedMod(
        lhs: UExpr<Sort>,
        rhs: UExpr<Sort>,
    ): UExpr<Sort> {
        withSimplificationDepthGuard {
            distributeOverIte(lhs, rhs, ::mkTvmSignedMod)?.let { return it }
        }
        return mkTvmSignedModNoSimplify(lhs, rhs)
    }

    fun <Sort : UBvSort> mkTvmSignedModNoSimplify(
        lhs: UExpr<Sort>,
        rhs: UExpr<Sort>,
    ): TvmSignedModulo<Sort> =
        tvmSignedModCache
            .createIfContextActive {
                TvmSignedModulo(this, lhs, rhs, lhs.sort)
            }.cast()

    private val tsaAccountIdSymbolInterner = mkAstInterner<TsaAccountIdSymbol>()

    fun mkTsaAccountIdSymbol(
        isStateInit: UExpr<KBoolSort>,
        boundStateInitHash: TvmSymbolicHashSymbol,
        symbolicAccountId: UExpr<KBvSort>,
        symbolicData: UHeapRef,
        symbolicCode: UHeapRef,
    ): TsaAccountIdSymbol {
        require(symbolicAccountId.sort.sizeBits == 256u)
        return tsaAccountIdSymbolInterner.createIfContextActive {
            TsaAccountIdSymbol(
                ctx = this,
                isStateInit = isStateInit,
                boundStateInitHash = boundStateInitHash,
                symbolicAccountId = symbolicAccountId,
                code = symbolicCode,
                data = symbolicData,
            )
        }
    }

    private val tvmHashCache = mkAstInterner<TvmHashSymbol>()

    fun mkTvmHash(
        ref: UConcreteHeapRef,
        fallbackMock: UMockSymbol<UBvSort>,
    ): TvmSymbolicHashSymbol =
        tvmHashCache
            .createIfContextActive {
                TvmSymbolicHashSymbol(this, ref, fallbackMock)
            }.cast()

    fun mkTvmConstantHash(
        ref: UConcreteHeapRef,
        refValue: Cell,
    ): TvmConstantHashSymbol =
        tvmHashCache
            .createIfContextActive {
                TvmConstantHashSymbol(this, refValue, ref)
            }.cast()

    val int257sort = TvmInt257Sort(this)
    val cellDataSort = TvmCellDataSort(this)

    // Utility sorts for arith operations
    val int257Ext1Sort = TvmInt257Ext1Sort(this)
    val int257Ext256Sort = TvmInt257Ext256Sort(this)

    val oneCellValue: KBitVecValue<TvmCellDataSort> = 1.toCellSort()
    val minusOneCellValue: KBitVecValue<TvmCellDataSort> = (-1).toCellSort()
    val zeroCellValue: KBitVecValue<TvmCellDataSort> = 0.toCellSort()

    val trueValue: KBitVecValue<TvmInt257Sort> = TRUE_CONCRETE_VALUE.toBv257()
    val falseValue: KBitVecValue<TvmInt257Sort> = FALSE_CONCRETE_VALUE.toBv257()
    val oneValue: KBitVecValue<TvmInt257Sort> = 1.toBv257()
    val twoValue: KBitVecValue<TvmInt257Sort> = 2.toBv257()
    val threeValue: KBitVecValue<TvmInt257Sort> = 3.toBv257()
    val eightValue: KBitVecValue<TvmInt257Sort> = 8.toBv257()
    val zeroValue: KBitVecValue<TvmInt257Sort> = falseValue
    val minusOneValue: KBitVecValue<TvmInt257Sort> = trueValue
    val intBitsValue: KBitVecValue<TvmInt257Sort> = INT_BITS.toInt().toBv257()
    val maxTupleSizeValue: KBitVecValue<TvmInt257Sort> = MAX_TUPLE_SIZE.toBv257()
    val unixTimeMinValue: KBitVecValue<TvmInt257Sort> = UNIX_TIME_MIN.toBv257()
    val unixTimeMaxValue: KBitVecValue<TvmInt257Sort> = UNIX_TIME_MAX.toBv257()
    val min257BitValue: KExpr<TvmInt257Sort> = bvMinValueSignedExtended(intBitsValue)
    val max257BitValue: KExpr<TvmInt257Sort> = bvMaxValueSignedExtended(intBitsValue)
    val maxLogicalTimeValue = mkBvShiftLeftExpr(oneValue, 64.toBv257())

    val masterchain: KBitVecValue<TvmInt257Sort> = minusOneValue
    val baseChain: KBitVecValue<TvmInt257Sort> = zeroValue

    val zeroSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(0)
    val oneSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(1)
    val twoSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(2)
    val threeSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(3)
    val fourSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(4)
    val eightSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(8)
    val sizeExpr32: UExpr<TvmSizeSort> = mkSizeExpr(32)
    val maxDataLengthSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(MAX_DATA_LENGTH)
    val maxRefsLengthSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(MAX_REFS_NUMBER)
    val intBitsSizeExpr = mkSizeExpr(INT_BITS.toInt())

    val zeroBit = mkBv(0, 1u)
    val oneBit = mkBv(1, 1u)

    val minMessageCurrencyValue = MIN_MESSAGE_CURRENCY.toBv257()
    val maxMessageCurrencyValue = MAX_MESSAGE_CURRENCY.toBv257()

    private var inputStackEntryCounter: Int = 0

    fun nextInputStackEntryId(): Int = inputStackEntryCounter++

    val nullValue: UConcreteHeapRef = mkConcreteHeapRef(NULL_ADDRESS)

    val quit0Cont = TvmQuitContinuation(0u)
    val quit1Cont = TvmQuitContinuation(1u)

    val throwTypeCheckError: (TvmState) -> Unit = setFailure(TvmTypeCheckError)
    val throwBadDestinationAddress: (TvmState) -> Unit = {
        it.setExit(TvmResult.TvmSoftFailure(TvmBadDestinationAddress(it.currentContract), it.phase))
    }
    val throwStackUnderflowError: (TvmState) -> Unit = setFailure(TvmStackUnderflowError)
    val throwStackOverflowError: (TvmState) -> Unit = setFailure(TvmStackOverflowError)
    val throwIntegerOverflowError: (TvmState) -> Unit = setFailure(TvmIntegerOverflowError)
    val throwIntegerOutOfRangeError: (TvmState) -> Unit = setFailure(TvmIntegerOutOfRangeError)
    val throwCellOverflowError: (TvmState) -> Unit = setFailure(TvmCellOverflowError)
    val throwIntAddressError: (TvmState) -> Unit = setFailure(TvmCellOverflowError)
    val throwUnknownCellUnderflowError: (TvmState) -> Unit =
        setFailure(TvmCellUnderflowError, TvmFailureType.UnknownError)
    val throwStructuralCellUnderflowError: (TvmState) -> Unit =
        setFailure(TvmCellUnderflowError, TvmFailureType.StructuralError)
    val throwRealCellUnderflowError: (TvmState) -> Unit = setFailure(TvmCellUnderflowError, TvmFailureType.RealError)

    val throwCellUnderflowErrorBasedOnContext: (TvmState, BadSizeContext) -> Unit = { state, context ->
        when (context) {
            BadSizeContext.GoodSizeIsUnknown -> throwUnknownCellUnderflowError(state)
            BadSizeContext.GoodSizeIsUnsat -> throwRealCellUnderflowError(state)
            BadSizeContext.GoodSizeIsSat -> throwStructuralCellUnderflowError(state)
        }
    }

    val throwRealDictError: (TvmState) -> Unit = setFailure(TvmDictError, TvmFailureType.RealError)

    val sendMsgActionTag = mkBvHex("0ec3c86d", 32u)
    val reserveActionTag = mkBvHex("36e6b809", 32u)
    val bouncedMessageTagLong = 0xffffffff

    val sendMsgFeeEstimationFlag = powerOfTwo(10u).toBv257()

    fun UBoolExpr.toBv257Bool(): UExpr<TvmInt257Sort> =
        with(ctx) {
            mkIte(
                condition = this@toBv257Bool,
                trueBranch = trueValue,
                falseBranch = falseValue,
            )
        }

    fun Number.toBv257(): KBitVecValue<TvmInt257Sort> = mkBv(toBigInteger(), int257sort)

    fun Number.toSizeSort(): KBitVecValue<TvmSizeSort> = mkBv(toBigInteger(), sizeSort)

    fun Number.toCellSort(): KBitVecValue<TvmCellDataSort> = mkBv(toBigInteger(), cellDataSort)

    fun <Sort : UBvSort> UExpr<Sort>.signedExtendToInteger(): UExpr<TvmInt257Sort> = signExtendToSort(int257sort)

    fun <Sort : UBvSort> UExpr<Sort>.unsignedExtendToInteger(): UExpr<TvmInt257Sort> = zeroExtendToSort(int257sort)

    fun <InSort : UBvSort, OutSort : UBvSort> UExpr<InSort>.zeroExtendToSort(sort: OutSort): UExpr<OutSort> {
        require(this.sort.sizeBits <= sort.sizeBits)
        val extensionSize = sort.sizeBits - this.sort.sizeBits
        return mkBvZeroExtensionExpr(extensionSize.toInt(), this).asExpr(sort)
    }

    fun <InSort : UBvSort, OutSort : UBvSort> UExpr<InSort>.signExtendToSort(sort: OutSort): UExpr<OutSort> {
        require(this.sort.sizeBits <= sort.sizeBits)
        val extensionSize = sort.sizeBits - this.sort.sizeBits
        return mkBvSignExtensionExpr(extensionSize.toInt(), this).asExpr(sort)
    }

    fun <Sort : UBvSort> UExpr<Sort>.extractToSizeSort(): UExpr<TvmSizeSort> = extractToSort(sizeSort)

    fun <Sort : UBvSort> UExpr<Sort>.extractToInt257Sort(): UExpr<TvmInt257Sort> = extractToSort(int257sort)

    fun <InSort : UBvSort, OutSort : UBvSort> UExpr<InSort>.extractToSort(sort: OutSort): UExpr<OutSort> {
        require(this.sort.sizeBits >= sort.sizeBits)

        return mkBvExtractExpr(sort.sizeBits.toInt() - 1, 0, this).asExpr(sort)
    }

    override fun mkBvSort(sizeBits: UInt): KBvSort =
        when (sizeBits) {
            INT_BITS -> int257sort
            CELL_DATA_BITS -> cellDataSort
            INT_EXT1_BITS -> int257Ext1Sort
            INT_EXT256_BITS -> int257Ext256Sort
            else -> super.mkBvSort(sizeBits)
        }

    override fun <T : KBvSort> mkBvSignedLessOrEqualExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<KBoolSort> {
        withSimplificationDepthGuard {
            if (arg0 is KInterpretedValue &&
                arg0.bigIntValue() == BigInteger.ZERO &&
                arg1 is KBvZeroExtensionExpr &&
                arg1.extensionSize > 0
            ) {
                return trueExpr
            }

            distributeOverIte(arg0, arg1, ::mkBvSignedLessOrEqualExpr)?.let { return it }
        }
        return super.mkBvSignedLessOrEqualExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvSignedLessExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<KBoolSort> =
        mkAnd(
            mkBvSignedLessOrEqualExpr(arg0, arg1),
            (arg0 eq arg1).not(),
        )

    override fun <T : KBvSort> mkBvSignedGreaterExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<KBoolSort> = mkBvSignedLessExpr(arg1, arg0)

    override fun <T : KBvSort> mkBvSignedGreaterOrEqualExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<KBoolSort> = mkBvSignedLessOrEqualExpr(arg1, arg0)

    override fun <T : KBvSort> mkBvExtractExpr(
        high: Int,
        low: Int,
        value: KExpr<T>,
    ): KExpr<KBvSort> {
        withSimplificationDepthGuard {
            return mkBvExtractExprSimplified(high, low, value)
        }
        return super.mkBvExtractExpr(high, low, value)
    }

    private fun <T : KBvSort> mkBvExtractExprSimplified(
        high: Int,
        low: Int,
        value: KExpr<T>,
    ): KExpr<KBvSort> {
        if (value is KBvOrExpr) {
            return mkBvOrExpr(
                mkBvExtractExpr(high, low, value.arg0),
                mkBvExtractExpr(high, low, value.arg1),
            )
        }

        if (value is KBvAndExpr) {
            return mkBvAndExpr(
                mkBvExtractExpr(high, low, value.arg0),
                mkBvExtractExpr(high, low, value.arg1),
            )
        }

        if (value is KBvLogicalShiftRightExpr && value.shift is KBitVecValue) {
            val maxSizeBits = value.sort.sizeBits.toInt()
            val shiftBI = (value.shift as KBitVecValue).toBigIntegerSigned()
            if (shiftBI < maxSizeBits.toBigInteger() && shiftBI >= BigInteger.ZERO) {
                val shift = shiftBI.toInt()
                val newHigh = high + shift
                val newLow = low + shift
                if (newLow >= 0 && newHigh < maxSizeBits) {
                    return mkBvExtractExpr(newHigh, newLow, value.arg)
                }
            }
        }

        if (value is KBvShiftLeftExpr && value.shift is KBitVecValue) {
            val maxSizeBits = value.sort.sizeBits.toInt()
            val shiftBI = (value.shift as KBitVecValue).toBigIntegerSigned()
            if (shiftBI < maxSizeBits.toBigInteger() && shiftBI >= BigInteger.ZERO) {
                val shift = shiftBI.toInt()
                val newHigh = high - shift
                val newLow = low - shift
                if (newLow >= 0 && newHigh < maxSizeBits) {
                    return mkBvExtractExpr(newHigh, newLow, value.arg)
                }
            }
        }

        if (value is KBvZeroExtensionExpr && value.value.sort.sizeBits > high.toUInt()) {
            return mkBvExtractExpr(high, low, value.value)
        }

        if (value is KBvZeroExtensionExpr && value.value.sort.sizeBits <= low.toUInt()) {
            return mkBv(0, (high - low + 1).toUInt())
        }

        if (value is KBvSignExtensionExpr && value.value.sort.sizeBits > high.toUInt()) {
            return mkBvExtractExpr(high, low, value.value)
        }

        if (low == 0 && value is KBvSignExtensionExpr && value.value.sort.sizeBits <= high.toUInt()) {
            val bits =
                value.value.sort.sizeBits
                    .toInt()
            return mkBvSignExtensionExpr(high + 1 - bits, value.value)
        }

        if (value is KBvZeroExtensionExpr && low.toUInt() < value.value.sort.sizeBits) {
            val bits =
                value.value.sort.sizeBits
                    .toInt()
            return if (high >= bits) {
                mkBvZeroExtensionExpr(high - bits + 1, mkBvExtractExpr(high = bits - 1, low = low, value.value))
            } else {
                mkBvExtractExpr(high = high, low = low, value.value)
            }
        }

        distributeUnaryOverIte(value) { mkBvExtractExpr(high, low, it) }?.let { return it }

        if (value is KBvConcatExpr && low.toUInt() >= value.arg1.sort.sizeBits) {
            val sub =
                value.arg1.sort.sizeBits
                    .toInt()
            return mkBvExtractExpr(high = high - sub, low = low - sub, value.arg0)
        }

        if (value is KBvConcatExpr &&
            low.toUInt() < value.arg1.sort.sizeBits &&
            high.toUInt() >= value.arg1.sort.sizeBits
        ) {
            val sub =
                value.arg1.sort.sizeBits
                    .toInt()
            return mkBvConcatExpr(
                mkBvExtractExpr(high = high - sub, low = 0, value.arg0),
                mkBvExtractExpr(high = sub - 1, low = low, value.arg1),
            )
        }

        if (value is KBvConcatExpr && high.toUInt() < value.arg1.sort.sizeBits) {
            return mkBvExtractExpr(high = high, low = low, value.arg1)
        }

        return super.mkBvExtractExpr(high, low, value)
    }

    override fun <T : KBvSort> mkBvLogicalShiftRightExpr(
        arg: KExpr<T>,
        shift: KExpr<T>,
    ): KExpr<T> {
        if (shift is KInterpretedValue &&
            arg is KBvConcatExpr &&
            arg.arg1.sort.sizeBits
                .toInt()
                .toBigInteger() == shift.bigIntValue()
        ) {
            return arg.arg0.zeroExtendToSort(arg.sort)
        }

        if (shift is KInterpretedValue &&
            arg is KBvConcatExpr &&
            arg.arg1.sort.sizeBits
                .toInt()
                .toBigInteger() < shift.bigIntValue()
        ) {
            val w0 =
                arg.arg0.sort.sizeBits
                    .toInt()
                    .toBigInteger()
            val toTakeFromFirst =
                shift.bigIntValue() -
                    arg.arg1.sort.sizeBits
                        .toInt()
                        .toBigInteger()
            if (toTakeFromFirst >= w0) {
                return mkBv(0, arg.sort).uncheckedCast()
            }
            return mkBvLogicalShiftRightExpr(arg.arg0, mkBv(toTakeFromFirst, arg.arg0.sort))
                .zeroExtendToSort(arg.sort)
                .uncheckedCast()
        }
        return super.mkBvLogicalShiftRightExpr(arg, shift)
    }

    override fun <T : KBvSort> mkBvShiftLeftExpr(
        arg: KExpr<T>,
        shift: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            return mkBvShiftLeftExprSimplified(arg, shift)
        }
        return super.mkBvShiftLeftExpr(arg, shift)
    }

    private fun <T : KBvSort> mkBvShiftLeftExprSimplified(
        arg: KExpr<T>,
        shift: KExpr<T>,
    ): KExpr<T> {
        if (shift is KBitVecValue) {
            val shiftBits = shift.bigIntValue()

            check(shiftBits >= BigInteger.ZERO) {
                "Negative shift"
            }

            val argSizeBits = arg.sort.sizeBits.toInt()

            if (shiftBits >= argSizeBits.toBigInteger()) {
                return mkBv(0, arg.sort.sizeBits).uncheckedCast()
            }

            if (shiftBits == BigInteger.ZERO) {
                return arg
            }

            if (arg is KBvShiftLeftExpr && arg.shift is KBitVecValue) {
                val accumulatedShiftBits = arg.shift.bigIntValue() + shiftBits
                if (accumulatedShiftBits < argSizeBits.toBigInteger()) {
                    val accumulatedShift: KExpr<T> = mkBv(accumulatedShiftBits, shift.sort.sizeBits).uncheckedCast()
                    return mkBvShiftLeftExpr(arg.arg, accumulatedShift)
                }
            }

            val concreteShiftBits = shiftBits.toInt()
            val remainingHighBits = argSizeBits - concreteShiftBits
            return mkBvConcatExpr(
                mkBvExtractExpr(high = remainingHighBits - 1, low = 0, value = arg),
                mkBv(0, concreteShiftBits.toUInt()),
            ).uncheckedCast()
        }
        if (shift is KIteExpr && arg !is KIteExpr) {
            if (shift.trueBranch is KBitVecValue && shift.trueBranch.bigIntValue() < BigInteger.ZERO) {
                return mkBvShiftLeftExpr(arg, shift.falseBranch)
            }
            if (shift.falseBranch is KBitVecValue && shift.falseBranch.bigIntValue() < BigInteger.ZERO) {
                return mkBvShiftLeftExpr(arg, shift.trueBranch)
            }
            return mkIte(
                shift.condition,
                mkBvShiftLeftExpr(arg, shift.trueBranch),
                mkBvShiftLeftExpr(arg, shift.falseBranch),
            )
        }
        return super.mkBvShiftLeftExpr(arg, shift)
    }

    override fun <T : KBvSort> mkBvMulNoOverflowExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
        isSigned: Boolean,
    ): KExpr<KBoolSort> {
        withSimplificationDepthGuard {
            distributeOverIte(arg0, arg1) { a, b -> mkBvMulNoOverflowExpr(a, b, isSigned) }?.let { return it }
        }
        return super.mkBvMulNoOverflowExpr(arg0, arg1, isSigned)
    }

    override fun <T : KBvSort> mkBvArithShiftRightExpr(
        arg: KExpr<T>,
        shift: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            if (arg is KBvArithShiftRightExpr && arg.shift is KInterpretedValue && shift is KInterpretedValue) {
                return mkBvArithShiftRightExpr(arg.arg, mkBvAddExpr(arg.shift, shift))
            }
            distributeOverIte(arg, shift, ::mkBvArithShiftRightExpr)?.let { return it }
        }
        return super.mkBvArithShiftRightExpr(arg, shift)
    }

    override fun <T : KBvSort, S : KBvSort> mkBvConcatExpr(
        arg0: KExpr<T>,
        arg1: KExpr<S>,
    ): KExpr<KBvSort> {
        withSimplificationDepthGuard {
            distributeOverMatchingIte(arg0, arg1, ::mkBvConcatExpr)?.let { return it }
            // Unlike most ops, concat distributes a single ITE side only when the other side is a
            // concrete value, to avoid duplicating an arbitrary symbolic operand into both branches.
            if (arg0 is KBitVecValue) {
                distributeUnaryOverIte(arg1) { mkBvConcatExpr(arg0, it) }?.let { return it }
            }
            if (arg1 is KBitVecValue) {
                distributeUnaryOverIte(arg0) { mkBvConcatExpr(it, arg1) }?.let { return it }
            }
        }
        return super.mkBvConcatExpr(arg0, arg1)
    }

    private fun tvmSimplifyBoolIte(
        condition: KExpr<KBoolSort>,
        trueBranch: KExpr<KBoolSort>,
        falseBranch: KExpr<KBoolSort>,
    ): KExpr<KBoolSort> =
        simplifyBoolIteConstBranches(
            condition = condition,
            trueBranch = trueBranch,
            falseBranch = falseBranch,
            rewriteOr = { a, b -> simplifyOr(a, b, flat = false) },
            rewriteAnd = KContext::simplifyAnd,
            rewriteNot = KContext::simplifyNot,
        ) { condition2, trueBranch2, falseBranch2 ->
            simplifyBoolIteSameConditionBranch(
                condition = condition2,
                trueBranch = trueBranch2,
                falseBranch = falseBranch2,
                rewriteAnd = KContext::simplifyAnd,
                rewriteOr = { a, b -> simplifyOr(a, b, flat = false) },
                cont = ::mkIteNoSimplify,
            )
        }

    private fun <T : KSort> tvmSimplifyIte(
        condition: KExpr<KBoolSort>,
        trueBranch: KExpr<T>,
        falseBranch: KExpr<T>,
    ): KExpr<T> =
        simplifyIteNotCondition(condition, trueBranch, falseBranch) { condition2, trueBranch2, falseBranch2 ->
            simplifyIteLight(condition2, trueBranch2, falseBranch2) { condition3, trueBranch3, falseBranch3 ->
                simplifyIteSameBranches(
                    condition3,
                    trueBranch3,
                    falseBranch3,
                    { a, b, c -> tvmSimplifyIte(a, b, c) },
                    { a, b -> simplifyOr(a, b, flat = false) },
                ) { condition4, trueBranch4, falseBranch4 ->
                    simplifyIteBool(
                        condition4,
                        trueBranch4,
                        falseBranch4,
                        { a, b, c -> tvmSimplifyBoolIte(a, b, c) },
                        ::mkIteNoSimplify,
                    )
                }
            }
        }

    override fun <T : KBvSort> mkBvAndExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            return mkBvAndExprSimplified(arg0, arg1)
        }
        return super.mkBvAndExpr(arg0, arg1)
    }

    private fun <T : KBvSort> mkBvAndExprSimplified(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        if (arg1 is KIteExpr && arg0 !is KIteExpr) {
            return mkBvAndExpr(arg1, arg0)
        }
        if (arg0 is KIteExpr && arg1 !is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvAndExpr(arg1, arg0.trueBranch),
                falseBranch = mkBvAndExpr(arg1, arg0.falseBranch),
            )
        }
        if (arg0 is KIteExpr && arg0.trueBranch is KBitVecValue && arg0.falseBranch is KBitVecValue) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvAndExpr(arg1, arg0.trueBranch),
                falseBranch = mkBvAndExpr(arg1, arg0.falseBranch),
            )
        }
        if (arg1 is KIteExpr && arg1.trueBranch is KBitVecValue && arg1.falseBranch is KBitVecValue) {
            return mkIte(
                arg1.condition,
                trueBranch = mkBvAndExpr(arg0, arg1.trueBranch),
                falseBranch = mkBvAndExpr(arg0, arg1.falseBranch),
            )
        }
        return super.mkBvAndExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvOrExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            return mkBvOrExprSimplified(arg0, arg1)
        }
        return super.mkBvOrExpr(arg0, arg1)
    }

    private fun <T : KBvSort> mkBvOrExprSimplified(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        if (arg1 is KIteExpr && arg0 !is KIteExpr) {
            return mkBvOrExpr(arg1, arg0)
        }
        if (arg0 is KIteExpr && arg1 !is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvOrExpr(arg1, arg0.trueBranch),
                falseBranch = mkBvOrExpr(arg1, arg0.falseBranch),
            )
        }
        if (arg0 is KIteExpr && arg0.trueBranch is KBitVecValue && arg0.falseBranch is KBitVecValue) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvOrExpr(arg1, arg0.trueBranch),
                falseBranch = mkBvOrExpr(arg1, arg0.falseBranch),
            )
        }
        return super.mkBvOrExpr(arg0, arg1)
    }

    override fun <T : KSort> mkIte(
        condition: KExpr<KBoolSort>,
        trueBranch: KExpr<T>,
        falseBranch: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            // ite(C, ite(C2, T2, F), F) → ite(C ∧ C2, T2, F)
            if (trueBranch is KIteExpr && trueBranch.falseBranch == falseBranch) {
                return mkIte(
                    mkAnd(condition, trueBranch.condition),
                    trueBranch.trueBranch,
                    falseBranch,
                )
            }
            // ite(C, ite(C2, F, T2), F) → ite(C ∧ ¬C2, T2, F)
            if (trueBranch is KIteExpr && trueBranch.trueBranch == falseBranch) {
                return mkIte(
                    mkAnd(condition, trueBranch.condition.not()),
                    trueBranch.falseBranch,
                    falseBranch,
                )
            }
            // ite(C, T, ite(C2, T2, T)) → ite(¬C ∧ C2, T2, T)
            if (falseBranch is KIteExpr && falseBranch.falseBranch == trueBranch) {
                return mkIte(
                    mkAnd(condition.not(), falseBranch.condition),
                    falseBranch.trueBranch,
                    trueBranch,
                )
            }
            // ite(C, T, ite(C2, T, T2)) → ite(¬C ∧ ¬C2, T2, T)
            if (falseBranch is KIteExpr && falseBranch.trueBranch == trueBranch) {
                return mkIte(
                    mkAnd(condition.not(), falseBranch.condition.not()),
                    falseBranch.falseBranch,
                    trueBranch,
                )
            }

            return tvmSimplifyIte(condition, trueBranch, falseBranch)
        }
        return super.mkIte(condition, trueBranch, falseBranch)
    }

    override fun <T : KSort> mkEq(
        lhs: KExpr<T>,
        rhs: KExpr<T>,
        order: Boolean,
    ): KExpr<KBoolSort> {
        withSimplificationDepthGuard {
            return mkEqSimplified(lhs, rhs, order)
        }
        return super.mkEq(lhs, rhs, order)
    }

    private fun <T : KSort> mkEqSimplified(
        lhs: KExpr<T>,
        rhs: KExpr<T>,
        order: Boolean,
    ): KExpr<KBoolSort> {
        if (rhs is UIteExpr &&
            rhs.trueBranch != rhs.falseBranch &&
            rhs.trueBranch is KInterpretedValue &&
            rhs.falseBranch is KInterpretedValue
        ) {
            if (rhs.trueBranch == lhs) {
                return rhs.condition
            }
            if (rhs.falseBranch == lhs) {
                return rhs.condition.not()
            }
        }

        if (lhs is UIteExpr &&
            lhs.trueBranch != lhs.falseBranch &&
            lhs.trueBranch is KInterpretedValue &&
            lhs.falseBranch is KInterpretedValue
        ) {
            if (lhs.trueBranch == rhs) {
                return lhs.condition
            }
            if (lhs.falseBranch == rhs) {
                return lhs.condition.not()
            }
        }

        if (rhs is KInterpretedValue && lhs !is KInterpretedValue) {
            return mkEq(rhs, lhs)
        }

        if (lhs is KBitVecValue && rhs is KBvConcatExpr && rhs.arg1 is KBitVecValue) {
            val border =
                rhs.arg1.sort.sizeBits
                    .toInt()
            val tail = mkBvExtractExpr<KBvSort>(high = border - 1, low = 0, lhs.uncheckedCast())
            check(tail is KBitVecValue) {
                "Unexpected tail: $tail"
            }
            if (tail == rhs.arg1) {
                val head =
                    mkBvExtractExpr<KBvSort>(
                        high = (lhs.sort as KBvSort).sizeBits.toInt() - 1,
                        low = border,
                        lhs.uncheckedCast(),
                    )
                return mkEq(head, rhs.arg0)
            } else {
                return falseExpr
            }
        }

        if (lhs is KBitVecValue && rhs is KBvConcatExpr && rhs.arg0 is KBitVecValue) {
            val border =
                rhs.arg1.sort.sizeBits
                    .toInt()
            val head =
                mkBvExtractExpr<KBvSort>(
                    high = (lhs.sort as KBvSort).sizeBits.toInt() - 1,
                    low = border,
                    lhs.uncheckedCast(),
                )
            check(head is KBitVecValue) {
                "Unexpected head: $head"
            }
            if (head == rhs.arg0) {
                val tail = mkBvExtractExpr<KBvSort>(high = border - 1, low = 0, lhs.uncheckedCast())
                return mkEq(rhs.arg1, tail)
            } else {
                return falseExpr
            }
        }

        if (lhs is KBvZeroExtensionExpr && rhs is KBvZeroExtensionExpr && lhs.extensionSize == rhs.extensionSize) {
            return mkEq(lhs.value, rhs.value)
        }

        return super.mkEq(lhs, rhs, order)
    }

    override fun <T : KBvSort> mkBvSubExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        if (arg0 is KBvAddExpr && arg0.arg1 == arg1) {
            return arg0.arg0
        }
        if (arg0 is KBvAddExpr && arg0.arg0 == arg1) {
            return arg0.arg1
        }
        return mkBvAddExpr(arg0, mkBvNegationExpr(arg1))
    }

    override fun <T : KBvSort> mkBvZeroExtensionExpr(
        extensionSize: Int,
        value: KExpr<T>,
    ): KExpr<KBvSort> {
        if (value is KBvZeroExtensionExpr) {
            return mkBvZeroExtensionExpr(extensionSize + value.extensionSize, value.value)
        }
        return super.mkBvZeroExtensionExpr(extensionSize, value)
    }

    override fun <T : KBvSort> mkBvAddExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            return mkBvAddExprSimplified(arg0, arg1)
        }
        return super.mkBvAddExpr(arg0, arg1)
    }

    private fun <T : KBvSort> mkBvAddExprSimplified(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        val transformedArg0 = arg0.tryTransformToIteWithConcreteLeaves()
        val transformedArg1 = arg1.tryTransformToIteWithConcreteLeaves()

        distributeOverIte(transformedArg0 ?: arg0, transformedArg1 ?: arg1, ::mkBvAddExpr)?.let { return it }

        if (transformedArg0 != null && transformedArg1 != null) {
            return super.mkBvAddExpr(transformedArg0, transformedArg1)
        }
        return super.mkBvAddExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvSignExtensionExpr(
        extensionSize: Int,
        value: KExpr<T>,
    ): KExpr<KBvSort> {
        withSimplificationDepthGuard {
            distributeUnaryOverIte(value) { mkBvSignExtensionExpr(extensionSize, it) }?.let { return it }
            if (value is KBvZeroExtensionExpr && value.extensionSize > 0) {
                return mkBvZeroExtensionExpr(extensionSize + value.extensionSize, value.value)
            }
        }
        return super.mkBvSignExtensionExpr(extensionSize, value)
    }

    override fun <T : KBvSort> mkBvMulExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<T> {
        withSimplificationDepthGuard {
            if (arg0 is KBitVecValue && arg1 !is KBitVecValue) {
                return mkBvMulExpr(arg1, arg0)
            }
            distributeOverIte(arg0, arg1, ::mkBvMulExpr)?.let { return it }
        }
        return super.mkBvMulExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvNegationExpr(value: KExpr<T>): KExpr<T> {
        withSimplificationDepthGuard {
            distributeUnaryOverIte(value, ::mkBvNegationExpr)?.let { return it }
        }
        return super.mkBvNegationExpr(value)
    }

    override fun <T : KBvSort> mkBvNotExpr(value: KExpr<T>): KExpr<T> {
        withSimplificationDepthGuard {
            distributeUnaryOverIte(value, ::mkBvNotExpr)?.let { return it }
        }
        return super.mkBvNotExpr(value)
    }

    companion object {
        private const val MAX_SIMPLIFICATION_DEPTH = 500

        const val MAX_DATA_LENGTH: Int = 1023
        const val MAX_REFS_NUMBER: Int = 4

        const val MAX_TUPLE_SIZE: Int = 255

        const val INT_BITS: UInt = 257u
        val CELL_DATA_BITS: UInt = MAX_DATA_LENGTH.toUInt()

        // Apr 05 2024 12:08:29 GMT+0000
        const val UNIX_TIME_MIN: Long = 1712318909

        // Jan 01 2100 00:00:00 GMT+0000
        const val UNIX_TIME_MAX: Long = 4102444800

        const val GRAMS_LENGTH_BITS: UInt = 4u
        const val MAX_GRAMS_BITS: UInt = 120u

        const val MAX_ACTIONS = 255

        const val CONFIG_KEY_LENGTH: Int = 32

        const val HASH_BITS: UInt = 256u

        const val STD_WORKCHAIN_BITS: Int = 8
        val ADDRESS_BITS: Int = HASH_BITS.toInt()

        const val ADDRESS_TAG_LENGTH: Int = 2
        val ADDRESS_TAG_BITS: UInt = ADDRESS_TAG_LENGTH.toUInt()

        const val NONE_ADDRESS_TAG = "00"
        const val EXTERN_ADDRESS_TAG = "01"

        const val STD_ADDRESS_TAG = "10"
        const val VAR_ADDRESS_TAG = "11"

        // Utility bit sizes for arith operations
        val INT_EXT1_BITS: UInt = INT_BITS + 1u
        val INT_EXT256_BITS: UInt = INT_BITS + 256u

        /**
         * Minimum incoming message value/balance in nanotons.
         * Used as a workaround for test generation, where a message with too small value caused errors in blueprint sandbox.
         */
        const val MIN_MESSAGE_CURRENCY: Long = 100_000_000

        // Maximum incoming message value/balance in nanotons
        val MAX_MESSAGE_CURRENCY: BigInteger = BigInteger.TEN.pow(19)

        const val MAX_FWD_FEE = 1166940800

        /** 1 TON in nanotons. */
        const val NANOTONS_IN_TON: Long = 1_000_000_000

        /** Upper bound (exclusive) for the symbolic storage phase fee in c7[12]: 10 TON. */
        const val MAX_STORAGE_PHASE_FEES: Long = 10L * NANOTONS_IN_TON

        /** Upper bound (exclusive) for the symbolic due payment in c7[15]: 0.1 TON. */
        const val MAX_DUE_PAYMENT: Long = NANOTONS_IN_TON / 10

        const val BITS_FOR_BALANCE = 64u
        const val BITS_FOR_UNIX_TIME = 32u
        const val BITS_FOR_FWD_FEE = 31u

        val RECEIVE_INTERNAL_ID: MethodId = 0.toMethodId()
        val RECEIVE_EXTERNAL_ID: MethodId = (-1).toMethodId()

        const val OP_BITS: UInt = 32u
        val OP_BYTES: UInt = OP_BITS / 4u

        const val TRUE_CONCRETE_VALUE = -1
        const val FALSE_CONCRETE_VALUE = 0

        val sliceRefPosField: TvmField = TvmFieldImpl(TvmSliceType, "refPos")
        val sliceCellField: TvmField = TvmFieldImpl(TvmSliceType, "cell")

        val dictKeyLengthField: TvmField = TvmFieldImpl(TvmDictCellType, "keyLength")

        val stdMsgAddrSize = ADDRESS_TAG_LENGTH + 1 + STD_WORKCHAIN_BITS + ADDRESS_BITS

        fun KContext.tctx(): TvmContext = this as TvmContext
    }

    init {
        check(unsignedIntegerFitsBits(MAX_MESSAGE_CURRENCY.toBv257(), BITS_FOR_BALANCE).isTrue) {
            "BITS_FOR_BALANCE is too small"
        }
        check(unsignedIntegerFitsBits(UNIX_TIME_MAX.toBv257(), BITS_FOR_UNIX_TIME).isTrue) {
            "BITS_FOR_UNIX_TIME is too small"
        }
        check(unsignedIntegerFitsBits(MAX_FWD_FEE.toBv257(), BITS_FOR_FWD_FEE).isTrue) {
            "BIT_FOR_FWD_FEE is too small"
        }
    }

    class TvmInt257Sort(
        ctx: KContext,
    ) : KBvCustomSizeSort(ctx, INT_BITS)

    class TvmCellDataSort(
        ctx: KContext,
    ) : KBvCustomSizeSort(ctx, CELL_DATA_BITS)

    // Utility sorts for arith operations
    class TvmInt257Ext1Sort(
        ctx: KContext,
    ) : KBvCustomSizeSort(ctx, INT_EXT1_BITS)

    class TvmInt257Ext256Sort(
        ctx: KContext,
    ) : KBvCustomSizeSort(ctx, INT_EXT256_BITS)

    infix fun <T : KBvSort> KExpr<T>.bvAdd(other: KExpr<T>): KExpr<T> = mkBvAddExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvMul(other: KExpr<T>): KExpr<T> = mkBvMulExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvSub(other: KExpr<T>): KExpr<T> = mkBvSubExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvUge(other: KExpr<T>): UBoolExpr = mkBvUnsignedGreaterOrEqualExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvUle(other: KExpr<T>): UBoolExpr = mkBvUnsignedLessOrEqualExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvUlt(other: KExpr<T>): UBoolExpr = mkBvUnsignedLessExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvSle(other: KExpr<T>): UBoolExpr = mkBvSignedLessOrEqualExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvUgt(other: KExpr<T>): UBoolExpr = mkBvUnsignedGreaterExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvAnd(other: KExpr<T>): KExpr<T> = mkBvAndExpr(this, other)

    fun <T : KBvSort> KExpr<T>.hasBitSet(idx: Int): UBoolExpr {
        val withMaskedApplied = this bvAnd (1.toBv(this.sort).shiftLeft(idx.toBv(sort)))
        return withMaskedApplied neq 0.toBv(this.sort)
    }
}

val KAst.tctx
    get() = ctx as TvmContext
