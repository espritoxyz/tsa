package org.usvm.machine.types.dp

import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.machine.TvmContext
import org.usvm.machine.types.memory.generateGuardForSwitch
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr

fun calculateDataLengths(
    ctx: TvmContext,
    labelsWithoutUnknowns: Collection<TlbCompositeLabel>,
    individualMaxCellTlbDepth: Map<TlbCompositeLabel, Int>,
    possibleSwitchVariants: List<Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>>,
): List<Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>> =
    calculateMapsByTlbDepth(
        ctx.tvmOptions.tlbOptions.maxTlbDepth,
        labelsWithoutUnknowns,
    ) { label, curDepth, prevDepthValues ->
        val tlbDepthBound =
            individualMaxCellTlbDepth[label]
                ?: error("individualMaxCellTlbDepth must be calculated for all labels")

        if (tlbDepthBound >= curDepth) {
            getDataLength(ctx, label.internalStructure, prevDepthValues, possibleSwitchVariants[curDepth])
        } else {
            prevDepthValues[label]
        }
    }

/**
 * If returns null, construction of the given depth is impossible.
 * */
private fun getDataLength(
    ctx: TvmContext,
    struct: TlbStructure,
    lengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>,
    possibleSwitchVariants: Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>,
): AbstractSizeExpr<SimpleAbstractionForUExpr>? =
    with(ctx) {
        when (struct) {
            is TlbStructure.Unknown -> {
                error("Cannot calculate length for Unknown leaf")
            }

            is TlbStructure.Empty -> {
                AbstractSizeExpr { zeroSizeExpr }
            }

            is TlbStructure.LoadRef -> {
                getDataLength(ctx, struct.rest, lengthsFromPreviousDepth, possibleSwitchVariants)
            }

            is TlbStructure.KnownTypePrefix -> {
                val furtherWithoutShift =
                    getDataLength(ctx, struct.rest, lengthsFromPreviousDepth, possibleSwitchVariants)
                        ?: return null // cannot construct with given depth

                val offset =
                    getKnownTypePrefixDataLength(struct, lengthsFromPreviousDepth)
                        ?: return null // cannot construct with given depth

                furtherWithoutShift.add(offset)
            }

            is TlbStructure.SwitchPrefix -> {
                val switchSize = struct.switchSize
                val possibleVariants =
                    possibleSwitchVariants[struct]
                        ?: error("Switch variants not found for switch $struct")

                val childLengths =
                    possibleVariants.mapIndexedNotNull { idx, (_, variant) ->

                        val further =
                            getDataLength(ctx, variant, lengthsFromPreviousDepth, possibleSwitchVariants)
                                ?: return@mapIndexedNotNull null // cannot construct this variant with given depth

                        val condition =
                            AbstractGuard<SimpleAbstractionForUExpr> { (address, path, state) ->
                                generateGuardForSwitch(struct, idx, possibleVariants, state, address, path)
                            }

                        condition to further
                    }

                if (childLengths.isEmpty()) {
                    return null // cannot construct with given depth
                }

                var childIte = childLengths.last().second // arbitrary value
                childLengths.subList(0, childLengths.size - 1).forEach { (condition, value) ->
                    val prev = childIte
                    childIte =
                        AbstractSizeExpr { param ->
                            mkIte(condition.apply(param), value.apply(param), prev.apply(param))
                        }
                }

                AbstractSizeExpr {
                    mkSizeAddExpr(childIte.apply(it), mkSizeExpr(switchSize))
                }
            }
        }
    }
