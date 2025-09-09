package org.usvm.machine.types.dp

import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.state.preloadDataBitsFromCellWithoutStructuralAsserts
import org.usvm.machine.types.dp.AbstractGuard.Companion.abstractFalse
import org.usvm.machine.types.dp.AbstractGuard.Companion.abstractTrue
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.generateCellDataConstraint
import org.usvm.machine.types.memory.generateGuardForSwitch
import org.usvm.mkSizeExpr

fun calculateDataConstraints(
    ctx: TvmContext,
    labels: Collection<TlbCompositeLabel>,
    dataLengths: List<Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>>,
    individualMaxCellTlbDepth: Map<TlbCompositeLabel, Int>,
    possibleSwitchVariants: List<Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>>,
): List<Map<TlbCompositeLabel, AbstractGuard<AbstractionForUExprWithCellDataPrefix>>> =
    calculateMapsByTlbDepth(ctx.tvmOptions.tlbOptions.maxTlbDepth, labels) { label, curDepth, prevDepthValues ->
        val tlbDepthBound = individualMaxCellTlbDepth[label]
            ?: error("individualMaxCellTlbDepth must be calculated for all labels")

        if (tlbDepthBound >= curDepth) {
            val dataLengthsFromPreviousDepth = if (curDepth == 0) emptyMap() else dataLengths[curDepth - 1]
            getDataConstraints(ctx, label.internalStructure, prevDepthValues, dataLengthsFromPreviousDepth, possibleSwitchVariants[curDepth])
        } else {
            prevDepthValues[label] ?: error("The value should be counted by now")
        }
    }

private fun getDataConstraints(
    ctx: TvmContext,
    struct: TlbStructure,
    constraintsFromPreviousDepth: Map<TlbCompositeLabel, AbstractGuard<AbstractionForUExprWithCellDataPrefix>>,
    dataLengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>,
    possibleSwitchVariants: Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>,
): AbstractGuard<AbstractionForUExprWithCellDataPrefix> = with(ctx) {
    when (struct) {
        is TlbStructure.Empty -> {
            // no constraints here
            abstractTrue()
        }

        is TlbStructure.LoadRef -> {
            getDataConstraints(ctx, struct.rest, constraintsFromPreviousDepth, dataLengthsFromPreviousDepth, possibleSwitchVariants)
        }

        is TlbStructure.KnownTypePrefix -> {
            val offset = getKnownTypePrefixDataLength(struct, dataLengthsFromPreviousDepth)?.convert()
                ?: return abstractFalse()  // cannot construct with given depth

            val innerGuard = if (struct.typeLabel is TlbCompositeLabel) {
                constraintsFromPreviousDepth[struct.typeLabel]?.addTlbLevel(struct)
                    ?: return abstractFalse()  // cannot construct with given depth

            } else {
                AbstractGuard {
                    generateCellDataConstraint(struct, it)
                }
            }

            val further = getDataConstraints(ctx, struct.rest, constraintsFromPreviousDepth, dataLengthsFromPreviousDepth, possibleSwitchVariants)

            innerGuard and further.shift(offset)
        }

        is TlbStructure.SwitchPrefix -> {
            val switchSize = mkSizeExpr(struct.switchSize)
            val possibleVariants = possibleSwitchVariants[struct]
                ?: error("Switch variants not found for switch $struct")
            possibleVariants.foldIndexed(abstractFalse()) { idx, acc, (key, variant) ->
                val further = getDataConstraints(
                    ctx,
                    variant,
                    constraintsFromPreviousDepth,
                    dataLengthsFromPreviousDepth,
                    possibleSwitchVariants,
                ).shift(AbstractSizeExpr { switchSize })

                val switchGuard = AbstractGuard<AbstractionForUExprWithCellDataPrefix> { (address, prefixSize, path, state) ->
                    val variantConstraint = generateGuardForSwitch(struct, idx, possibleVariants, state, address, path)
                    val data = state.preloadDataBitsFromCellWithoutStructuralAsserts(address, prefixSize, struct.switchSize)
                    val expected = mkBv(key, struct.switchSize.toUInt())
                    val dataConstraint = data eq expected

                    variantConstraint and dataConstraint
                }

                acc or (switchGuard and further)
            }
        }

        is TlbStructure.Unknown -> {
            AbstractGuard { (address, prefixSize, path, state) ->
                val field = UnknownBlockField(struct.id, path)
                val fieldValue = state.memory.readField(address, field, field.getSort(ctx))
                val curData = state.fieldManagers.cellDataFieldManager.readCellDataWithoutAsserts(state, address)

                mkBvShiftLeftExpr(curData, prefixSize.zeroExtendToSort(cellDataSort)) eq fieldValue
            }
        }
    }
}
