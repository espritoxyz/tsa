package org.usvm.machine.types

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentList
import org.ton.Endian
import org.ton.FixedSizeDataLabel
import org.ton.TlbAddressByRef
import org.ton.TlbBasicMsgAddrLabel
import org.ton.TlbBitArrayByRef
import org.ton.TlbBitArrayOfConcreteSize
import org.ton.TlbBuiltinLabel
import org.ton.TlbCoinsLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbIntegerLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbLabel
import org.ton.TlbMaybeRefLabel
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.SwitchPrefix
import org.ton.defaultTlbMaybeRefLabel
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocSliceFromData
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.tctx
import org.usvm.machine.types.memory.ConcreteSizeBlockField
import org.usvm.machine.types.memory.SliceRefField
import org.usvm.machine.types.memory.SymbolicSizeBlockField
import org.usvm.machine.types.memory.extractInt
import org.usvm.machine.types.memory.generateGuardForSwitch
import org.usvm.machine.types.memory.readConcreteBv
import org.usvm.mkSizeExpr
import org.usvm.mkSizeLtExpr
import org.usvm.sizeSort
import kotlin.math.min

sealed interface TvmCellDataTypeRead<ReadResult> {
    fun readFromConstant(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        data: String,
    ): ReadResult?

    fun extractTlbValueIfPossible(
        curStructure: KnownTypePrefix,
        label: TlbBuiltinLabel,
        address: UHeapRef,
        path: PersistentList<Int>,
        state: TvmState,
        leftTlbDepth: Int,
    ): ReadResult?

    fun createLeftBitsDataLoad(leftBits: UExpr<TvmSizeSort>): TvmCellDataTypeRead<ReadResult>? = null

    fun defaultTlbLabel(): TlbLabel?

    fun defaultTlbLabelSize(
        state: TvmState,
        ref: UConcreteHeapRef,
        path: List<Int>,
    ): UExpr<TvmSizeSort>?

    fun sizeIsBad(
        dataSuffix: UExpr<TvmContext.TvmCellDataSort>,
        dataSuffixLength: UExpr<TvmSizeSort>,
    ): UBoolExpr
}

sealed interface SizedCellDataTypeRead {
    val sizeBits: UExpr<TvmSizeSort>
}

data class TvmCellDataIntegerRead(
    override val sizeBits: UExpr<TvmSizeSort>,
    val isSigned: Boolean,
    val endian: Endian,
) : TvmCellDataTypeRead<UExprReadResult<TvmContext.TvmInt257Sort>>,
    SizedCellDataTypeRead {
    override fun readFromConstant(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        data: String,
    ): UExprReadResult<TvmContext.TvmInt257Sort> {
        val intExpr = extractInt(offset, sizeBits, data, isSigned)
        return UExprReadResult(intExpr)
    }

    override fun extractTlbValueIfPossible(
        curStructure: KnownTypePrefix,
        label: TlbBuiltinLabel,
        address: UHeapRef,
        path: PersistentList<Int>,
        state: TvmState,
        leftTlbDepth: Int,
    ): UExprReadResult<TvmContext.TvmInt257Sort>? =
        with(state.ctx) {
            if (label !is TlbIntegerLabelOfConcreteSize) {
                return null
            }

            // no checks for sizeBits are made: they should be done before this call

            val field = ConcreteSizeBlockField(label.concreteSize, curStructure.id, path)
            val value = state.memory.readField(address, field, field.getSort(this))

            val result =
                if (isSigned) {
                    value.signedExtendToInteger()
                } else {
                    value.unsignedExtendToInteger()
                }

            UExprReadResult(result)
        }

    override fun defaultTlbLabel(): TlbLabel =
        if (sizeBits is KInterpretedValue) {
            TlbIntegerLabelOfConcreteSize(sizeBits.intValue(), isSigned, endian)
        } else {
            TlbIntegerLabelOfSymbolicSize(
                isSigned,
                endian,
                arity = 0,
            ) { _, _ -> TlbIntegerLabel.SizeExprBits(sizeBits) }
        }

    override fun defaultTlbLabelSize(
        state: TvmState,
        ref: UConcreteHeapRef,
        path: List<Int>,
    ): UExpr<TvmSizeSort> = sizeBits

    override fun sizeIsBad(
        dataSuffix: UExpr<TvmContext.TvmCellDataSort>,
        dataSuffixLength: UExpr<TvmSizeSort>,
    ): UBoolExpr =
        with(dataSuffix.ctx.tctx()) {
            mkSizeLtExpr(dataSuffixLength, sizeBits)
        }
}

class TvmCellMaybeConstructorBitRead(
    val ctx: TvmContext,
) : TvmCellDataTypeRead<UExprReadResult<UBoolSort>>,
    SizedCellDataTypeRead {
    override val sizeBits: UExpr<TvmSizeSort>
        get() = ctx.oneSizeExpr

    override fun readFromConstant(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        data: String,
    ): UExprReadResult<UBoolSort> =
        with(offset.tctx) {
            val bit = extractInt(offset, oneSizeExpr, data, isSigned = false)
            val boolExpr =
                mkIte(
                    bit eq zeroValue,
                    trueBranch = { falseExpr },
                    falseBranch = { trueExpr },
                )
            UExprReadResult(boolExpr)
        }

    override fun extractTlbValueIfPossible(
        curStructure: KnownTypePrefix,
        label: TlbBuiltinLabel,
        address: UHeapRef,
        path: PersistentList<Int>,
        state: TvmState,
        leftTlbDepth: Int,
    ): UExprReadResult<UBoolSort>? =
        with(state.ctx) {
            if (label !is TlbMaybeRefLabel) {
                return null
            }

            val newPath = path.add(curStructure.id)

            val switchStruct =
                label.internalStructure as? SwitchPrefix
                    ?: error("structure of TlbMaybeRefLabel must be switch")

            val possibleVariants =
                state.dataCellInfoStorage.mapper.calculatedTlbLabelInfo.getPossibleSwitchVariants(
                    switchStruct,
                    leftTlbDepth,
                )
            val trueVariant = possibleVariants.indexOfFirst { it.key == "1" }

            if (trueVariant == -1) {
                return@with UExprReadResult(falseExpr)
            }

            val expr = generateGuardForSwitch(switchStruct, trueVariant, possibleVariants, state, address, newPath)
            UExprReadResult(expr)
        }

    override fun defaultTlbLabel(): TlbLabel = defaultTlbMaybeRefLabel

    override fun defaultTlbLabelSize(
        state: TvmState,
        ref: UConcreteHeapRef,
        path: List<Int>,
    ) = ctx.oneSizeExpr

    override fun sizeIsBad(
        dataSuffix: UExpr<TvmContext.TvmCellDataSort>,
        dataSuffixLength: UExpr<TvmSizeSort>,
    ): UBoolExpr =
        with(dataSuffix.ctx.tctx()) {
            mkSizeLtExpr(dataSuffixLength, oneSizeExpr)
        }
}

// As a read result expects address length + slice with the address
class TvmCellDataMsgAddrRead(
    val ctx: TvmContext,
) : TvmCellDataTypeRead<UExprPairReadResult<TvmSizeSort, UAddressSort>> {
    override fun readFromConstant(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        data: String,
    ): UExprPairReadResult<TvmSizeSort, UAddressSort>? =
        with(offset.tctx) {
            val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
            val twoDataBits =
                if (concreteOffset != null) {
                    data.substring(concreteOffset, min(concreteOffset + 2, data.length))
                } else {
                    null
                }
            if (twoDataBits == "00") {
                val value = state.allocSliceFromData(mkBv(0, sizeBits = 2u))
                state.dataCellInfoStorage.mapper.addAddressSlice(value)

                UExprPairReadResult(twoSizeExpr, value)
            } else {
                null
            }
        }

    override fun extractTlbValueIfPossible(
        curStructure: KnownTypePrefix,
        label: TlbBuiltinLabel,
        address: UHeapRef,
        path: PersistentList<Int>,
        state: TvmState,
        leftTlbDepth: Int,
    ): UExprPairReadResult<TvmSizeSort, UAddressSort>? =
        when (label) {
            is TlbAddressByRef -> {
                val field = SliceRefField(curStructure.id, path)
                val value = state.memory.readField(address, field, field.getSort(ctx))
                val length = state.getSliceRemainingBitsCount(value)
                UExprPairReadResult(length, value)
            }

            is TlbBasicMsgAddrLabel ->
                with(state.ctx) {
                    val struct =
                        (label as TlbCompositeLabel).internalStructure as? SwitchPrefix
                            ?: error("structure of TlbFullMsgAddrLabel must be switch")

                    if (struct.variants.size > 1) {
                        TODO()
                    }

                    val variant = struct.variants.single()

                    val rest =
                        variant.struct as? KnownTypePrefix
                            ?: error("Unexpected structure: ${variant.struct}")
                    val restLabel =
                        rest.typeLabel as? FixedSizeDataLabel
                            ?: error("Unexpected label: ${rest.typeLabel}")
                    val restField = ConcreteSizeBlockField(restLabel.concreteSize, rest.id, path.add(curStructure.id))
                    val restValue = state.memory.readField(address, restField, restField.getSort(this))

                    val prefix = mkBv(variant.key, variant.key.length.toUInt())

                    val content = mkBvConcatExpr(prefix, restValue)

                    val value = state.allocSliceFromData(content)
                    state.dataCellInfoStorage.mapper.addAddressSlice(value)

                    val length = mkSizeExpr(TvmContext.stdMsgAddrSize)
                    UExprPairReadResult(length, value)
                }

            else -> {
                null
            }
        }

    override fun defaultTlbLabel() = TlbBasicMsgAddrLabel

    override fun defaultTlbLabelSize(
        state: TvmState,
        ref: UConcreteHeapRef,
        path: List<Int>,
    ) = ctx.mkSizeExpr(TvmContext.stdMsgAddrSize)

    override fun sizeIsBad(
        dataSuffix: UExpr<TvmContext.TvmCellDataSort>,
        dataSuffixLength: UExpr<TvmSizeSort>,
    ): UBoolExpr =
        with(dataSuffix.ctx.tctx()) {
            TODO()
        }
}

data class TvmCellDataBitArrayRead(
    override val sizeBits: UExpr<TvmSizeSort>,
) : TvmCellDataTypeRead<UExprReadResult<UAddressSort>>,
    SizedCellDataTypeRead {
    override fun readFromConstant(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        data: String,
    ): UExprReadResult<UAddressSort>? {
        return if (offset is KInterpretedValue) {
            val bits =
                readConcreteBv(state.ctx, offset.intValue(), data, sizeBits)
                    ?: return null
            val slice = state.allocSliceFromData(bits)
            UExprReadResult(slice)
        } else {
            null
        }
    }

    override fun extractTlbValueIfPossible(
        curStructure: KnownTypePrefix,
        label: TlbBuiltinLabel,
        address: UHeapRef,
        path: PersistentList<Int>,
        state: TvmState,
        leftTlbDepth: Int,
    ): UExprReadResult<UAddressSort>? =
        with(state.ctx) {
            when (label) {
                is FixedSizeDataLabel -> {
                    val field = ConcreteSizeBlockField(label.concreteSize, curStructure.id, path)
                    val fieldValue = state.memory.readField(address, field, field.getSort(this))
                    UExprReadResult(state.allocSliceFromData(fieldValue))
                }

                is TlbBitArrayByRef -> {
                    val field = SliceRefField(curStructure.id, path)
                    val value = state.memory.readField(address, field, field.getSort(this))
                    UExprReadResult(value)
                }

                is TlbAddressByRef -> {
                    val field = SliceRefField(curStructure.id, path)
                    val value = state.memory.readField(address, field, field.getSort(this))
                    UExprReadResult(value)
                }

                is TlbBasicMsgAddrLabel -> {
                    val struct =
                        (label as TlbCompositeLabel).internalStructure as? SwitchPrefix
                            ?: error("structure of TlbFullMsgAddrLabel must be switch")

                    if (struct.variants.size > 1) {
                        TODO()
                    }

                    val variant = struct.variants.single()

                    val rest =
                        variant.struct as? KnownTypePrefix
                            ?: error("Unexpected structure: ${variant.struct}")
                    val restLabel =
                        rest.typeLabel as? FixedSizeDataLabel
                            ?: error("Unexpected label: ${rest.typeLabel}")
                    val restField = ConcreteSizeBlockField(restLabel.concreteSize, rest.id, path.add(curStructure.id))
                    val restValue = state.memory.readField(address, restField, restField.getSort(this))

                    val prefix = mkBv(variant.key, variant.key.length.toUInt())

                    val content = mkBvConcatExpr(prefix, restValue)

                    val value = state.allocSliceFromData(content)
                    UExprReadResult(value)
                }

                else -> {
                    null
                }
            }
        }

    override fun createLeftBitsDataLoad(leftBits: UExpr<TvmSizeSort>): TvmCellDataBitArrayRead =
        TvmCellDataBitArrayRead(leftBits)

    override fun defaultTlbLabel(): TlbLabel? =
        if (sizeBits is KInterpretedValue) {
            TlbBitArrayOfConcreteSize(sizeBits.intValue())
        } else {
            null
        }

    override fun defaultTlbLabelSize(
        state: TvmState,
        ref: UConcreteHeapRef,
        path: List<Int>,
    ) = sizeBits

    override fun sizeIsBad(
        dataSuffix: UExpr<TvmContext.TvmCellDataSort>,
        dataSuffixLength: UExpr<TvmSizeSort>,
    ): UBoolExpr =
        with(dataSuffix.ctx.tctx()) {
            mkSizeLtExpr(dataSuffixLength, sizeBits)
        }
}

// As a read result expects bitvector of size 4 (coin prefix) + coin value as int257
class TvmCellDataCoinsRead(
    val ctx: TvmContext,
) : TvmCellDataTypeRead<UExprPairReadResult<KBvSort, TvmContext.TvmInt257Sort>> {
    override fun readFromConstant(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        data: String,
    ): UExprPairReadResult<KBvSort, TvmContext.TvmInt257Sort>? =
        with(offset.tctx) {
            val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
            val fourDataBits =
                if (concreteOffset != null) {
                    data.substring(concreteOffset, min(concreteOffset + 4, data.length))
                } else {
                    null
                }

            if (fourDataBits == "0000") {
                UExprPairReadResult(mkBv(0, mkBvSort(4u)), zeroValue)
            } else {
                null
            }
        }

    override fun extractTlbValueIfPossible(
        curStructure: KnownTypePrefix,
        label: TlbBuiltinLabel,
        address: UHeapRef,
        path: PersistentList<Int>,
        state: TvmState,
        leftTlbDepth: Int,
    ): UExprPairReadResult<KBvSort, TvmContext.TvmInt257Sort>? {
        if (label !is TlbCoinsLabel) {
            return null
        }

        val newPath = path.add(curStructure.id)
        return extractTlbValueFromTlbCoinsLabelFields(address, newPath, state)
    }

    private fun extractTlbValueFromTlbCoinsLabelFields(
        ref: UHeapRef,
        path: List<Int>,
        state: TvmState,
    ): UExprPairReadResult<KBvSort, TvmContext.TvmInt257Sort> =
        with(state.ctx) {
            val lengthStruct = TlbCoinsLabel.internalStructure as KnownTypePrefix
            check(lengthStruct.typeLabel is TlbIntegerLabelOfConcreteSize)

            val gramsStruct = lengthStruct.rest as KnownTypePrefix
            check(gramsStruct.typeLabel is TlbIntegerLabelOfSymbolicSize)

            val gramsField = SymbolicSizeBlockField(gramsStruct.typeLabel.lengthUpperBound, gramsStruct.id, path)
            val gramsValue =
                state.memory
                    .readField(
                        ref,
                        gramsField,
                        gramsField.getSort(this),
                    ).unsignedExtendToInteger()

            val lengthField = ConcreteSizeBlockField(lengthStruct.typeLabel.concreteSize, lengthStruct.id, path)
            val lengthValue = state.memory.readField(ref, lengthField, lengthField.getSort(this))

            UExprPairReadResult(lengthValue, gramsValue)
        }

    override fun defaultTlbLabel() = TlbCoinsLabel

    override fun defaultTlbLabelSize(
        state: TvmState,
        ref: UConcreteHeapRef,
        path: List<Int>,
    ) = with(ctx) {
        val value = extractTlbValueFromTlbCoinsLabelFields(ref, path, state)
        val coinsLength = mkBvShiftLeftExpr(value.first.zeroExtendToSort(sizeSort), threeSizeExpr)
        val fullLength = mkBvAddExpr(coinsLength, fourSizeExpr)
        fullLength
    }

    override fun sizeIsBad(
        dataSuffix: UExpr<TvmContext.TvmCellDataSort>,
        dataSuffixLength: UExpr<TvmSizeSort>,
    ): UBoolExpr =
        with(dataSuffix.ctx.tctx()) {
            TODO()
        }
}

fun <ReadResult> TvmCellDataTypeRead<ReadResult>.isEmptyRead(ctx: TvmContext): UBoolExpr =
    with(ctx) {
        when (this@isEmptyRead) {
            is SizedCellDataTypeRead -> sizeBits eq zeroSizeExpr
            is TvmCellDataMsgAddrRead, is TvmCellDataCoinsRead -> falseExpr
        }
    }
