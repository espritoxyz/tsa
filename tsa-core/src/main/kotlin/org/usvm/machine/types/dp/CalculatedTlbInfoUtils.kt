package org.usvm.machine.types.dp

import kotlinx.collections.immutable.PersistentList
import org.ton.TlbAtomicLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TvmParameterInfo
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.dataLength
import org.usvm.machine.types.memory.typeArgs
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr

data class AbstractionForUExpr(
    val ref: UConcreteHeapRef,
    val path: PersistentList<Int>,
    val state: TvmState,
) {
    val ctx: TvmContext
        get() = state.ctx

    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        AbstractionForUExpr(
            ref,
            path.add(struct.id),
            state,
        )
}

@JvmInline
value class AbstractGuard(
    val apply: (AbstractionForUExpr) -> UBoolExpr,
) {
    infix fun or(other: AbstractGuard) =
        AbstractGuard {
            with(it.ctx) {
                apply(it) or other.apply(it)
            }
        }

    infix fun and(other: AbstractGuard) =
        AbstractGuard {
            with(it.ctx) {
                apply(it) and other.apply(it)
            }
        }

    fun not() =
        AbstractGuard {
            with(it.ctx) {
                apply(it).not()
            }
        }

    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        AbstractGuard { param ->
            val newParam = param.addTlbLevel(struct)
            apply(newParam)
        }

    companion object {
        fun abstractTrue(): AbstractGuard = AbstractGuard { it.ctx.trueExpr }

        fun abstractFalse(): AbstractGuard = AbstractGuard { it.ctx.falseExpr }
    }
}

@JvmInline
value class AbstractSizeExpr(
    val apply: (AbstractionForUExpr) -> UExpr<TvmSizeSort>,
) {
    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        AbstractSizeExpr { param ->
            val newParam = param.addTlbLevel(struct)
            apply(newParam)
        }
}

fun AbstractSizeExpr.add(numOfBits: AbstractSizeExpr) =
    AbstractSizeExpr { param ->
        val offset = numOfBits.apply(param)
        param.ctx.mkSizeAddExpr(offset, apply(param))
    }

class ChildrenStructure(
    val children: List<ChildStructure>,
    val numberOfChildrenExceeded: AbstractGuard,
) {
    init {
        require(children.size == TvmContext.MAX_REFS_NUMBER)
    }

    fun exactNumberOfChildren(num: Int): AbstractGuard {
        require(num in 0..TvmContext.MAX_REFS_NUMBER)
        return when (num) {
            0 -> children[0].exists().not()
            TvmContext.MAX_REFS_NUMBER ->
                children[TvmContext.MAX_REFS_NUMBER - 1].exists() and
                    numberOfChildrenExceeded.not()

            else -> children[num - 1].exists() and children[num].exists().not()
        }
    }

    fun numberOfChildren(ctx: TvmContext): AbstractSizeExpr =
        with(ctx) {
            AbstractSizeExpr { param ->
                children.foldIndexed(zeroSizeExpr) { childIndex, acc, struct ->
                    mkIte(
                        struct.exists().apply(param),
                        trueBranch = mkSizeExpr(childIndex + 1),
                        falseBranch = acc,
                    )
                }
            }
        }

    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        ChildrenStructure(
            children.map { it.addTlbLevel(struct) },
            numberOfChildrenExceeded.addTlbLevel(struct),
        )

    infix fun and(newGuard: AbstractGuard) =
        ChildrenStructure(
            children.map { it and newGuard },
            numberOfChildrenExceeded and newGuard,
        )

    infix fun union(other: ChildrenStructure) =
        ChildrenStructure(
            (children zip other.children).map { (x, y) -> x union y },
            numberOfChildrenExceeded or other.numberOfChildrenExceeded,
        )

    companion object {
        fun empty(): ChildrenStructure =
            ChildrenStructure(
                List(TvmContext.MAX_REFS_NUMBER) { ChildStructure(emptyMap()) },
                AbstractGuard.abstractFalse(),
            )
    }
}

class ChildStructure(
    val variants: Map<TvmParameterInfo.CellInfo, AbstractGuard>,
) {
    fun exists(): AbstractGuard =
        variants.values.fold(AbstractGuard.abstractFalse()) { acc, guard ->
            acc or guard
        }

    fun addTlbLevel(addedStruct: TlbStructure.KnownTypePrefix) =
        ChildStructure(
            variants.entries.associate { (struct, guard) ->
                struct to guard.addTlbLevel(addedStruct)
            },
        )

    infix fun union(other: ChildStructure): ChildStructure {
        val result = variants.toMutableMap()
        other.variants.entries.forEach { (struct, guard) ->
            val oldValue = result[struct]
            result[struct] = oldValue?.let { it or guard } ?: guard
        }
        return ChildStructure(result)
    }

    infix fun and(newGuard: AbstractGuard) =
        ChildStructure(
            variants.entries.associate { (struct, guard) ->
                struct to (guard and newGuard)
            },
        )
}

fun getKnownTypePrefixDataLength(
    struct: TlbStructure.KnownTypePrefix,
    lengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr>,
): AbstractSizeExpr? =
    when (struct.typeLabel) {
        is TlbAtomicLabel -> {
            AbstractSizeExpr { param ->
                val typeArgs = struct.typeArgs(param.state, param.ref, param.path)
                struct.typeLabel.dataLength(param.state, typeArgs)
            }
        }
        is TlbCompositeLabel -> {
            lengthsFromPreviousDepth[struct.typeLabel]?.addTlbLevel(struct)
        }
    }

fun <Key, Value> calculateMapsByTlbDepth(
    maxTlbDepth: Int,
    keys: Iterable<Key>,
    makeCalculation: (Key, Int, Map<Key, Value>) -> Value?,
): List<Map<Key, Value>> {
    var cur = mapOf<Key, Value>()
    val result = mutableListOf<Map<Key, Value>>()

    for (curDepth in 0..maxTlbDepth) {
        val newMap = hashMapOf<Key, Value>()
        keys.forEach { label ->
            val newValue = makeCalculation(label, curDepth, cur)
            newValue?.let {
                newMap += label to it
            }
        }
        cur = newMap
        result.add(cur)
    }

    return result
}
