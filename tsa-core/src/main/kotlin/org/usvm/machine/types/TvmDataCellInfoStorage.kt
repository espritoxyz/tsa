package org.usvm.machine.types

import org.ton.TlbAtomicLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.dp.CalculatedTlbLabelInfo
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.utils.extractAddresses


class TvmDataCellInfoStorage private constructor(
    private val ctx: TvmContext,
    val mapper: TvmAddressToLabelMapper,
    val sliceMapper: TvmSliceToTlbStackMapper,
) {
    fun notifyAboutChildRequest(state: TvmState, ref: UHeapRef) = with(ctx) {
        val staticAddresses = extractAddresses(ref).map { it.second }

        staticAddresses.forEach {
            if (!state.isTerminated) {
                mapper.initializeConstraintsForChildren(state, it)
            }
        }
    }

    fun getLabelForFreshSlice(cellRef: UHeapRef): Map<TvmParameterInfo.CellInfo, UBoolExpr> = with(ctx) {
        val staticAddresses = extractAddresses(cellRef, extractAllocated = true)
        val result = hashMapOf<TvmParameterInfo.CellInfo, UBoolExpr>()

        staticAddresses.forEach { (guard, ref) ->
            val labelInfo = mapper.getLabelInfo(ref) ?: LabelInfo(mapOf(TvmParameterInfo.UnknownCellInfo to trueExpr))
            labelInfo.variants.forEach { (info, innerGuard) ->
                val oldGuard = result[info] ?: falseExpr
                result[info] = oldGuard or (guard and innerGuard)
            }
        }

        return result
    }

    fun getNoUnexpectedEndOfReadingCondition(
        state: TvmState,
        endOfCell: TvmDataCellLoadedTypeInfo.EndOfCell
    ): UBoolExpr = with(ctx) {
        val labelInfo = mapper.getLabelInfo(endOfCell.cellAddress)
            ?: return trueExpr
        return labelInfo.variants.entries.fold(trueExpr as UBoolExpr) { acc, (curInfo, guard) ->
            if (curInfo !is TvmParameterInfo.DataCellInfo) {
                // case of DictCell: do nothing
                return@fold acc
            }
            when (val label = curInfo.dataCellStructure) {
                is TlbAtomicLabel -> {
                    check(label.arity == 0)
                    val refNumberGuard = endOfCell.refNumber eq zeroSizeExpr
                    val dataLengthGuard = endOfCell.offset eq label.dataLength(state, emptyList())
                    acc and (guard implies (refNumberGuard and dataLengthGuard))
                }
                is TlbCompositeLabel -> {
                    val leafInfo = mapper.calculatedTlbLabelInfo.getLeavesInfo(state, endOfCell.cellAddress, label)
                        ?: return@fold acc

                    leafInfo.fold(acc) { innerAcc, (struct, sizeInfo) ->
                        when (struct) {
                            is TlbStructure.Unknown -> {
                                val newGuard = mkSizeGeExpr(endOfCell.offset, sizeInfo.dataLength) and
                                        mkSizeGeExpr(endOfCell.refNumber, sizeInfo.refsLength)
                                innerAcc and ((guard and sizeInfo.guard) implies  newGuard)
                            }

                            is TlbStructure.Empty -> {
                                val newGuard = (endOfCell.offset eq sizeInfo.dataLength) and
                                        (endOfCell.refNumber eq sizeInfo.refsLength)
                                innerAcc and ((guard and sizeInfo.guard) implies  newGuard)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getNoUnexpectedLoadRefCondition(
        state: TvmState,
        loadRef: TvmDataCellLoadedTypeInfo.LoadRef,
    ): UBoolExpr = with(ctx) {
        val labelInfo = mapper.getLabelInfo(loadRef.cellAddress)
            ?: return trueExpr
        return labelInfo.variants.entries.fold(trueExpr as UBoolExpr) { acc, (curInfo, guard) ->
            if (curInfo !is TvmParameterInfo.DataCellInfo) {
                // TODO: throw error for treating DictCell as DataCell
                return@fold acc
            }
            when (val label = curInfo.dataCellStructure) {
                is TlbAtomicLabel -> {
                    acc and (guard implies (loadRef.refNumber eq zeroSizeExpr))
                }
                is TlbCompositeLabel -> {
                    val leafInfo = mapper.calculatedTlbLabelInfo.getLeavesInfo(state, loadRef.cellAddress, label)
                        ?: return@fold acc

                    leafInfo.fold(acc) { innerAcc, (struct, sizeInfo) ->
                        when (struct) {
                            is TlbStructure.Unknown -> {
                                innerAcc
                            }
                            is TlbStructure.Empty -> {
                                val newGuard = mkSizeLeExpr(loadRef.refNumber, sizeInfo.refsLength)
                                innerAcc and ((guard and sizeInfo.guard) implies newGuard)
                            }
                        }
                    }
                }
            }
        }
    }

    fun clone(): TvmDataCellInfoStorage = TvmDataCellInfoStorage(ctx, mapper, sliceMapper.clone())

    companion object {
        fun build(
            state: TvmState,
            info: TvmInputInfo,
            additionalCellLabels: Map<UConcreteHeapRef, TvmParameterInfo.CellInfo> = emptyMap(),
            additionalSliceToCell: Map<UConcreteHeapRef, UConcreteHeapRef> = emptyMap(),
        ): TvmDataCellInfoStorage {
            val inputAddresses = extractInputParametersAddresses(state, info)
            val addressesWithCellInfo = InputParametersStructure(
                cellToInfo = additionalCellLabels + inputAddresses.cellToInfo,
                sliceToCell = additionalSliceToCell + inputAddresses.sliceToCell,
            )
            val labels = addressesWithCellInfo.cellToInfo.values.mapNotNull {
                (it as? TvmParameterInfo.DataCellInfo)?.dataCellStructure as? TlbCompositeLabel
            }
            val calculatedTlbLabelInfo = CalculatedTlbLabelInfo(state.ctx, labels)
            val mapper = TvmAddressToLabelMapper(state, addressesWithCellInfo, calculatedTlbLabelInfo)
            val sliceMapper = TvmSliceToTlbStackMapper.constructInitialSliceMapper(state.ctx, addressesWithCellInfo)

            return TvmDataCellInfoStorage(state.ctx, mapper, sliceMapper)
        }
    }
}
