package org.usvm.machine.state

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USymbol
import org.usvm.machine.TvmContext
import org.usvm.machine.intblast.TvmTransformer

/**
 * `account_id` is the last 256 bits of the address.
 * We assume that [symbolicAccountId] is never compared to any symbolic hashes in path constraints of state `st`.
 * This means that (hopefully) the models of [symbolicAccountId] (when [isStateInit] is modeled to `false`)
 * represent the senders that are allowed to trigger the execution modeled by the `st` exluding the senders that satisfy
 * the code authorization constraints.
 */
class TsaAccountId(
    ctx: TvmContext,
    val symbolicAccountId: UExpr<UBvSort>,
    val isStateInit: UBoolExpr,
    val code: UConcreteHeapRef,
    val data: UConcreteHeapRef,
    val boundStateInitHash: UExpr<UBvSort>,
) : USymbol<UBvSort>(ctx) {
    override val sort: UBvSort
        get() = ctx.mkBvSort(256u)

    override fun print(printer: ExpressionPrinter) {
        printer.append("myaddr<")
        printer.append(symbolicAccountId)
        printer.append(">")
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { symbolicAccountId },
            { isStateInit },
            { code },
            { data },
            { boundStateInitHash },
        )

    override fun internHashCode(): Int = hash(symbolicAccountId, isStateInit, code, data, boundStateInitHash)

    override fun accept(transformer: KTransformerBase): KExpr<UBvSort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }
}
