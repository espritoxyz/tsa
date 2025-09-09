package org.usvm.machine.types

import org.ton.TlbStructure

fun <Acc> TlbStructure.fold(
    init: Acc,
    f: (Acc, TlbStructure) -> Acc
): Acc {
    val cur = f(init, this)
    return when (this) {
        is TlbStructure.KnownTypePrefix -> rest.fold(cur, f)
        is TlbStructure.LoadRef -> rest.fold(cur, f)
        is TlbStructure.SwitchPrefix -> variants.fold(cur) { acc, (_, struct) -> struct.fold(acc, f) }
        is TlbStructure.Unknown, is TlbStructure.Empty -> cur
    }
}

fun TlbStructure.forEach(f: (TlbStructure) -> Unit) = fold(Unit) { _, struct -> f(struct) }
