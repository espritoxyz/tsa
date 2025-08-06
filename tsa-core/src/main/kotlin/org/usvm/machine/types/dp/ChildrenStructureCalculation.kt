package org.usvm.machine.types.dp

import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.machine.TvmContext
import org.usvm.machine.types.memory.generateGuardForSwitch

fun calculateChildrenStructures(
    ctx: TvmContext,
    labelsWithoutUnknowns: Collection<TlbCompositeLabel>,
    dataLengths: List<Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>>,
    individualMaxCellTlbDepth: Map<TlbCompositeLabel, Int>,
    possibleSwitchVariants: List<Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>>,
): List<Map<TlbCompositeLabel, ChildrenStructure<SimpleAbstractionForUExpr>>> =
    calculateMapsByTlbDepth(ctx.tvmOptions.tlbOptions.maxTlbDepth, labelsWithoutUnknowns) { label, curDepth, prevDepthValues ->
        val tlbDepthBound = individualMaxCellTlbDepth[label]
            ?: error("individualMaxCellTlbDepth must be calculated for all labels")

        if (tlbDepthBound >= curDepth) {
            val dataLengthsFromPreviousDepth = if (curDepth == 0) emptyMap() else dataLengths[curDepth - 1]
            getChildrenStructure(ctx, label.internalStructure, prevDepthValues, dataLengthsFromPreviousDepth, possibleSwitchVariants[curDepth])
        } else {
            prevDepthValues[label]
        }
    }

private fun getChildrenStructure(
    ctx: TvmContext,
    struct: TlbStructure,
    structuresFromPreviousDepth: Map<TlbCompositeLabel, ChildrenStructure<SimpleAbstractionForUExpr>>,
    dataLengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr<SimpleAbstractionForUExpr>>,
    possibleSwitchVariants: Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>,
): ChildrenStructure<SimpleAbstractionForUExpr>? = with(ctx) {
    when (struct) {
        is TlbStructure.Unknown -> {
            error("Cannot calculate ChildrenStructure for Unknown leaf")
        }

        is TlbStructure.Empty -> {
            ChildrenStructure.empty()
        }

        is TlbStructure.LoadRef -> {
            val furtherChildren = getChildrenStructure(
                ctx,
                struct.rest,
                structuresFromPreviousDepth,
                dataLengthsFromPreviousDepth,
                possibleSwitchVariants
            ) ?: return null  // cannot construct with given depth

            val exceededGuard = furtherChildren.children.last().exists()

            val newChildren = listOf(
                ChildStructure<SimpleAbstractionForUExpr>(mapOf(struct.ref to AbstractGuard.abstractTrue()))
            ) + furtherChildren.children.subList(0, furtherChildren.children.size - 1)

            ChildrenStructure(newChildren, exceededGuard)
        }

        is TlbStructure.KnownTypePrefix -> {
            val furtherChildren = getChildrenStructure(
                ctx,
                struct.rest,
                structuresFromPreviousDepth,
                dataLengthsFromPreviousDepth,
                possibleSwitchVariants,
            ) ?: return null  // cannot construct with given depth

            if (struct.typeLabel !is TlbCompositeLabel) {
                return furtherChildren
            }

            val innerChildren = structuresFromPreviousDepth[struct.typeLabel]?.addTlbLevel(struct)
                ?: return null  // cannot construct with given depth

            var newExceeded = innerChildren.numberOfChildrenExceeded or furtherChildren.numberOfChildrenExceeded

            for (i in 0 until TvmContext.MAX_REFS_NUMBER) {
                newExceeded = newExceeded or
                        (innerChildren.children[i].exists() and furtherChildren.children[3 - i].exists())
            }

            val newChildren = List(4) { childIdx ->
                var result = innerChildren.children[childIdx]
                for (childrenInInner in 0..childIdx) {
                    val guard = innerChildren.exactNumberOfChildren(ctx, childrenInInner)
                    result = result union (furtherChildren.children[childIdx - childrenInInner] and guard)
                }
                result
            }

            ChildrenStructure(newChildren, newExceeded)
        }

        is TlbStructure.SwitchPrefix -> {
            var atLeastOneBranch = false
            val possibleVariants = possibleSwitchVariants[struct]
                ?: error("Switch variants not found for switch $struct")
            val result = possibleVariants.foldIndexed(ChildrenStructure.empty<SimpleAbstractionForUExpr>()) { idx, acc, (key, rest) ->
                val further = getChildrenStructure(
                    ctx,
                    rest,
                    structuresFromPreviousDepth,
                    dataLengthsFromPreviousDepth,
                    possibleSwitchVariants,
                ) ?: error("Switch variant with key $key must be reachable")

                atLeastOneBranch = true
                val variantGuard = AbstractGuard<SimpleAbstractionForUExpr> { (address, path, state) ->
                    generateGuardForSwitch(struct, idx, possibleVariants, state, address, path)
                }

                acc union (further and variantGuard)
            }

            if (!atLeastOneBranch) {
                null
            } else {
                result
            }
        }
    }
}
