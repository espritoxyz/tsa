package org.usvm.machine.types

import org.ton.FixedSizeDataLabel
import org.ton.TlbAddressByRef
import org.ton.TlbAtomicLabel
import org.ton.TlbBitArrayByRef
import org.ton.TlbBitArrayOfConcreteSize
import org.ton.TlbBuiltinLabel
import org.ton.TlbCoinsLabel
import org.ton.TlbIntegerLabel
import org.ton.TlbMaybeRefLabel
import org.ton.TlbMsgAddrLabel
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo
import org.ton.compositeLabelOfUnknown
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.isStatic
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.sliceCellField
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.generateSymbolicCell
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGtExpr
import org.usvm.mkSizeSubExpr
import org.usvm.types.USingleTypeStream

fun <AccType> foldOnTvmType(
    type: TvmType,
    init: AccType,
    f: (AccType, TvmType) -> AccType,
): AccType {
    var acc = init
    var curType: TvmType? = type
    while (curType != null) {
        acc = f(acc, curType)
        curType = curType.parentType
    }
    return acc
}

fun TvmState.getPossibleTypes(ref: UConcreteHeapRef): Sequence<TvmType> {
    val stream = memory.types.getTypeStream(ref)
    check(stream is USingleTypeStream)
    val type = stream.commonSuperType
    return typeSystem.findSubtypes(type)
}

fun <ReadResult> TlbBuiltinLabel.accepts(
    ctx: TvmContext,
    labelArgs: List<UExpr<TvmSizeSort>>,
    symbolicTypeRead: TvmCellDataTypeRead<ReadResult>,
): UBoolExpr =
    with(ctx) {
        when (this@accepts) {
            is TlbIntegerLabel -> {
                if (symbolicTypeRead is SizedCellDataTypeRead &&
                    (
                        symbolicTypeRead is TvmCellDataIntegerRead &&
                            isSigned == symbolicTypeRead.isSigned &&
                            endian == symbolicTypeRead.endian ||
                            symbolicTypeRead is TvmCellDataBitArrayRead
                    )
                ) {
                    symbolicTypeRead.sizeBits eq bitSize(this, labelArgs)
                } else {
                    falseExpr
                }
            }

            is TlbAddressByRef -> {
                if (symbolicTypeRead is TvmCellDataBitArrayRead) {
                    symbolicTypeRead.sizeBits eq sizeBits
                } else {
                    (symbolicTypeRead is TvmCellDataMsgAddrRead).expr
                }
            }

            is TlbMsgAddrLabel -> {
                (symbolicTypeRead is TvmCellDataMsgAddrRead).expr
            }

            is TlbCoinsLabel -> {
                (symbolicTypeRead is TvmCellDataCoinsRead).expr
            }

            is TlbMaybeRefLabel -> {
                // case of TvmCellDataIntegerRead should be processed by internal structure of TlbMaybeRefLabel
                when (symbolicTypeRead) {
                    is TvmCellMaybeConstructorBitRead -> trueExpr
                    else -> falseExpr
                }
            }

            is TlbBitArrayOfConcreteSize -> {
                if (symbolicTypeRead is TvmCellDataBitArrayRead) {
                    symbolicTypeRead.sizeBits eq mkSizeExpr(concreteSize)
                } else {
                    falseExpr
                }
            }

            is TlbBitArrayByRef -> {
                if (symbolicTypeRead is TvmCellDataBitArrayRead) {
                    symbolicTypeRead.sizeBits eq sizeBits
                } else {
                    falseExpr
                }
            }
        }
    }

fun TlbBuiltinLabel.passBitArrayRead(
    ctx: TvmContext,
    labelArgs: List<UExpr<TvmSizeSort>>,
    bitArrayLength: UExpr<TvmSizeSort>,
): ContinueLoadOnNextFrameData? =
    with(ctx) {
        when (this@passBitArrayRead) {
            is TlbIntegerLabel -> {
                val intLength = bitSize(this, labelArgs)
                val leftBits = mkSizeSubExpr(bitArrayLength, intLength)
                ContinueLoadOnNextFrameData(
                    mkSizeGtExpr(bitArrayLength, intLength),
                    leftBits,
                )
            }

            is TlbBitArrayOfConcreteSize -> {
                val length = mkSizeExpr(concreteSize)
                ContinueLoadOnNextFrameData(mkSizeGtExpr(bitArrayLength, length), mkSizeSubExpr(bitArrayLength, length))
            }

            is TlbBitArrayByRef -> {
                ContinueLoadOnNextFrameData(
                    mkSizeGtExpr(bitArrayLength, sizeBits),
                    mkSizeSubExpr(bitArrayLength, sizeBits),
                )
            }

            is TlbMsgAddrLabel, is TlbCoinsLabel, is TlbMaybeRefLabel -> {
                null
            }
        }
    }

data class ContinueLoadOnNextFrameData(
    val guard: UBoolExpr,
    val leftBits: UExpr<TvmSizeSort>,
)

fun TlbBuiltinLabel.isEmptyLabel(
    ctx: TvmContext,
    labelArgs: List<UExpr<TvmSizeSort>>,
): UBoolExpr =
    with(ctx) {
        when (this@isEmptyLabel) {
            is TlbIntegerLabel -> {
                bitSize(this, labelArgs) eq zeroSizeExpr
            }

            is TlbMsgAddrLabel -> {
                falseExpr
            }

            is TlbCoinsLabel -> {
                falseExpr
            }

            is TlbMaybeRefLabel -> {
                falseExpr
            }

            is TlbBitArrayOfConcreteSize -> {
                mkBool(concreteSize == 0)
            }

            is TlbBitArrayByRef -> {
                sizeBits eq zeroSizeExpr
            }
        }
    }

fun TlbAtomicLabel.dataLength(
    state: TvmState,
    args: List<UExpr<TvmSizeSort>>,
): UExpr<TvmSizeSort> =
    with(state.ctx) {
        when (this@dataLength) {
            is TlbIntegerLabel -> bitSize(this, args)
            is FixedSizeDataLabel -> mkSizeExpr(concreteSize)
            is TlbBitArrayByRef -> sizeBits
            is TlbAddressByRef -> sizeBits
        }
    }

fun TlbAtomicLabel.defaultCellValue(ctx: TvmContext): String =
    when (this) {
        is TlbIntegerLabel -> {
            val defaultLength = bitSize(ctx, List(arity) { ctx.zeroSizeExpr }).intValue()
            "0".repeat(defaultLength)
        }

        is FixedSizeDataLabel -> {
            "0".repeat(concreteSize)
        }

        is TlbBitArrayByRef, is TlbMsgAddrLabel -> {
            error("Cannot calculate default cell value for $this")
        }
    }

fun TlbAtomicLabel.lengthUpperBound(): Int =
    when (this) {
        is TlbIntegerLabel -> lengthUpperBound
        is FixedSizeDataLabel -> concreteSize
        is TlbBitArrayByRef, is TlbMsgAddrLabel -> error("Cannot calculate lengthUpperBound for $this")
    }

data class InputParametersStructure(
    val cellToInfo: Map<UConcreteHeapRef, TvmParameterInfo.CellInfo>,
    val sliceToCell: Map<UConcreteHeapRef, UConcreteHeapRef>,
)

fun extractInputParametersAddresses(
    initialState: TvmState,
    inputInfo: TvmInputInfo,
): InputParametersStructure {
    val cells = hashMapOf<UConcreteHeapRef, TvmParameterInfo.CellInfo>()
    val slices = hashMapOf<UConcreteHeapRef, UConcreteHeapRef>()
    inputInfo.parameterInfos.entries.forEach { (param, paramInfo) ->
        val entry = initialState.stack.peekStackEntry(param)
        check(entry is TvmStack.TvmInputStackEntry) {
            "During TvmAddressToLabelMapper building stack must consist only of input entries, but $entry found"
        }

        when (paramInfo) {
            is TvmParameterInfo.CellInfo -> {
                val stackValue =
                    initialState.stack.getStackValue(entry, TvmCellType) {
                        initialState.generateSymbolicCell()
                    }
                // At this point stack should be empty (since TvmState is the initial state)
                // => stackValue is from input stack entry
                // => stackValue.cellValue must be UConcreteHeapRef
                val address = stackValue.cellValue as UConcreteHeapRef
                cells[address] = paramInfo
            }

            is TvmParameterInfo.SliceInfo -> {
                val stackValue =
                    initialState.stack.getStackValue(entry, TvmSliceType) {
                        initialState.generateSymbolicSlice()
                    }
                val sliceAddress =
                    stackValue.sliceValue
                        ?: error("Could not extract slice address while building TvmDataCellInfoStorage")
                // At this point stack should be empty (since TvmState is the initial state)
                // => stackValue is from input stack entry
                // => sliceAddress must be UConcreteHeapRef
                // => the corresponding cell address must also be concrete
                val address =
                    initialState.memory.readField(
                        sliceAddress,
                        sliceCellField,
                        initialState.ctx.addressSort,
                    ) as UConcreteHeapRef
                cells[address] = paramInfo.cellInfo
                // sliceAddress is concrete for the same reason as cell's address
                slices[sliceAddress as UConcreteHeapRef] = address
            }

            is TvmParameterInfo.NoInfo -> {
                // do nothing
            }
        }
    }

    if (inputInfo.c4Info != null) {
        check(inputInfo.c4Info.size == initialState.contractIdToC4Register.size) {
            "Unexpected size of c4Info"
        }

        inputInfo.c4Info.forEachIndexed { index, cellInfo ->
            val c4 =
                initialState.contractIdToC4Register[index]?.value?.value as? UConcreteHeapRef
                    ?: error("Unexpected value in c4: ${initialState.contractIdToC4Register[index]?.value?.value}")

            if (c4.isStatic) {
                cells[c4] = TvmParameterInfo.DataCellInfo(cellInfo)
            }
        }
    } else {
        initialState.contractIdToC4Register.values.forEach { c4 ->
            val c4Cell =
                c4.value.value as? UConcreteHeapRef
                    ?: error("Unexpected value in c4: ${c4.value.value}")

            if (c4Cell.isStatic) {
                cells[c4Cell] = TvmParameterInfo.DataCellInfo(compositeLabelOfUnknown)
            }
        }
    }

    return InputParametersStructure(cells, slices)
}
