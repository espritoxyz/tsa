package org.usvm.machine

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.collection.map.primitive.UMapRegion
import org.usvm.collection.map.primitive.UMapRegionId
import org.usvm.collection.set.primitive.USetRegion
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isStaticHeapRef
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.memory.foldHeapRef
import org.usvm.regions.Region
import org.usvm.uctx

// todo: remove this bunch of internal functions, copied from usvm-core

private const val LEFT_CHILD = 0
private const val RIGHT_CHILD = 1
private const val DONE = 2


/**
 * Reassembles [this] non-recursively with applying [concreteMapper] on allocated [UConcreteHeapRef], [staticMapper] on
 * static [UConcreteHeapRef], and [symbolicMapper] on [USymbolicHeapRef]. Respects [UIteExpr], so the structure of
 * the result expression will be the same as [this] is, but implicit simplifications may occur.
 *
 * @param ignoreNullRefs if true, then null references will be ignored. It means that all leafs with nulls
 * considered unsatisfiable, so we assume their guards equal to false. If [ignoreNullRefs] is true and [this] is
 * [UNullRef], throws an [IllegalArgumentException].
 */
internal inline fun <Sort : USort> UHeapRef.map(
    concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    staticMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    symbolicMapper: (USymbolicHeapRef) -> UExpr<Sort>,
    ignoreNullRefs: Boolean = true,
): UExpr<Sort> = when {
    isStaticHeapRef(this) -> staticMapper(this)
    this is UConcreteHeapRef -> concreteMapper(this)
    this is UNullRef -> {
        require(!ignoreNullRefs) { "Got nullRef on the top!" }
        symbolicMapper(this)
    }

    this is USymbolicHeapRef -> symbolicMapper(this)
    this is UIteExpr<UAddressSort> -> {
        /**
         * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
         * unprocessed child of the pair.first (`0` means the left child, `1` means the right child).
         */
        val nodeToChild = mutableListOf<Pair<UHeapRef, Int>>()
        val completelyMapped = mutableListOf<UExpr<Sort>>()

        nodeToChild.add(this to LEFT_CHILD)


        while (nodeToChild.isNotEmpty()) {
            val (ref, state) = nodeToChild.removeLast()
            when {
                isStaticHeapRef(ref) -> completelyMapped += staticMapper(ref)
                ref is UConcreteHeapRef -> completelyMapped += concreteMapper(ref)
                ref is USymbolicHeapRef -> completelyMapped += symbolicMapper(ref)
                ref is UIteExpr<UAddressSort> -> {

                    when (state) {
                        LEFT_CHILD -> {
                            when {
                                ignoreNullRefs && ref.trueBranch == uctx.nullRef -> {
                                    nodeToChild += ref.falseBranch to LEFT_CHILD
                                }

                                ignoreNullRefs && ref.falseBranch == uctx.nullRef -> {
                                    nodeToChild += ref.trueBranch to LEFT_CHILD
                                }

                                else -> {
                                    nodeToChild += ref to RIGHT_CHILD
                                    nodeToChild += ref.trueBranch to LEFT_CHILD
                                }
                            }
                        }

                        RIGHT_CHILD -> {
                            nodeToChild += ref to DONE
                            nodeToChild += ref.falseBranch to LEFT_CHILD
                        }

                        DONE -> {
                            // we firstly process the left child of [cur], so it will be under the top of the stack
                            // the top of the stack will be the right child
                            val rhs = completelyMapped.removeLast()
                            val lhs = completelyMapped.removeLast()
                            completelyMapped += ctx.mkIte(ref.condition, lhs, rhs)
                        }
                    }
                }
            }
        }

        completelyMapped.single()
    }

    else -> error("Unexpected ref: $this")
}

/**
 * Executes [foldHeapRef] with passed [concreteMapper] as a staticMapper.
 */
internal inline fun <Sort : USort> UHeapRef.mapWithStaticAsConcrete(
    concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    symbolicMapper: (USymbolicHeapRef) -> UExpr<Sort>,
    ignoreNullRefs: Boolean = true,
): UExpr<Sort> = map(
    concreteMapper,
    staticMapper = concreteMapper,
    symbolicMapper,
    ignoreNullRefs
)


internal fun <SetType, KeySort : USort, Reg : Region<Reg>> UWritableMemory<*>.setUnion(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: SetType,
    keySort: KeySort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    guard: UBoolExpr,
    ownership: MutabilityOwnership,
) {
    val regionId = USetRegionId(keySort, type, keyInfo)
    val region = getRegion(regionId)

    check(region is USetRegion<SetType, KeySort, Reg>) {
        "setUnion is not applicable to $region"
    }

    val newRegion = region.union(srcRef, dstRef, guard, ownership)
    setRegion(regionId, newRegion)
}

internal fun <MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> UWritableMemory<*>.mapMerge(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    mapType: MapType,
    keySort: KeySort,
    sort: ValueSort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    keySet: USetRegionId<MapType, KeySort, Nothing>,
    guard: UBoolExpr,
    ownership: MutabilityOwnership,
) {
    val regionId = UMapRegionId(keySort, sort, mapType, keyInfo)
    val region = getRegion(regionId)

    check(region is UMapRegion<MapType, KeySort, ValueSort, Reg>) {
        "mapMerge is not applicable to $region"
    }

    val keySetRegion = getRegion(keySet)
    check(keySetRegion is USetRegion<MapType, KeySort, *>) {
        "mapMerge is not applicable to set $region"
    }

    val newRegion = region.merge(srcRef, dstRef, mapType, keySetRegion, guard, ownership)
    setRegion(regionId, newRegion)
}
