package org.usvm.machine.types.dp

import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.machine.TvmContext
import kotlin.math.min

fun calculateMaximumRefs(
    maxTlbDepth: Int,
    compositeLabels: Collection<TlbCompositeLabel>,
    individualMaxCellTlbDepth: Map<TlbCompositeLabel, Int>
): List<Map<TlbCompositeLabel, Int>> =
    calculateMapsByTlbDepth(maxTlbDepth, compositeLabels) { label, curDepth, prevDepthValues ->
        val tlbDepthBound =
            individualMaxCellTlbDepth[label]
                ?: error("individualMaxCellTlbDepth must be calculated for all labels")

        if (tlbDepthBound >= curDepth) {
            getMaximumRefs(label.internalStructure, prevDepthValues)
        } else {
            prevDepthValues[label]
        }
    }

/**
 * If returns null, construction of the given depth is impossible.
 * */
private fun getMaximumRefs(
    struct: TlbStructure,
    maxRefsFromPreviousDepth: Map<TlbCompositeLabel, Int>
): Int? {
    val cur: Int =
        when (struct) {
            is TlbStructure.Unknown -> {
                TvmContext.MAX_REFS_NUMBER
            }
            is TlbStructure.Empty -> {
                0
            }
            is TlbStructure.SwitchPrefix -> {
                struct.variants.maxOf { getMaximumRefs(it.struct, maxRefsFromPreviousDepth) ?: -1 }
            }
            is TlbStructure.LoadRef -> {
                1 + (getMaximumRefs(struct.rest, maxRefsFromPreviousDepth) ?: return null)
            }
            is TlbStructure.KnownTypePrefix -> {
                val add =
                    if (struct.typeLabel is TlbCompositeLabel) {
                        maxRefsFromPreviousDepth[struct.typeLabel]
                            ?: return null
                    } else {
                        0
                    }
                val further =
                    getMaximumRefs(struct.rest, maxRefsFromPreviousDepth)
                        ?: return null
                add + further
            }
        }
    if (cur == -1) {
        return null
    }
    return min(cur, TvmContext.MAX_REFS_NUMBER)
}
