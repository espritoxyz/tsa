package org.usvm.machine.types.dp

import org.ton.TlbAtomicLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.machine.TvmContext
import org.usvm.machine.types.lengthUpperBound
import kotlin.math.min

/**
 * Maximum TL-B depth is calculated based on data length.
 * */
fun calculateMaxCellTlbDepths(
    maxTlbDepth: Int,
    labels: Collection<TlbCompositeLabel>,
): Map<TlbCompositeLabel, Int> {
    val result = hashMapOf<TlbCompositeLabel, Int>()

    // Calculate trimmed maximum possible data length
    // Then, based on that, calculate maximum TL-B depth
    calculateMapsByTlbDepth(maxTlbDepth, labels) { label, curDepth, prevDepthValues ->
        val curResult = result[label]
        if (curResult != null) {
            return@calculateMapsByTlbDepth TvmContext.MAX_DATA_LENGTH + 1
        }

        val value = getMaxCellLength(label.internalStructure, prevDepthValues)
        if (value != null && value > TvmContext.MAX_DATA_LENGTH) {
            result[label] = curDepth - 1
        }

        value?.let { min(it, TvmContext.MAX_DATA_LENGTH + 1) }
    }

    labels.forEach { label ->
        if (label !in result) {
            result[label] = maxTlbDepth
        }
    }

    return result
}

private fun getMaxCellLength(
    struct: TlbStructure,
    maxLengthFromPreviousDepth: Map<TlbCompositeLabel, Int>,
): Int? {
    return when (struct) {
        is TlbStructure.Unknown, is TlbStructure.Empty -> {
            0
        }

        is TlbStructure.LoadRef -> {
            getMaxCellLength(struct.rest, maxLengthFromPreviousDepth)
        }

        is TlbStructure.KnownTypePrefix -> {
            val further =
                getMaxCellLength(struct.rest, maxLengthFromPreviousDepth)
                    ?: return null

            val offset =
                when (struct.typeLabel) {
                    is TlbAtomicLabel -> struct.typeLabel.lengthUpperBound()
                    is TlbCompositeLabel -> maxLengthFromPreviousDepth[struct.typeLabel]
                } ?: return null

            offset + further
        }

        is TlbStructure.SwitchPrefix -> {
            var longestVariant: Int? = null
            for (variant in struct.variants) {
                val further = getMaxCellLength(variant.struct, maxLengthFromPreviousDepth)
                if (further != null && (longestVariant == null || longestVariant < further)) {
                    longestVariant = further
                }
            }

            longestVariant?.let { it + struct.switchSize }
        }
    }
}
