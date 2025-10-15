package org.usvm.machine.types.dp

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.isFalse
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.preloadDataBitsFromCellWithoutStructuralAsserts
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.generateCellDataConstraint
import org.usvm.machine.types.memory.generateGuardForSwitch
import org.usvm.mkSizeExpr
import kotlin.math.min

fun calculateDataConstraint(
    label: TlbCompositeLabel,
    state: TvmState,
    cellRef: UConcreteHeapRef,
    curMaxTlbDepth: Int,
    dataLengths: List<Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>>,
    individualMaxCellTlbDepth: Map<TlbCompositeLabel, Int>,
    possibleSwitchVariants: List<Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>>,
): UBoolExpr {
    val tlbDepthBound =
        individualMaxCellTlbDepth[label]
            ?: error("individualMaxCellTlbDepth must be calculated for all labels")

    return getDataConstraint(
        label.internalStructure,
        state,
        cellRef,
        prefixSize = state.ctx.zeroSizeExpr,
        path = persistentListOf(),
        curMaxTlbDepth = min(tlbDepthBound, curMaxTlbDepth),
        dataLengths,
        possibleSwitchVariants,
    )
}

private fun getDataConstraint(
    struct: TlbStructure,
    state: TvmState,
    cellRef: UConcreteHeapRef,
    prefixSize: UExpr<TvmSizeSort>,
    path: PersistentList<Int>,
    curMaxTlbDepth: Int,
    dataLengths: List<Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>>,
    possibleSwitchVariants: List<Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>>,
): UBoolExpr =
    with(state.ctx) {
        val paramForAbstractGuards = SimpleAbstractionForUExpr(cellRef, path, state)

        when (struct) {
            is TlbStructure.Empty -> {
                // no constraints here
                trueExpr
            }

            is TlbStructure.LoadRef -> {
                getDataConstraint(
                    struct.rest,
                    state,
                    cellRef,
                    prefixSize,
                    path,
                    curMaxTlbDepth,
                    dataLengths,
                    possibleSwitchVariants,
                )
            }

            is TlbStructure.KnownTypePrefix -> {
                val dataLengthsFromPreviousDepth =
                    if (curMaxTlbDepth == 0) {
                        return falseExpr
                    } else {
                        dataLengths[curMaxTlbDepth - 1]
                    }

                val offset =
                    getKnownTypePrefixDataLength(struct, dataLengthsFromPreviousDepth)
                        ?.apply
                        ?.let { it(paramForAbstractGuards) }
                        ?: return falseExpr // cannot construct with given depth

                val innerGuard =
                    if (struct.typeLabel is TlbCompositeLabel) {
                        getDataConstraint(
                            struct.typeLabel.internalStructure,
                            state,
                            cellRef,
                            prefixSize,
                            path.add(struct.id),
                            curMaxTlbDepth - 1,
                            dataLengths,
                            possibleSwitchVariants,
                        )
                    } else {
                        generateCellDataConstraint(struct, cellRef, prefixSize, path, state)
                    }

                if (innerGuard.isFalse) {
                    return falseExpr
                }

                val further =
                    getDataConstraint(
                        struct.rest,
                        state,
                        cellRef,
                        mkBvAddExpr(prefixSize, offset),
                        path,
                        curMaxTlbDepth,
                        dataLengths,
                        possibleSwitchVariants,
                    )

                innerGuard and further
            }

            is TlbStructure.SwitchPrefix -> {
                val switchSize = mkSizeExpr(struct.switchSize)
                val possibleVariants =
                    possibleSwitchVariants[curMaxTlbDepth][struct]
                        ?: error("Switch variants not found for switch $struct")

                possibleVariants.foldIndexed(falseExpr as UBoolExpr) { idx, acc, (key, variant) ->
                    val further =
                        getDataConstraint(
                            variant,
                            state,
                            cellRef,
                            mkBvAddExpr(prefixSize, switchSize),
                            path,
                            curMaxTlbDepth,
                            dataLengths,
                            possibleSwitchVariants,
                        )

                    val variantConstraint =
                        generateGuardForSwitch(struct, idx, possibleVariants, state, cellRef, path)

                    val data =
                        state.preloadDataBitsFromCellWithoutStructuralAsserts(
                            cellRef,
                            prefixSize,
                            struct.switchSize,
                        )
                    val expected = mkBv(key, struct.switchSize.toUInt())
                    val dataConstraint = data eq expected

                    val switchGuard = variantConstraint and dataConstraint

                    acc or (switchGuard and further)
                }
            }

            is TlbStructure.Unknown -> {
                val field = UnknownBlockField(struct.id, path)
                val fieldValue = state.memory.readField(cellRef, field, field.getSort(state.ctx))
                val curData = state.fieldManagers.cellDataFieldManager.readCellDataWithoutAsserts(state, cellRef)

                mkBvShiftLeftExpr(curData, prefixSize.zeroExtendToSort(cellDataSort)) eq fieldValue
            }
        }
    }
