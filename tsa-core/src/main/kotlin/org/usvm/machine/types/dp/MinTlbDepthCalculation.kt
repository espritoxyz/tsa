package org.usvm.machine.types.dp

import org.ton.TlbCompositeLabel
import org.ton.TlbStructure

fun calculateMinTlbDepth(
    maxTlbDepth: Int,
    compositeLabels: Collection<TlbCompositeLabel>,
): Map<TlbCompositeLabel, Int> {
    val result = hashMapOf<TlbCompositeLabel, Int>()
    compositeLabels.forEach { label ->
        if (zeroDepthIsPossible(label)) {
            result[label] = 0
        }
    }
    for (curDepth in 1..maxTlbDepth) {
        val newLabels = mutableListOf<TlbCompositeLabel>()
        compositeLabels.forEach { label ->
            if (result[label] != null) {
                return@forEach
            }
            val currentDepthIsPossible = constructionIsPossible(label.internalStructure) { result[it] != null }
            if (currentDepthIsPossible) {
                newLabels.add(label)
            }
        }
        newLabels.forEach { result[it] = curDepth }
    }
    return result
}

private fun zeroDepthIsPossible(label: TlbCompositeLabel) = zeroDepthIsPossible(label.internalStructure)

private fun zeroDepthIsPossible(struct: TlbStructure): Boolean = constructionIsPossible(struct) { false }

fun constructionIsPossible(
    struct: TlbStructure,
    possibleCompositeLabel: (TlbCompositeLabel) -> Boolean,
): Boolean =
    when (struct) {
        is TlbStructure.Unknown, is TlbStructure.Empty -> {
            true
        }
        is TlbStructure.LoadRef -> {
            constructionIsPossible(struct.rest, possibleCompositeLabel)
        }
        is TlbStructure.KnownTypePrefix -> {
            if (struct.typeLabel is TlbCompositeLabel) {
                possibleCompositeLabel(struct.typeLabel)
            } else {
                constructionIsPossible(struct.rest, possibleCompositeLabel)
            }
        }
        is TlbStructure.SwitchPrefix -> {
            struct.variants.any { constructionIsPossible(it.struct, possibleCompositeLabel) }
        }
    }
