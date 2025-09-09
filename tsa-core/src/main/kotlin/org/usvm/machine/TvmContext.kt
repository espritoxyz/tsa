package org.usvm.machine

import io.ksmt.KContext
import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvLogicalShiftRightExpr
import io.ksmt.expr.KExpr
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
import io.ksmt.utils.BvUtils.bvMaxValueUnsigned
import io.ksmt.utils.BvUtils.toBigIntegerSigned
import io.ksmt.utils.asExpr
import io.ksmt.utils.powerOfTwo
import io.ksmt.utils.toBigInteger
import org.ton.bytecode.MethodId
import java.math.BigInteger
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
import org.usvm.machine.state.TvmCellOverflowError
import org.usvm.machine.state.TvmCellUnderflowError
import org.usvm.machine.state.TvmDictError
import org.usvm.machine.state.TvmFailureType
import org.usvm.machine.state.TvmIntegerOutOfRangeError
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.machine.state.TvmStackOverflowError
import org.usvm.machine.state.TvmStackUnderflowError
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmTypeCheckError
import org.usvm.machine.state.bvMaxValueSignedExtended
import org.usvm.machine.state.bvMinValueSignedExtended
import org.usvm.machine.state.setFailure
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.dp.AbstractGuard
import org.usvm.machine.types.dp.AbstractionForUExpr
import org.usvm.mkSizeExpr
import org.usvm.sizeSort

// TODO: There is no size sort in TVM because of absence of arrays, but we need to represent cell data as boolean arrays
//  with size no more than 1023

// TODO make it Bv16
typealias TvmSizeSort = UBv32Sort

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
    val fourValue: KBitVecValue<TvmInt257Sort> = 4.toBv257()
    val eightValue: KBitVecValue<TvmInt257Sort> = 8.toBv257()
    val zeroValue: KBitVecValue<TvmInt257Sort> = falseValue
    val minusOneValue: KBitVecValue<TvmInt257Sort> = trueValue
    val intBitsValue: KBitVecValue<TvmInt257Sort> = INT_BITS.toInt().toBv257()
    val maxTupleSizeValue: KBitVecValue<TvmInt257Sort> = MAX_TUPLE_SIZE.toBv257()
    val unitTimeMinValue: KBitVecValue<TvmInt257Sort> = UNIX_TIME_MIN.toBv257()
    val unitTimeMaxValue: KBitVecValue<TvmInt257Sort> = UNIX_TIME_MAX.toBv257()
    val min257BitValue: KExpr<TvmInt257Sort> = bvMinValueSignedExtended(intBitsValue)
    val max257BitValue: KExpr<TvmInt257Sort> = bvMaxValueSignedExtended(intBitsValue)
    val maxGramsValue: KExpr<TvmInt257Sort> = bvMaxValueUnsigned<UBvSort>(MAX_GRAMS_BITS).unsignedExtendToInteger()
    val maxTimestampValue = mkBvShiftLeftExpr(oneValue, 64.toBv257())

    val masterchain: KBitVecValue<TvmInt257Sort> = minusOneValue
    val baseChain: KBitVecValue<TvmInt257Sort> = zeroValue

    val zeroSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(0)
    val oneSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(1)
    val twoSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(2)
    val threeSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(3)
    val fourSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(4)
    val sixSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(6)
    val eightSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(8)
    val sizeExpr32: UExpr<TvmSizeSort> = mkSizeExpr(32)
    val maxDataLengthSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(MAX_DATA_LENGTH)
    val maxRefsLengthSizeExpr: UExpr<TvmSizeSort> = mkSizeExpr(MAX_REFS_NUMBER)
    val stdMsgAddrSizeExpr = mkSizeExpr(stdMsgAddrSize)

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
    val throwStackUnderflowError: (TvmState) -> Unit = setFailure(TvmStackUnderflowError)
    val throwStackOverflowError: (TvmState) -> Unit = setFailure(TvmStackOverflowError)
    val throwIntegerOverflowError: (TvmState) -> Unit = setFailure(TvmIntegerOverflowError)
    val throwIntegerOutOfRangeError: (TvmState) -> Unit = setFailure(TvmIntegerOutOfRangeError)
    val throwCellOverflowError: (TvmState) -> Unit = setFailure(TvmCellOverflowError)
    val throwUnknownCellUnderflowError: (TvmState) -> Unit =
        setFailure(TvmCellUnderflowError, TvmFailureType.UnknownError)
    val throwStructuralCellUnderflowError: (TvmState) -> Unit =
        setFailure(TvmCellUnderflowError, TvmFailureType.FixedStructuralError)
    val throwSymbolicStructuralCellUnderflowError: (TvmState) -> Unit =
        setFailure(TvmCellUnderflowError, TvmFailureType.SymbolicStructuralError)
    val throwRealCellUnderflowError: (TvmState) -> Unit = setFailure(TvmCellUnderflowError, TvmFailureType.RealError)
    val throwRealDictError: (TvmState) -> Unit = setFailure(TvmDictError, TvmFailureType.RealError)

    val sendMsgActionTag = mkBvHex("0ec3c86d", 32u)
    val reserveActionTag = mkBvHex("36e6b809", 32u)
    val bouncedMessageTagLong = 0xffffffff

    val sendMsgFeeEstimationFlag = powerOfTwo(10u).toBv257()

    fun UBoolExpr.toBv257Bool(): UExpr<TvmInt257Sort> = with(ctx) {
        mkIte(
            condition = this@toBv257Bool,
            trueBranch = trueValue,
            falseBranch = falseValue,
        )
    }

    fun Number.toBv257(): KBitVecValue<TvmInt257Sort> = mkBv(toBigInteger(), int257sort)
    fun Number.toCellSort(): KBitVecValue<TvmCellDataSort> = mkBv(toBigInteger(), cellDataSort)

    fun <Sort : UBvSort> UExpr<Sort>.signedExtendToInteger(): UExpr<TvmInt257Sort> =
        signExtendToSort(int257sort)

    fun <Sort : UBvSort> UExpr<Sort>.unsignedExtendToInteger(): UExpr<TvmInt257Sort> =
        zeroExtendToSort(int257sort)

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

    fun <Sort : UBvSort> UExpr<Sort>.extractToSizeSort(): UExpr<TvmSizeSort> =
        extractToSort(sizeSort)

    fun <Sort : UBvSort> UExpr<Sort>.extractToInt257Sort(): UExpr<TvmInt257Sort> =
        extractToSort(int257sort)

    fun <InSort : UBvSort, OutSort : UBvSort> UExpr<InSort>.extractToSort(sort: OutSort): UExpr<OutSort> {
        require(this.sort.sizeBits >= sort.sizeBits)

        return mkBvExtractExpr(sort.sizeBits.toInt() - 1, 0, this).asExpr(sort)
    }

    override fun mkBvSort(sizeBits: UInt): KBvSort = when (sizeBits) {
        INT_BITS -> int257sort
        CELL_DATA_BITS -> cellDataSort
        INT_EXT1_BITS -> int257Ext1Sort
        INT_EXT256_BITS -> int257Ext256Sort
        else -> super.mkBvSort(sizeBits)
    }

    override fun <T : KBvSort> mkBvExtractExpr(high: Int, low: Int, value: KExpr<T>): KExpr<KBvSort> {
        if (value is KBvLogicalShiftRightExpr && value.shift is KBitVecValue) {
            val maxSizeBits = value.sort.sizeBits.toInt()
            val shiftBI = (value.shift as KBitVecValue).toBigIntegerSigned()
            if (shiftBI < maxSizeBits.toBigInteger() && shiftBI >= BigInteger.ZERO) {
                val shift = shiftBI.toInt()
                val newHigh = high + shift
                val newLow = low + shift
                if (newLow >= 0 && newHigh < maxSizeBits) {
                    return super.mkBvExtractExpr(newHigh, newLow, value.arg)
                }
            }
        }
        return super.mkBvExtractExpr(high, low, value)
    }

    private fun tvmSimplifyBoolIte(
        condition: KExpr<KBoolSort>,
        trueBranch: KExpr<KBoolSort>,
        falseBranch: KExpr<KBoolSort>
    ): KExpr<KBoolSort> =
        simplifyBoolIteConstBranches(
            condition = condition,
            trueBranch = trueBranch,
            falseBranch = falseBranch,
            rewriteOr = { a, b -> simplifyOr(a, b, flat = false) },
            rewriteAnd = KContext::simplifyAnd,
            rewriteNot = KContext::simplifyNot
        ) { condition2, trueBranch2, falseBranch2 ->
            simplifyBoolIteSameConditionBranch(
                condition = condition2,
                trueBranch = trueBranch2,
                falseBranch = falseBranch2,
                rewriteAnd = KContext::simplifyAnd,
                rewriteOr = { a, b -> simplifyOr(a, b, flat = false) },
                cont = ::mkIteNoSimplify
            )
        }

    private fun <T : KSort> tvmSimplifyIte(
        condition: KExpr<KBoolSort>,
        trueBranch: KExpr<T>,
        falseBranch: KExpr<T>
    ): KExpr<T> =
        simplifyIteNotCondition(condition, trueBranch, falseBranch) { condition2, trueBranch2, falseBranch2 ->
            simplifyIteLight(condition2, trueBranch2, falseBranch2) { condition3, trueBranch3, falseBranch3 ->
                simplifyIteSameBranches(
                    condition3,
                    trueBranch3,
                    falseBranch3,
                    { a, b, c -> tvmSimplifyIte(a, b, c) },
                    { a, b -> simplifyOr(a, b, flat = false) }
                ) { condition4, trueBranch4, falseBranch4 ->
                    simplifyIteBool(condition4, trueBranch4, falseBranch4, { a, b, c -> tvmSimplifyBoolIte(a, b, c) }, ::mkIteNoSimplify)
                }
            }
        }

    override fun <T : KSort> mkIte(condition: KExpr<KBoolSort>, trueBranch: KExpr<T>, falseBranch: KExpr<T>): KExpr<T> {
        return tvmSimplifyIte(condition, trueBranch, falseBranch)
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
        val MAX_MESSAGE_CURRENCY: BigInteger = BigInteger.TEN.pow(20)

        val RECEIVE_INTERNAL_ID: MethodId = 0.toMethodId()
        val RECEIVE_EXTERNAL_ID: MethodId = (-1).toMethodId()

        const val OP_BITS: UInt = 32u

        const val TRUE_CONCRETE_VALUE = -1
        const val FALSE_CONCRETE_VALUE = 0

        val cellRefsLengthField: TvmField = TvmFieldImpl(TvmCellType, "refsLength")

        val sliceDataPosField: TvmField = TvmFieldImpl(TvmSliceType, "dataPos")
        val sliceRefPosField: TvmField = TvmFieldImpl(TvmSliceType, "refPos")
        val sliceCellField: TvmField = TvmFieldImpl(TvmSliceType, "cell")

        val dictKeyLengthField: TvmField = TvmFieldImpl(TvmDictCellType, "keyLength")

        val stdMsgAddrSize = ADDRESS_TAG_LENGTH + 1 + STD_WORKCHAIN_BITS + ADDRESS_BITS

        fun KContext.tctx(): TvmContext = this as TvmContext
    }

    class TvmInt257Sort(ctx: KContext) : KBvCustomSizeSort(ctx, INT_BITS)
    class TvmCellDataSort(ctx: KContext) : KBvCustomSizeSort(ctx, CELL_DATA_BITS)

    // Utility sorts for arith operations
    class TvmInt257Ext1Sort(ctx: KContext) : KBvCustomSizeSort(ctx, INT_EXT1_BITS)
    class TvmInt257Ext256Sort(ctx: KContext) : KBvCustomSizeSort(ctx, INT_EXT256_BITS)
}
