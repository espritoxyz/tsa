package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.KContext
import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvAddExpr
import io.ksmt.expr.KBvConcatExpr
import io.ksmt.expr.KBvLogicalShiftRightExpr
import io.ksmt.expr.KBvMulExpr
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
import io.ksmt.utils.BvUtils.bigIntValue
import io.ksmt.utils.BvUtils.shiftLeft
import io.ksmt.utils.BvUtils.toBigIntegerSigned
import io.ksmt.utils.asExpr
import io.ksmt.utils.powerOfTwo
import io.ksmt.utils.toBigInteger
import io.ksmt.utils.uncheckedCast
import org.ton.bigint.toBigInt
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.ton.bytecode.TvmQuitContinuation
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UBvSort
import org.usvm.UComponents
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UIteExpr
import org.usvm.isTrue
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
import org.usvm.machine.state.setExit
import org.usvm.machine.state.setFailure
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.memory.stack.BadSizeContext
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.utils.isIteWithConcreteLeaves
import java.math.BigInteger
import kotlin.math.min

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

    override fun <T : KBvSort> mkBvSignedLessExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<KBoolSort> {
        return mkAnd(
            mkBvSignedLessOrEqualExpr(arg0, arg1),
            mkNot(mkEq(arg0, arg1)),
        )
    }

    override fun mkNot(arg: KExpr<KBoolSort>): KExpr<KBoolSort> {
        if (arg is KIteExpr) {
            return mkIte(
                arg.condition,
                trueBranch = mkNot(arg.trueBranch),
                falseBranch = mkNot(arg.falseBranch),
            )
        }
        return super.mkNot(arg)
    }

    override fun <T : KBvSort> mkBvSignedGreaterExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<KBoolSort> {
        return mkBvSignedLessExpr(arg1, arg0)
    }

    override fun <T : KBvSort> mkBvUnsignedLessOrEqualExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<KBoolSort> {
        return mkBvSignedLessOrEqualExpr(mkBvZeroExtensionExpr(1, arg0), mkBvZeroExtensionExpr(1, arg1))
    }

    override fun <T : KBvSort> mkBvUnsignedGreaterOrEqualExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<KBoolSort> {
        return mkBvUnsignedLessOrEqualExpr(arg1, arg0)
    }

    override fun <T : KBvSort> mkBvUnsignedLessExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<KBoolSort> {
        return mkAnd(
            mkBvUnsignedLessOrEqualExpr(arg0, arg1),
            mkNot(mkEq(arg0, arg1)),
        )
    }

    override fun <T : KBvSort> mkBvUnsignedGreaterExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<KBoolSort> {
        return mkBvUnsignedLessExpr(arg1, arg0)
    }

    override fun <T : KBvSort> mkBvSignedLessOrEqualExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<KBoolSort> {
        if (arg0 is KInterpretedValue &&
            arg0.bigIntValue() == BigInteger.ZERO &&
            arg1 is KBvZeroExtensionExpr &&
            arg1.extensionSize > 0
        ) {
            return trueExpr
        }

        if (arg1 is KInterpretedValue &&
            arg1.bigIntValue() == BigInteger.ZERO &&
            arg0 is KBvZeroExtensionExpr &&
            arg0.extensionSize > 0
        ) {
            return arg0.value eq mkBv(0, arg0.value.sort)
        }

        if (arg0 is KBvZeroExtensionExpr && arg1 is KBvZeroExtensionExpr) {
            val minExtension = min(arg0.extensionSize, arg1.extensionSize)
            if (minExtension > 1) {
                val a = if (arg0.extensionSize > minExtension) {
                    mkBvZeroExtensionExpr(arg0.extensionSize - minExtension, arg0.value)
                } else {
                    arg0.value
                }
                val b = if (arg1.extensionSize > minExtension) {
                    mkBvZeroExtensionExpr(arg1.extensionSize - minExtension, arg1.value)
                } else {
                    arg1.value
                }
                return mkBvUnsignedLessOrEqualExpr(a, b)
            }
        }

        if (arg0 !is KIteExpr && arg1 is KIteExpr) {
            return mkIte(
                arg1.condition,
                trueBranch = mkBvSignedLessOrEqualExpr(arg0, arg1.trueBranch),
                falseBranch = mkBvSignedLessOrEqualExpr(arg0, arg1.falseBranch),
            )
        }

        if (arg1 !is KIteExpr && arg0 is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvSignedLessOrEqualExpr(arg0.trueBranch, arg1),
                falseBranch = mkBvSignedLessOrEqualExpr(arg0.falseBranch, arg1),
            )
        }

        // HACK: we assume that overflows with the given constraints don't happen

        if (arg0 is KBitVecValue && arg0.bigIntValue() == BigInteger.ZERO && arg1 is KBvMulExpr) {
            val (a, b) = arg1.arg0 to arg1.arg1
            return mkOr(
                a eq mkBv(0, a.sort),
                b eq mkBv(0, b.sort),
                mkEq(
                    mkBvSignedLessOrEqualExpr(arg0, a),
                    mkBvSignedLessOrEqualExpr(arg0, b),
                ),
            )
        }

        if (arg1 is KBitVecValue && arg1.bigIntValue() == BigInteger.ZERO && arg0 is KBvMulExpr) {
            val (a, b) = arg0.arg0 to arg0.arg1
            return mkOr(
                a eq mkBv(0, a.sort),
                b eq mkBv(0, b.sort),
                mkNot(
                    mkEq(
                        mkBvSignedLessOrEqualExpr(arg1, a),
                        mkBvSignedLessOrEqualExpr(arg1, b),
                    ),
                ),
            )
        }

        return super.mkBvSignedLessOrEqualExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvSignedGreaterOrEqualExpr(
        arg0: KExpr<T>,
        arg1: KExpr<T>,
    ): KExpr<KBoolSort> = mkBvSignedLessOrEqualExpr(arg1, arg0)

    override fun <T : KBvSort> mkBvExtractExpr(
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

        if (value is KBvZeroExtensionExpr && value.value.sort.sizeBits <= low.toUInt()) {
            return mkBv(0, (high - low + 1).toUInt())
        }

        if (value is KBvZeroExtensionExpr && value.value.sort.sizeBits > high.toUInt()) {
            return mkBvExtractExpr(high, low, value.value)
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

        if (low == 0 && value is KBvZeroExtensionExpr && value.value.sort.sizeBits <= high.toUInt()) {
            val bits =
                value.value.sort.sizeBits
                    .toInt()
            return mkBvZeroExtensionExpr(high + 1 - bits, value.value)
        }

        if (value is KIteExpr) {
            return mkIte(
                value.condition,
                trueBranch = mkBvExtractExpr(high, low, value.trueBranch),
                falseBranch = mkBvExtractExpr(high, low, value.falseBranch),
            )
        }

        if (value is KBvConcatExpr && low.toUInt() >= value.arg1.sort.sizeBits) {
            val sub =
                value.arg1.sort.sizeBits
                    .toInt()
            return mkBvExtractExpr(high = high - sub, low = low - sub, value.arg0)
        }

        if (value is KBvConcatExpr && low.toUInt() < value.arg1.sort.sizeBits && high.toUInt() >= value.arg1.sort.sizeBits) {
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
        return super.mkBvLogicalShiftRightExpr(arg, shift)
    }

    override fun <T : KBvSort> mkBvShiftLeftExpr(
        arg: KExpr<T>,
        shift: KExpr<T>,
    ): KExpr<T> {
        if (shift is KIteExpr && shift.isIteWithConcreteLeaves()) {
            return mkIte(
                shift.condition,
                trueBranch = mkBvShiftLeftExpr(arg, shift.trueBranch),
                falseBranch = mkBvShiftLeftExpr(arg, shift.falseBranch),
            )
        }
        if (arg is KIteExpr && shift is KBitVecValue) {
            return mkIte(
                arg.condition,
                trueBranch = mkBvShiftLeftExpr(arg.trueBranch, shift),
                falseBranch = mkBvShiftLeftExpr(arg.falseBranch, shift),
            )
        }
        val bits = arg.sort.sizeBits.toInt()
        if (shift is KBitVecValue && arg is KBvLogicalShiftRightExpr && arg.shift == shift && shift.bigIntValue() < bits.toBigInt()) {
            val expr = mkBvExtractExpr(high = bits - 1, low = shift.intValue(), arg.arg)
            return mkBvConcatExpr(expr, mkBv(0, shift.intValue().toUInt())).uncheckedCast()
        }
        if (arg is KBvShiftLeftExpr && arg.shift is KBitVecValue && shift is KBitVecValue) {
            val maxSizeBits = arg.sort.sizeBits.toInt()
            val shiftBI1 = (arg.shift as KBitVecValue).toBigIntegerSigned()
            val shiftBI2 = shift.toBigIntegerSigned()
            if (shiftBI1 + shiftBI2 < maxSizeBits.toBigInteger() &&
                shiftBI1 >= BigInteger.ZERO &&
                shiftBI2 >= BigInteger.ZERO
            ) {
                return mkBvShiftLeftExpr(arg.arg, mkBvAddExpr(arg.shift, shift))
            }
        }
        if (arg is KBvSignExtensionExpr &&
            shift is KBitVecValue &&
            shift.bigIntValue() <= arg.extensionSize.toBigInteger()
        ) {
            val diff = arg.extensionSize - shift.intValue()
            val concat = mkBvConcatExpr(arg.value, mkBv(0, shift.intValue().toUInt()))
            return if (diff > 0) {
                mkBvSignExtensionExpr(diff, concat).uncheckedCast()
            } else {
                concat.uncheckedCast()
            }
        }
        if (arg is KBvZeroExtensionExpr &&
            shift is KBitVecValue &&
            shift.bigIntValue() <= arg.extensionSize.toBigInteger()
        ) {
            val diff = arg.extensionSize - shift.intValue()
            val concat = mkBvConcatExpr(arg.value, mkBv(0, shift.intValue().toUInt()))
            return if (diff > 0) {
                mkBvZeroExtensionExpr(diff, concat).uncheckedCast()
            } else {
                concat.uncheckedCast()
            }
        }
        return super.mkBvShiftLeftExpr(arg, shift)
    }

    override fun <T : KBvSort, S : KBvSort> mkBvConcatExpr(arg0: KExpr<T>, arg1: KExpr<S>): KExpr<KBvSort> {
        if (arg0 is KIteExpr && arg1 !is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvConcatExpr(arg0.trueBranch, arg1),
                falseBranch = mkBvConcatExpr(arg0.falseBranch, arg1),
            )
        }
        if (arg1 is KIteExpr && arg0 !is KIteExpr) {
            return mkIte(
                arg1.condition,
                trueBranch = mkBvConcatExpr(arg0, arg1.trueBranch),
                falseBranch = mkBvConcatExpr(arg0, arg1.falseBranch),
            )
        }
        return super.mkBvConcatExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvOrExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<T> {
        if (arg0 is KBitVecValue && arg1 !is KBitVecValue) {
            return mkBvOrExpr(arg1, arg0)
        }
        if (arg1 is KBitVecValue && arg0 is KBvZeroExtensionExpr) {
            val bits = arg1.sort.sizeBits.toInt()
            val part1 = mkBvExtractExpr(high = bits - 1, low = bits - arg0.extensionSize, arg1)
            val part2Const = mkBvExtractExpr(high = bits - arg0.extensionSize - 1, low = 0, arg1)
            val part2 = mkBvOrExpr(part2Const, arg0.value)
            return mkBvConcatExpr(part1, part2).uncheckedCast()
        }
        if (arg0 is KIteExpr && arg1 !is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvOrExpr(arg0.trueBranch, arg1),
                falseBranch = mkBvOrExpr(arg0.falseBranch, arg1),
            )
        }
        if (arg1 is KIteExpr && arg0 !is KIteExpr) {
            return mkBvOrExpr(arg1, arg0)
        }
        if (arg0 is KIteExpr && arg1 is KIteExpr && arg0.condition == arg1.condition) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvOrExpr(arg0.trueBranch, arg1.trueBranch),
                falseBranch = mkBvOrExpr(arg0.falseBranch, arg1.falseBranch),
            )
        }
        return super.mkBvOrExpr(arg0, arg1)
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

    override fun <T : KSort> mkIte(
        condition: KExpr<KBoolSort>,
        trueBranch: KExpr<T>,
        falseBranch: KExpr<T>,
    ): KExpr<T> {
        if (trueBranch is KIteExpr && trueBranch.falseBranch == falseBranch) {
            return mkIte(
                mkAnd(condition, trueBranch.condition),
                trueBranch.trueBranch,
                falseBranch,
            )
        }
        return tvmSimplifyIte(condition, trueBranch, falseBranch)
    }

    override fun mkAnd(lhs: KExpr<KBoolSort>, rhs: KExpr<KBoolSort>, flat: Boolean, order: Boolean): KExpr<KBoolSort> {
        return mkAnd(listOf(lhs, rhs), flat, order)
    }

    override fun mkAnd(args: List<KExpr<KBoolSort>>, flat: Boolean, order: Boolean): KExpr<KBoolSort> {
        val ites = args.mapNotNull { it as? KIteExpr }.groupBy { it.condition }
        val nonItes = args.filter { it !is KIteExpr }
        val groupedItes = ites.values.map { itesWithSameCondition ->
            mkIte(
                condition = itesWithSameCondition.first().condition,
                trueBranch = mkAnd(itesWithSameCondition.map { it.trueBranch }, flat, order),
                falseBranch = mkAnd(itesWithSameCondition.map { it.falseBranch }, flat, order),
            )
        }
        return super.mkAnd(nonItes + groupedItes, flat, order)
    }

    override fun mkOr(lhs: KExpr<KBoolSort>, rhs: KExpr<KBoolSort>, flat: Boolean, order: Boolean): KExpr<KBoolSort> {
        return mkOr(listOf(lhs, rhs), flat, order)
    }

    override fun mkOr(args: List<KExpr<KBoolSort>>, flat: Boolean, order: Boolean): KExpr<KBoolSort> {
        val ites = args.mapNotNull { it as? KIteExpr }.groupBy { it.condition }
        val nonItes = args.filter { it !is KIteExpr }
        val groupedItes = ites.values.map { itesWithSameCondition ->
            mkIte(
                condition = itesWithSameCondition.first().condition,
                trueBranch = mkOr(itesWithSameCondition.map { it.trueBranch }, flat, order),
                falseBranch = mkOr(itesWithSameCondition.map { it.falseBranch }, flat, order),
            )
        }
        return super.mkOr(nonItes + groupedItes, flat, order)
    }

    override fun <T : KSort> mkEq(
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

        // HACK: we assume that overflows with the given constraints don't happen
        if (lhs is KBitVecValue && lhs.bigIntValue() == BigInteger.ZERO && rhs is KBvMulExpr) {
            val (a, b) = rhs.arg0 to rhs.arg1
            if (a.sort is KBvSort && b.sort is KBvSort) {
                return mkOr(
                    a eq mkBv(0, a.sort as KBvSort).uncheckedCast(),
                    b eq mkBv(0, b.sort as KBvSort).uncheckedCast(),
                )
            }
        }

        if (lhs !is KIteExpr && rhs is KIteExpr) {
            return mkIte(
                rhs.condition,
                trueBranch = mkEq(rhs.trueBranch, lhs),
                falseBranch = mkEq(rhs.falseBranch, lhs),
            )
        }

        if (lhs is KIteExpr && rhs !is KIteExpr) {
            return mkIte(
                lhs.condition,
                trueBranch = mkEq(lhs.trueBranch, rhs),
                falseBranch = mkEq(lhs.falseBranch, rhs),
            )
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

        if (lhs is KBitVecValue && rhs is KBvZeroExtensionExpr && rhs.extensionSize > 0) {
            val bits = rhs.sort.sizeBits.toInt()
            val prefix = mkBvExtractExpr<KBvSort>(high = bits - 1, low = bits - rhs.extensionSize, lhs.uncheckedCast())
            if (prefix.bigIntValue() == BigInteger.ZERO) {
                return mkEq(
                    rhs.value,
                    mkBvExtractExpr<KBvSort>(high = bits - rhs.extensionSize - 1, low = 0, lhs.uncheckedCast()),
                )
            }
        }

        if (lhs is KBvConcatExpr && rhs is KBvConcatExpr && lhs.arg1 == rhs.arg1) {
            return mkEq(lhs.arg0, rhs.arg0)
        }

        if (lhs is KBvConcatExpr && rhs is KBvConcatExpr && lhs.arg0 == rhs.arg0) {
            return mkEq(lhs.arg1, rhs.arg1)
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

    override fun <T : KBvSort> mkBvAddExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<T> {
        if (arg0 is KIteExpr && arg1 !is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvAddExpr(arg0.trueBranch, arg1),
                falseBranch = mkBvAddExpr(arg0.falseBranch, arg1),
            )
        }
        if (arg0 !is KIteExpr && arg1 is KIteExpr) {
            return mkBvAddExpr(arg1, arg0)
        }
        return super.mkBvAddExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvMulExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<T> {
        if (arg0 is KIteExpr && arg1 !is KIteExpr) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvMulExpr(arg0.trueBranch, arg1),
                falseBranch = mkBvMulExpr(arg0.falseBranch, arg1),
            )
        }
        if (arg0 !is KIteExpr && arg1 is KIteExpr) {
            return mkBvMulExpr(arg1, arg0)
        }
        return super.mkBvMulExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvZeroExtensionExpr(
        extensionSize: Int,
        value: KExpr<T>,
    ): KExpr<KBvSort> {
        if (value is KBvZeroExtensionExpr) {
            return mkBvZeroExtensionExpr(extensionSize + value.extensionSize, value.value)
        }
        if (value is KIteExpr) {
            return mkIte(
                value.condition,
                trueBranch = mkBvZeroExtensionExpr(extensionSize, value.trueBranch),
                falseBranch = mkBvZeroExtensionExpr(extensionSize, value.falseBranch),
            )
        }
        return super.mkBvZeroExtensionExpr(extensionSize, value)
    }

    override fun <T : KBvSort> mkBvAndExpr(arg0: KExpr<T>, arg1: KExpr<T>): KExpr<T> {
        if (arg0 is KIteExpr && arg0.trueBranch is KBitVecValue && arg0.falseBranch is KBitVecValue) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvAndExpr(arg1, arg0.trueBranch),
                falseBranch = mkBvAndExpr(arg1, arg0.falseBranch),
            )
        }
        if (arg1 is KIteExpr && arg1.trueBranch is KBitVecValue && arg1.falseBranch is KBitVecValue) {
            return mkBvAndExpr(arg1, arg0)
        }
        if (arg0 is KIteExpr && arg1 is KIteExpr && arg0.condition == arg1.condition) {
            return mkIte(
                arg0.condition,
                trueBranch = mkBvAndExpr(arg0.trueBranch, arg1.trueBranch),
                falseBranch = mkBvAndExpr(arg1.falseBranch, arg1.falseBranch),
            )
        }
        return super.mkBvAndExpr(arg0, arg1)
    }

    override fun <T : KBvSort> mkBvNotExpr(value: KExpr<T>): KExpr<T> {
        if (value is KIteExpr) {
            return mkIte(
                condition = value.condition,
                trueBranch = mkBvNotExpr(value.trueBranch),
                falseBranch = mkBvNotExpr(value.falseBranch),
            )
        }
        return super.mkBvNotExpr(value)
    }

    override fun <T : KBvSort> mkBvNegationExpr(value: KExpr<T>): KExpr<T> {
        if (value is KIteExpr) {
            return mkIte(
                condition = value.condition,
                trueBranch = mkBvNegationExpr(value.trueBranch),
                falseBranch = mkBvNegationExpr(value.falseBranch),
            )
        }
        return super.mkBvNegationExpr(value)
    }

    companion object {
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

        // Minimum incoming message value/balance in nanotons
        const val MIN_MESSAGE_CURRENCY: Long = 100_000_000

        // Maximum incoming message value/balance in nanotons
        val MAX_MESSAGE_CURRENCY: BigInteger = BigInteger.TEN.pow(19)

        const val MAX_FWD_FEE = 1166940800

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

    infix fun <T : KBvSort> KExpr<T>.bvUgt(other: KExpr<T>): UBoolExpr = mkBvUnsignedGreaterExpr(this, other)

    infix fun <T : KBvSort> KExpr<T>.bvAnd(other: KExpr<T>): KExpr<T> = mkBvAndExpr(this, other)

    fun <T : KBvSort> KExpr<T>.hasBitSet(idx: Int): UBoolExpr {
        val withMaskedApplied = this bvAnd (1.toBv(this.sort).shiftLeft(idx.toBv(sort)))
        return withMaskedApplied neq 0.toBv(this.sort)
    }
}

val KAst.tctx
    get() = ctx as TvmContext
