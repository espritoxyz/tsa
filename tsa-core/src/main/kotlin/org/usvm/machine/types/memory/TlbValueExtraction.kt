package org.usvm.machine.types.memory

import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentList
import org.ton.FixedSizeDataLabel
import org.ton.TlbBuiltinLabel
import org.ton.TlbStructure.KnownTypePrefix
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIntepretedValue
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.extractIntFromShiftedData
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import kotlin.math.min

/**
 * This function must be called only after checking that [TlbBuiltinLabel] accepts this [read].
 * */
fun <ReadResult> TlbBuiltinLabel.extractTlbValueIfPossible(
    curStructure: KnownTypePrefix,
    read: TvmCellDataTypeRead<ReadResult>,
    address: UHeapRef,
    path: PersistentList<Int>,
    state: TvmState,
    leftTlbDepth: Int,
): ReadResult? = read.extractTlbValueIfPossible(curStructure, this, address, path, state, leftTlbDepth)

fun extractKBvOfConcreteSizeFromTlbIfPossible(
    curStructure: KnownTypePrefix,
    address: UHeapRef,
    path: PersistentList<Int>,
    state: TvmState,
): UExpr<KBvSort>? =
    with(state.ctx) {
        when (curStructure.typeLabel) {
            is FixedSizeDataLabel -> {
                val field = ConcreteSizeBlockField(curStructure.typeLabel.concreteSize, curStructure.id, path)
                state.memory.readField(address, field, field.getSort(this))
            }

            else -> {
                null
            }
        }
    }

internal fun extractInt(
    offset: UExpr<TvmSizeSort>,
    length: UExpr<TvmSizeSort>,
    data: String,
    isSigned: Boolean,
): UExpr<TvmContext.TvmInt257Sort> =
    with(offset.ctx.tctx()) {
        val bits = mkBv(data, data.length.toUInt()).zeroExtendToSort(cellDataSort)
        val shifted =
            mkBvLogicalShiftRightExpr(
                bits,
                mkSizeSubExpr(mkSizeExpr(data.length), mkSizeAddExpr(offset, length)).zeroExtendToSort(cellDataSort),
            )
        return extractIntFromShiftedData(shifted, length.zeroExtendToSort(int257sort), isSigned)
    }

fun readConcreteBv(
    ctx: TvmContext,
    offset: Int?,
    data: String,
    readSize: UExpr<TvmSizeSort>,
): UExpr<KBvSort>? =
    with(ctx) {
        val requiredReadSize = (readSize as? UIntepretedValue)?.intValue() ?: return null
        val boundedReadSize = min(data.length, requiredReadSize)
        if (boundedReadSize == 0) return null
        val bits = mkBv(data.drop(offset ?: return null).take(boundedReadSize), boundedReadSize.toUInt())
        bits
    }
