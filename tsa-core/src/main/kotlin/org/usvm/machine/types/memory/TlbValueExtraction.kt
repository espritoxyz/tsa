package org.usvm.machine.types.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBvSort
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
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbMaybeRefLabel
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.SwitchPrefix
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIntepretedValue
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
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
): ReadResult? =
    with(state.ctx) {
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
                val gramsValue =
                    state.memory
                        .readField(
                            address,
                            gramsField,
                            gramsField.getSort(this),
                        ).unsignedExtendToInteger()

                val lengthField = ConcreteSizeBlockField(lengthStruct.typeLabel.concreteSize, lengthStruct.id, newPath)
                val lengthValue = state.memory.readField(address, lengthField, lengthField.getSort(this))

                UExprPairReadResult(lengthValue, gramsValue).uncheckedCast<Any, ReadResult>()
            }

            is TlbIntegerLabelOfConcreteSize -> {
                if (read !is TvmCellDataIntegerRead) {
                    return null
                }

                // no checks for sizeBits are made: they should be done before this call

                val field = ConcreteSizeBlockField(concreteSize, curStructure.id, path)
                val value = state.memory.readField(address, field, field.getSort(this))

                val result =
                    if (read.isSigned) {
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
                val fieldValue = state.memory.readField(address, field, field.getSort(this))

                UExprReadResult(state.allocSliceFromData(fieldValue)).uncheckedCast<Any, ReadResult>()
            }

            is TlbBitArrayByRef -> {
                if (read !is TvmCellDataBitArrayRead) {
                    return@with null
                }

                val field = SliceRefField(curStructure.id, path)
                val value = state.memory.readField(address, field, field.getSort(this))

                UExprReadResult(value).uncheckedCast<Any, ReadResult>()
            }

            is TlbAddressByRef -> {
                if (read !is TvmCellDataMsgAddrRead && read !is TvmCellDataBitArrayRead) {
                    return@with null
                }

                val field = SliceRefField(curStructure.id, path)
                val value = state.memory.readField(address, field, field.getSort(this))

                val length = state.getSliceRemainingBitsCount(value)

                when (read) {
                    is TvmCellDataBitArrayRead -> UExprReadResult(value).uncheckedCast<Any, ReadResult>()
                    is TvmCellDataMsgAddrRead -> UExprPairReadResult(length, value).uncheckedCast()
                    else -> error("Unexpected read: $read")
                }
            }

            is TlbBasicMsgAddrLabel -> {
                if (read !is TvmCellDataMsgAddrRead && read !is TvmCellDataBitArrayRead) {
                    return@with null
                }

                val struct =
                    (this@extractTlbValueIfPossible as TlbCompositeLabel).internalStructure as? SwitchPrefix
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

                val switchStruct =
                    internalStructure as? SwitchPrefix
                        ?: error("structure of TlbMaybeRefLabel must be switch")

                val possibleVariants =
                    state.dataCellInfoStorage.mapper.calculatedTlbLabelInfo.getPossibleSwitchVariants(
                        switchStruct,
                        leftTlbDepth,
                    )
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

fun <ReadResult : TvmCellDataTypeReadValue> TlbBuiltinLabel.extractKBvOfConcreteSizeFromTlbIfPossible(
    curStructure: KnownTypePrefix,
    read: TvmCellDataTypeRead<ReadResult>,
    address: UHeapRef,
    path: PersistentList<Int>,
    state: TvmState,
): UExpr<KBvSort>? =
    with(state.ctx) {
        check(curStructure.typeLabel == this@extractKBvOfConcreteSizeFromTlbIfPossible)
        check(read is TvmCellDataBitArrayRead)
        when (this@extractKBvOfConcreteSizeFromTlbIfPossible) {
            is TlbCoinsLabel -> {
                return null
            }

            is TlbIntegerLabelOfConcreteSize -> {
                val field = ConcreteSizeBlockField(concreteSize, curStructure.id, path)
                val value = state.memory.readField(address, field, field.getSort(this))
                value
            }

            is TlbBitArrayOfConcreteSize -> {
                val field = ConcreteSizeBlockField(concreteSize, curStructure.id, path)
                val fieldValue = state.memory.readField(address, field, field.getSort(this))
                fieldValue
            }

            else -> {
                null
            }
        }
    }

private fun extractInt(
    offset: UExpr<TvmSizeSort>,
    length: UExpr<TvmSizeSort>,
    data: String,
    isSigned: Boolean,
): UExpr<TvmContext.TvmInt257Sort> =
    with(offset.ctx.tctx()) {
        val bits = mkBv(data, data.length.toUInt()).zeroExtendToSort(cellDataSort)
        val shifted =
            mkBvLogicalShiftRightExpr(
                bits,
                mkSizeSubExpr(mkSizeExpr(data.length), mkSizeAddExpr(offset, length)).zeroExtendToSort(cellDataSort),
            )
        return extractIntFromShiftedData(shifted, length.zeroExtendToSort(int257sort), isSigned)
    }

fun <ReadResult : TvmCellDataTypeReadValue> TvmCellDataTypeRead<ReadResult>.readFromConstant(
    state: TvmState,
    offset: UExpr<TvmSizeSort>,
    data: String,
): ReadResult? =
    with(offset.ctx.tctx()) {
        when (this@readFromConstant) {
            is TvmCellDataIntegerRead -> {
                val intExpr = extractInt(offset, sizeBits, data, isSigned)
                UExprReadResult(intExpr).uncheckedCast()
            }

            is TvmCellMaybeConstructorBitRead -> {
                val bit = extractInt(offset, oneSizeExpr, data, isSigned = false)
                val boolExpr =
                    mkIte(
                        bit eq zeroValue,
                        trueBranch = { falseExpr },
                        falseBranch = { trueExpr },
                    )
                UExprReadResult(boolExpr).uncheckedCast()
            }

            is TvmCellDataMsgAddrRead -> {
                val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
                val twoDataBits =
                    if (concreteOffset != null) {
                        data.substring(concreteOffset, min(concreteOffset + 2, data.length))
                    } else {
                        null
                    }
                val result =
                    if (twoDataBits == "00") {
                        UExprPairReadResult(twoSizeExpr, state.allocSliceFromData(mkBv(0, sizeBits = 2u)))
                    } else {
                        null
                    }

                result?.uncheckedCast<Any, ReadResult>()
            }

            is TvmCellDataBitArrayRead -> {
                if (offset is KInterpretedValue) {
                    val bits =
                        readConcreteBv(state.ctx, offset.intValue(), data, this@readFromConstant.sizeBits)
                            ?: return@with null
                    val slice = state.allocSliceFromData(bits)
                    UExprReadResult(slice).uncheckedCast()
                } else {
                    null
                }
            }

            is TvmCellDataCoinsRead -> {
                val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
                val fourDataBits =
                    if (concreteOffset != null) {
                        data.substring(concreteOffset, min(concreteOffset + 4, data.length))
                    } else {
                        null
                    }
                val result =
                    if (fourDataBits == "0000") {
                        UExprPairReadResult(mkBv(0, mkBvSort(4u)), zeroValue)
                    } else {
                        null
                    }

                result?.uncheckedCast<Any, ReadResult>()
            }
        }
    }

fun readConcreteBv(
    ctx: TvmContext,
    offset: Int?,
    data: String,
    readSize: UExpr<TvmSizeSort>,
): UExpr<KBvSort>? =
    with(ctx) {
        val requiredReadSize = (readSize as? UIntepretedValue)?.intValue() ?: return null
        val boundedReadSize = min(data.length, requiredReadSize)
        if (boundedReadSize == 0) return null
        val bits = mkBv(data.drop(offset ?: return null).take(boundedReadSize), boundedReadSize.toUInt())
        bits
    }
