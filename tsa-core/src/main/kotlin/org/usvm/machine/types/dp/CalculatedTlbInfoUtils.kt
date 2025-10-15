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
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.dataLength
import org.usvm.machine.types.memory.typeArgs
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr

sealed interface AbstractionForUExpr<NewAbstraction> {
    val ref: UConcreteHeapRef
    val path: PersistentList<Int>
    val state: TvmState

    val ctx: TvmContext
        get() = ref.ctx.tctx()

    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix): NewAbstraction
}

data class SimpleAbstractionForUExpr(
    override val ref: UConcreteHeapRef,
    override val path: PersistentList<Int>,
    override val state: TvmState,
) : AbstractionForUExpr<SimpleAbstractionForUExpr> {
    override fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        SimpleAbstractionForUExpr(
            ref,
            path.add(0, struct.id),
            state,
        )
}

data class AbstractionForUExprWithCellDataPrefix(
    override val ref: UConcreteHeapRef,
    val prefixSize: UExpr<TvmSizeSort>,
    override val path: PersistentList<Int>,
    override val state: TvmState,
) : AbstractionForUExpr<AbstractionForUExprWithCellDataPrefix> {
    override fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        AbstractionForUExprWithCellDataPrefix(ref, prefixSize, path.add(0, struct.id), state)
}

@JvmInline
value class AbstractGuard<Abstraction : AbstractionForUExpr<Abstraction>>(
    val apply: (Abstraction) -> UBoolExpr,
) {
    infix fun or(other: AbstractGuard<Abstraction>) =
        AbstractGuard<Abstraction> {
            with(it.ctx) {
                apply(it) or other.apply(it)
            }
        }

    infix fun and(other: AbstractGuard<Abstraction>) =
        AbstractGuard<Abstraction> {
            with(it.ctx) {
                apply(it) and other.apply(it)
            }
        }

    fun not() =
        AbstractGuard<Abstraction> {
            with(it.ctx) {
                apply(it).not()
            }
        }

    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        AbstractGuard<Abstraction> { param ->
            val newParam = param.addTlbLevel(struct)
            apply(newParam)
        }

    companion object {
        fun <Abstraction : AbstractionForUExpr<Abstraction>> abstractTrue(): AbstractGuard<Abstraction> =
            AbstractGuard { it.ctx.trueExpr }

        fun <Abstraction : AbstractionForUExpr<Abstraction>> abstractFalse(): AbstractGuard<Abstraction> =
            AbstractGuard { it.ctx.falseExpr }
    }
}

fun AbstractGuard<AbstractionForUExprWithCellDataPrefix>.shift(
    numOfBits: AbstractSizeExpr<AbstractionForUExprWithCellDataPrefix>,
) = AbstractGuard<AbstractionForUExprWithCellDataPrefix> { param ->
    val offset = numOfBits.apply(param)
    val (address, prefixSize, path, state) = param
    apply(AbstractionForUExprWithCellDataPrefix(address, param.ctx.mkSizeAddExpr(prefixSize, offset), path, state))
}

@JvmInline
value class AbstractSizeExpr<Abstraction : AbstractionForUExpr<Abstraction>>(
    val apply: (Abstraction) -> UExpr<TvmSizeSort>,
) {
    fun addTlbLevel(struct: TlbStructure.KnownTypePrefix) =
        AbstractSizeExpr<Abstraction> { param ->
            val newParam = param.addTlbLevel(struct)
            apply(newParam)
        }
}

fun AbstractSizeExpr<SimpleAbstractionForUExpr>.add(numOfBits: AbstractSizeExpr<SimpleAbstractionForUExpr>) =
    AbstractSizeExpr<SimpleAbstractionForUExpr> { param ->
        val offset = numOfBits.apply(param)
        param.ctx.mkSizeAddExpr(offset, apply(param))
    }

fun AbstractSizeExpr<SimpleAbstractionForUExpr>.convert(): AbstractSizeExpr<AbstractionForUExprWithCellDataPrefix> =
    AbstractSizeExpr { param ->
        apply(SimpleAbstractionForUExpr(param.ref, param.path, param.state))
    }

class ChildrenStructure<Abstraction : AbstractionForUExpr<Abstraction>>(
    val children: List<ChildStructure<Abstraction>>,
    val numberOfChildrenExceeded: AbstractGuard<Abstraction>,
) {
    init {
        require(children.size == TvmContext.MAX_REFS_NUMBER)
    }

    fun exactNumberOfChildren(
        ctx: TvmContext,
        num: Int,
    ): AbstractGuard<Abstraction> =
        with(ctx) {
            require(num in 0..TvmContext.MAX_REFS_NUMBER)
            when (num) {
                0 -> children[0].exists().not()
                TvmContext.MAX_REFS_NUMBER ->
                    children[TvmContext.MAX_REFS_NUMBER - 1].exists() and
                        numberOfChildrenExceeded.not()
                else -> children[num - 1].exists() and children[num].exists().not()
            }
        }

    fun numberOfChildren(ctx: TvmContext): AbstractSizeExpr<Abstraction> =
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

    infix fun and(newGuard: AbstractGuard<Abstraction>) =
        ChildrenStructure(
            children.map { it and newGuard },
            numberOfChildrenExceeded and newGuard,
        )

    infix fun union(other: ChildrenStructure<Abstraction>) =
        ChildrenStructure(
            (children zip other.children).map { (x, y) -> x union y },
            numberOfChildrenExceeded or other.numberOfChildrenExceeded,
        )

    companion object {
        fun <Abstraction : AbstractionForUExpr<Abstraction>> empty(): ChildrenStructure<Abstraction> =
            ChildrenStructure(
                List(TvmContext.MAX_REFS_NUMBER) { ChildStructure(emptyMap()) },
                AbstractGuard.abstractFalse(),
            )
    }
}

class ChildStructure<Abstraction : AbstractionForUExpr<Abstraction>>(
    val variants: Map<TvmParameterInfo.CellInfo, AbstractGuard<Abstraction>>,
) {
    fun exists(): AbstractGuard<Abstraction> =
        variants.values.fold(AbstractGuard.abstractFalse()) { acc, guard ->
            acc or guard
        }

    fun addTlbLevel(addedStruct: TlbStructure.KnownTypePrefix) =
        ChildStructure(
            variants.entries.associate { (struct, guard) ->
                struct to guard.addTlbLevel(addedStruct)
            },
        )

    infix fun union(other: ChildStructure<Abstraction>): ChildStructure<Abstraction> {
        val result = variants.toMutableMap()
        other.variants.entries.forEach { (struct, guard) ->
            val oldValue = result[struct]
            result[struct] = oldValue?.let { it or guard } ?: guard
        }
        return ChildStructure(result)
    }

    infix fun and(newGuard: AbstractGuard<Abstraction>) =
        ChildStructure(
            variants.entries.associate { (struct, guard) ->
                struct to (guard and newGuard)
            },
        )
}

fun <Abstraction : AbstractionForUExpr<Abstraction>> getKnownTypePrefixDataLength(
    struct: TlbStructure.KnownTypePrefix,
    lengthsFromPreviousDepth: Map<TlbCompositeLabel, AbstractSizeExpr<Abstraction>>,
): AbstractSizeExpr<Abstraction>? =
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
