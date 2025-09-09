package org.usvm.machine.types.dp

import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TlbStructure.Empty
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.LoadRef
import org.ton.TlbStructure.SwitchPrefix
import org.ton.TlbStructure.SwitchPrefix.SwitchVariant
import org.ton.TlbStructure.Unknown

fun calculatePossibleSwitchVariants(
    maxTlbDepth: Int,
    switches: Collection<SwitchPrefix>,
    minTlbDepths: Map<TlbCompositeLabel, Int>,
): List<Map<SwitchPrefix, List<SwitchVariant>>> =
    calculateMapsByTlbDepth(maxTlbDepth, switches) { switch, curDepth, _ ->
        getPossibleSwitchVariantsForGivenDepth(curDepth, switch, minTlbDepths)
    }

private fun getPossibleSwitchVariantsForGivenDepth(
    tlbDepth: Int,
    switch: SwitchPrefix,
    minTlbDepths: Map<TlbCompositeLabel, Int>,
): List<SwitchVariant> =
    switch.variants.filter { (_, variant) ->
        constructionIsPossible(variant) {
            val minDepth =
                minTlbDepths[it]
                    ?: error("minTlbDepth must be known for $it, but it is not")
            minDepth <= tlbDepth - 1
        }
    }

fun extractAllSwitches(labels: Collection<TlbCompositeLabel>): Set<SwitchPrefix> =
    labels.fold(mutableSetOf()) { acc, label ->
        acc.addAll(extractAllSwitches(label.internalStructure))
        acc
    }

private fun extractAllSwitches(structure: TlbStructure): Set<SwitchPrefix> =
    when (structure) {
        is Empty, Unknown -> {
            setOf()
        }
        is LoadRef -> {
            extractAllSwitches(structure.rest)
        }
        is KnownTypePrefix -> {
            extractAllSwitches(structure.rest)
        }
        is SwitchPrefix -> {
            structure.variants.fold(setOf(structure)) { acc, (_, variant) ->
                acc + extractAllSwitches(variant)
            }
        }
    }
