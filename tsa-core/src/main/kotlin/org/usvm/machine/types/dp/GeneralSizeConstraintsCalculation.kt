package org.usvm.machine.types.dp

import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbAtomicLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.dataLength
import org.usvm.machine.types.memory.generateGuardForSwitch
import org.usvm.machine.types.memory.typeArgs
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr

/**
 * Returns size information for lists (Empty or Unknown).
 * */
fun calculateSizeInfoForLeaves(
    address: UConcreteHeapRef,
    state: TvmState,
    structure: TlbStructure,
    dataLengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr>,
    childrenStructureFromPreviousDepth: Map<TlbCompositeLabel, ChildrenStructure>,
    possibleSwitchVariants: Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>,
): List<Pair<TlbStructure.Leaf, VertexCalculatedSize>> =
    with(state.ctx) {
        val result = mutableListOf<Pair<TlbStructure.Leaf, VertexCalculatedSize>>()

        val bfsQueue = ArrayDeque(listOf(structure to VertexCalculatedSize(trueExpr, zeroSizeExpr, zeroSizeExpr)))

        while (bfsQueue.isNotEmpty()) {
            val (curStruct, curSize) = bfsQueue.removeFirst()

            when (curStruct) {
                is TlbStructure.Leaf -> {
                    result.add(curStruct to curSize)
                }

                is TlbStructure.LoadRef -> {
                    val nextStruct = curStruct.rest
                    val newSize =
                        VertexCalculatedSize(
                            curSize.guard,
                            dataLength = curSize.dataLength,
                            refsLength = mkSizeAddExpr(curSize.refsLength, oneSizeExpr),
                        )
                    bfsQueue.add(nextStruct to newSize)
                }

                is TlbStructure.KnownTypePrefix -> {
                    val nextStruct = curStruct.rest
                    val (curDataOffset, curRefsOffset) =
                        when (val label = curStruct.typeLabel) {
                            is TlbAtomicLabel -> {
                                val args = curStruct.typeArgs(state, address, emptyList())
                                label.dataLength(state, args) to zeroSizeExpr
                            }
                            is TlbCompositeLabel -> {
                                val dataOffset =
                                    dataLengthsFromPreviousDepth[label]
                                        ?: error("dataLengthsFromPreviousDepth for $label must be calculated")
                                val childrenStructure =
                                    childrenStructureFromPreviousDepth[label]
                                        ?: error("childrenStructureFromPreviousDepth for $label must be calculated")
                                val param = AbstractionForUExpr(address, persistentListOf(curStruct.id), state)
                                val refsOffset = childrenStructure.numberOfChildren(this).apply(param)
                                dataOffset.apply(param) to refsOffset
                            }
                        }
                    val newSize =
                        VertexCalculatedSize(
                            curSize.guard,
                            dataLength = mkSizeAddExpr(curSize.dataLength, curDataOffset),
                            refsLength = mkSizeAddExpr(curSize.refsLength, curRefsOffset),
                        )
                    bfsQueue.add(nextStruct to newSize)
                }

                is TlbStructure.SwitchPrefix -> {
                    val newDataLength = mkSizeAddExpr(curSize.dataLength, mkSizeExpr(curStruct.switchSize))
                    val possibleVariants =
                        possibleSwitchVariants[curStruct]
                            ?: error("Switch variants not found for switch $curStruct")

                    possibleVariants.forEachIndexed { idx, (_, nextStruct) ->
                        val switchGuard =
                            generateGuardForSwitch(curStruct, idx, possibleVariants, state, address, persistentListOf())
                        val newSize =
                            VertexCalculatedSize(
                                curSize.guard and switchGuard,
                                dataLength = newDataLength,
                                refsLength = curSize.refsLength,
                            )
                        bfsQueue.add(nextStruct to newSize)
                    }
                }
            }
        }

        return result
    }

data class VertexCalculatedSize(
    val guard: UBoolExpr,
    val dataLength: UExpr<TvmSizeSort>,
    val refsLength: UExpr<TvmSizeSort>,
)

fun calculateGeneralSizeConstraints(
    address: UConcreteHeapRef,
    state: TvmState,
    structure: TlbStructure,
    dataLengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr>,
    childrenStructureFromPreviousDepth: Map<TlbCompositeLabel, ChildrenStructure>,
    possibleSwitchVariants: Map<TlbStructure.SwitchPrefix, List<TlbStructure.SwitchPrefix.SwitchVariant>>,
): UBoolExpr =
    with(state.ctx) {
        val dataLengthField =
            state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(state, address)
        val refsLengthField = state.fieldManagers.cellRefsLengthFieldManager.readCellRefLength(state, address)

        val info =
            calculateSizeInfoForLeaves(
                address,
                state,
                structure,
                dataLengthsFromPreviousDepth,
                childrenStructureFromPreviousDepth,
                possibleSwitchVariants,
            )

        return info.fold(trueExpr as UBoolExpr) { acc, (leaf, sizeInfo) ->
            when (leaf) {
                is TlbStructure.Unknown -> {
                    val newGuard =
                        mkSizeGeExpr(dataLengthField, sizeInfo.dataLength) and
                            mkSizeGeExpr(refsLengthField, sizeInfo.refsLength)
                    acc and (sizeInfo.guard implies newGuard)
                }
                is TlbStructure.Empty -> {
                    val newGuard = (dataLengthField eq sizeInfo.dataLength) and (refsLengthField eq sizeInfo.refsLength)
                    acc and (sizeInfo.guard implies newGuard)
                }
            }
        }
    }
