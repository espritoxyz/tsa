package org.usvm.machine.types.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentList
import org.ton.FixedSizeDataLabel
import org.ton.TlbAddressByRef
import org.ton.TlbBasicMsgAddrLabel
import org.ton.TlbBitArrayByRef
import org.ton.TlbBitArrayOfConcreteSize
import org.ton.TlbBuiltinLabel
import org.ton.TlbCoinsLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbFullMsgAddrLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbMaybeRefLabel
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.SwitchPrefix
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocSliceFromData
import org.usvm.machine.state.extractIntFromShiftedData
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmCellDataCoinsRead
import org.usvm.machine.types.TvmCellDataIntegerRead
import org.usvm.machine.types.TvmCellDataMsgAddrRead
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmCellDataTypeReadValue
import org.usvm.machine.types.TvmCellMaybeConstructorBitRead
import org.usvm.machine.types.UExprPairReadResult
import org.usvm.machine.types.UExprReadResult
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import kotlin.math.min

/**
 * This function must be called only after checking that [TlbBuiltinLabel] accepts this [read].
 * */
fun <ReadResult : TvmCellDataTypeReadValue> TlbBuiltinLabel.extractTlbValueIfPossible(
    curStructure: KnownTypePrefix,
    read: TvmCellDataTypeRead<ReadResult>,
    address: UHeapRef,
    path: PersistentList<Int>,
    state: TvmState,
    leftTlbDepth: Int,
): ReadResult? = with(state.ctx) {
    check(curStructure.typeLabel == this@extractTlbValueIfPossible)
    when (this@extractTlbValueIfPossible) {
        is TlbCoinsLabel -> {
            if (read !is TvmCellDataCoinsRead) {
                return null
            }

            val newPath = path.add(curStructure.id)

            val lengthStruct = internalStructure as KnownTypePrefix
            check(lengthStruct.typeLabel is TlbIntegerLabelOfConcreteSize)

            val gramsStruct = lengthStruct.rest as KnownTypePrefix
            check(gramsStruct.typeLabel is TlbIntegerLabelOfSymbolicSize)

            val gramsField = SymbolicSizeBlockField(gramsStruct.typeLabel.lengthUpperBound, gramsStruct.id, newPath)
            val gramsValue = state.memory.readField(address, gramsField, gramsField.getSort()).unsignedExtendToInteger()

            val lengthField = ConcreteSizeBlockField(lengthStruct.typeLabel.concreteSize, lengthStruct.id, newPath)
            val lengthValue = state.memory.readField(address, lengthField, lengthField.getSort())

            UExprPairReadResult(lengthValue, gramsValue).uncheckedCast<Any, ReadResult>()
        }

        is TlbIntegerLabelOfConcreteSize -> {
            if (read !is TvmCellDataIntegerRead) {
                return null
            }

            // no checks for sizeBits are made: they should be done before this call

            val field = ConcreteSizeBlockField(concreteSize, curStructure.id, path)
            val value = state.memory.readField(address, field, field.getSort())

            val result = if (read.isSigned) {
                value.signedExtendToInteger()
            } else {
                value.unsignedExtendToInteger()
            }

            UExprReadResult(result).uncheckedCast<Any, ReadResult>()
        }

        is TlbBitArrayOfConcreteSize -> {
            if (read !is TvmCellDataBitArrayRead) {
                return@with null
            }

            val field = ConcreteSizeBlockField(concreteSize, curStructure.id, path)
            val fieldValue = state.memory.readField(address, field, field.getSort())

            UExprReadResult(state.allocSliceFromData(fieldValue)).uncheckedCast<Any, ReadResult>()
        }

        is TlbBitArrayByRef -> {
            if (read !is TvmCellDataBitArrayRead) {
                return@with null
            }

            val field = SliceRefField(curStructure.id, path)
            val value = state.memory.readField(address, field, field.getSort())

            UExprReadResult(value).uncheckedCast<Any, ReadResult>()
        }

        is TlbAddressByRef -> {
            if (read !is TvmCellDataMsgAddrRead && read !is TvmCellDataBitArrayRead) {
                return@with null
            }

            val field = SliceRefField(curStructure.id, path)
            val value = state.memory.readField(address, field, field.getSort())

            val length = state.getSliceRemainingBitsCount(value)

            when (read) {
                is TvmCellDataBitArrayRead -> UExprReadResult(value).uncheckedCast<Any, ReadResult>()
                is TvmCellDataMsgAddrRead -> UExprPairReadResult(length, value).uncheckedCast()
                else -> error("Unexpected read: $read")
            }
        }

        is TlbFullMsgAddrLabel, is TlbBasicMsgAddrLabel -> {

            if (read !is TvmCellDataMsgAddrRead && read !is TvmCellDataBitArrayRead) {
                return@with null
            }

            val struct = (this@extractTlbValueIfPossible as TlbCompositeLabel).internalStructure as? SwitchPrefix
                ?: error("structure of TlbFullMsgAddrLabel must be switch")

            if (struct.variants.size > 1) {
                TODO()
            }

            val variant = struct.variants.single()

            val rest = variant.struct as? KnownTypePrefix
                ?: error("Unexpected structure: ${variant.struct}")
            val restLabel = rest.typeLabel as? FixedSizeDataLabel
                ?: error("Unexpected label: ${rest.typeLabel}")
            val restField = ConcreteSizeBlockField(restLabel.concreteSize, rest.id, path.add(curStructure.id))
            val restValue = state.memory.readField(address, restField, restField.getSort())

            val prefix = mkBv(variant.key, variant.key.length.toUInt())

            val content = mkBvConcatExpr(prefix, restValue)

            val value = state.allocSliceFromData(content)
            val length = mkSizeExpr(TvmContext.stdMsgAddrSize)

            when (read) {
                is TvmCellDataBitArrayRead -> UExprReadResult(value).uncheckedCast<Any, ReadResult>()
                is TvmCellDataMsgAddrRead -> UExprPairReadResult(length, value).uncheckedCast()
                else -> error("Unexpected read: $read")
            }
        }

        is TlbMaybeRefLabel -> {
            if (read !is TvmCellMaybeConstructorBitRead) {
                return@with null
            }

            val newPath = path.add(curStructure.id)

            val switchStruct = internalStructure as? SwitchPrefix
                ?: error("structure of TlbMaybeRefLabel must be switch")

            val possibleVariants = state.dataCellInfoStorage.mapper.calculatedTlbLabelInfo.getPossibleSwitchVariants(switchStruct, leftTlbDepth)
            val trueVariant = possibleVariants.indexOfFirst { it.key == "1" }

            if (trueVariant == -1) {
                return@with UExprReadResult(falseExpr).uncheckedCast<Any, ReadResult>()
            }

            val expr = generateGuardForSwitch(switchStruct, trueVariant, possibleVariants, state, address, newPath)
            UExprReadResult(expr).uncheckedCast<Any, ReadResult>()
        }

        else -> {
            // TODO
            null
        }
    }
}

context(TvmContext)
private fun extractInt(
    offset: UExpr<TvmSizeSort>,
    length: UExpr<TvmSizeSort>,
    data: String,
    isSigned: Boolean,
): UExpr<TvmContext.TvmInt257Sort> {
    val bits = mkBv(data, data.length.toUInt()).zeroExtendToSort(cellDataSort)
    val shifted = mkBvLogicalShiftRightExpr(
        bits,
        mkSizeSubExpr(mkSizeExpr(data.length), mkSizeAddExpr(offset, length)).zeroExtendToSort(cellDataSort),
    )
    return extractIntFromShiftedData(shifted, length.zeroExtendToSort(int257sort), isSigned)
}

context(TvmContext)
fun <ReadResult : TvmCellDataTypeReadValue> TvmCellDataTypeRead<ReadResult>.readFromConstant(
    offset: UExpr<TvmSizeSort>,
    data: String,
): ReadResult? = when (this) {
    is TvmCellDataIntegerRead -> {
        val intExpr = extractInt(offset, sizeBits, data, isSigned)
        UExprReadResult(intExpr).uncheckedCast()
    }
    is TvmCellMaybeConstructorBitRead -> {
        val bit = extractInt(offset, oneSizeExpr, data, isSigned = false)
        val boolExpr = mkIte(
            bit eq zeroValue,
            trueBranch = { falseExpr },
            falseBranch = { trueExpr },
        )
        UExprReadResult(boolExpr).uncheckedCast()
    }
    is TvmCellDataMsgAddrRead -> {
        null
    }
    is TvmCellDataBitArrayRead -> {
        null
    }
    is TvmCellDataCoinsRead -> {
        val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
        val fourDataBits = if (concreteOffset != null) {
            data.substring(concreteOffset, min(concreteOffset + 4, data.length))
        } else {
            null
        }
        val result = if (fourDataBits == "0000") {
            UExprPairReadResult(mkBv(0, mkBvSort(4u)), zeroValue)
        } else {
            null
        }

        result?.uncheckedCast<Any, ReadResult>()
    }
}
