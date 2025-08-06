package org.usvm.machine.state

import org.ton.bytecode.TvmContinuation
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmStack.TvmConcreteStackEntry
import org.usvm.machine.state.TvmStack.TvmInputStackEntry
import org.usvm.machine.state.TvmStack.TvmStackTupleValue
import org.usvm.machine.state.TvmStack.TvmStackValue
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmContinuationType
import org.usvm.machine.types.TvmIntegerType
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.TvmRealType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType

data class TypeCastException(
    val oldType: TvmType,
    val newType: TvmType
) : RuntimeException() {
    override val message: String = "Trying to cast $oldType value to $newType"
}

private fun TvmStack.add(value: UExpr<out USort>, type: TvmRealType) {
    // TODO check size 256?
    addStackEntry(value.toStackValue(type).toStackEntry())
}

fun TvmState.addOnStack(value: UExpr<out USort>, type: TvmRealType) {
    stack.add(value, type)
    if (value.sort is UAddressSort) {
        @Suppress("UNCHECKED_CAST")
        assertType(value as UHeapRef, type)
    }
}

fun TvmStepScopeManager.addOnStack(value: UExpr<out USort>, type: TvmRealType) =
    calcOnState { addOnStack(value, type) }

fun TvmStack.addInt(value: UExpr<TvmInt257Sort>) {
    add(value, TvmIntegerType)
}

fun TvmStack.addContinuation(value: TvmContinuation) {
    addStackEntry(TvmStack.TvmStackContinuationValue(value).toStackEntry())
}

fun TvmStack.addTuple(value: TvmStackTupleValue) {
    addStackEntry(value.toStackEntry())
}

fun TvmStackValue.toStackEntry(): TvmConcreteStackEntry = TvmConcreteStackEntry(this)

fun TvmState.takeLastIntOrNull(): UExpr<TvmInt257Sort>? {
    val intStackValue = stack.takeLast(TvmIntegerType) { id ->
        ctx.mkRegisterReading(id, ctx.int257sort)
    }

    if (intStackValue !is TvmStack.TvmStackIntValue) {
        return null
    }

    return intStackValue.intValue
}

fun TvmState.takeLastIntOrThrowTypeError(): UExpr<TvmInt257Sort>? =
    takeLastIntOrNull() ?: run {
        ctx.throwTypeCheckError(this)
        null
    }

fun TvmStepScopeManager.takeLastIntOrThrowTypeError(): UExpr<TvmInt257Sort>? =
    calcOnState { takeLastIntOrThrowTypeError() }

fun TvmStepScopeManager.takeLastSliceOrThrowTypeError(): UHeapRef? = with(ctx) {
    val slice = this@takeLastSliceOrThrowTypeError.calcOnState { takeLastSlice() }
    if (slice == null) {
        doWithState(throwTypeCheckError)
    }
    slice
}

fun TvmState.takeLastCell(): UHeapRef? =
    takeLastRef(TvmCellType, TvmStackValue::cellValue) {
        generateSymbolicCell()
    }?.also { ensureSymbolicCellInitialized(it) }

fun TvmStepScopeManager.takeLastCell(): UHeapRef? =
    calcOnState { takeLastCell() }

fun TvmState.takeLastSlice(): UHeapRef? =
    takeLastRef(TvmSliceType, TvmStackValue::sliceValue) {
        generateSymbolicSlice()
    }?.also { ensureSymbolicSliceInitialized(it) }

fun TvmState.takeLastBuilder(): UConcreteHeapRef? =
    takeLastRef(TvmBuilderType, TvmStackValue::builderValue) {
        generateSymbolicBuilder()
    }?.also { ensureSymbolicBuilderInitialized(it) }

fun TvmState.takeLastRef(type: TvmRealReferenceType): UHeapRef? = when (type) {
    is TvmCellType -> takeLastCell()
    is TvmSliceType -> takeLastSlice()
    is TvmBuilderType -> takeLastBuilder()
}

fun TvmStepScopeManager.takeLastTuple(): TvmStackTupleValue? = calcOnStateCtx {
    val lastEntry = stack.takeLastEntry()

    when (lastEntry) {
        is TvmConcreteStackEntry -> lastEntry.cell(stack) as? TvmStackTupleValue
        is TvmInputStackEntry -> {
            val cell = lastEntry.cell(stack)
            if (cell != null) {
                return@calcOnStateCtx cell as? TvmStackTupleValue
            }

            val size = ctx.mkRegisterReading(lastEntry.id, ctx.int257sort)
            val sizeConstraint = mkAnd(
                mkBvSignedLessOrEqualExpr(zeroValue, size),
                mkBvSignedLessOrEqualExpr(size, maxTupleSizeValue)
            )
            assert(
                sizeConstraint,
                unsatBlock = { error("Cannot assert tuple size constraints") }
            ) ?: return@calcOnStateCtx null

            val symbolicTuple = TvmStack.TvmStackTupleValueInputNew(entries = mutableMapOf(), size = size)
            symbolicTuple.also {
                stack.putInputEntryValue(lastEntry, it)
            }
        }
    }
}

fun TvmStack.takeLastContinuation(): TvmContinuation? {
    val continuationStackValue = takeLast(TvmContinuationType) { _ ->
        error("Unexpected continuation as an input")
    }

    return continuationStackValue.continuationValue
}

private fun <Ref : UHeapRef> TvmState.takeLastRef(
    referenceType: TvmRealReferenceType,
    extractValue: TvmStackValue.() -> Ref?,
    generateSymbolicRef: (Int) -> UHeapRef
): Ref? {
    val lastRefValue = stack.takeLast(referenceType, generateSymbolicRef)
    return lastRefValue.extractValue()?.also { assertType(it, referenceType) }
}

fun doXchg(scope: TvmStepScopeManager, first: Int, second: Int) {
    scope.doWithState {
        stack.swap(first, second)
    }
}

fun doSwap(scope: TvmStepScopeManager) = doXchg(scope, first = 0, second = 1)

fun doPop(scope: TvmStepScopeManager, i: Int) {
    scope.doWithState {
        stack.pop(i)
    }
}

fun doPush(scope: TvmStepScopeManager, i: Int) {
    scope.doWithState {
        stack.push(i)
    }
}

fun TvmStack.doBlkSwap(i: Int, j: Int) {
    reverse(i + 1, j + 1)
    reverse(j + 1, 0)
    reverse(i + j + 2, 0)
}

fun TvmStack.doPuxc(i: Int, j: Int) {
    push(i)
    swap(0, 1)
    swap(0, j + 1)
}

fun TvmStack.doXchg2(i: Int, j: Int) {
    swap(1, i)
    swap(0, j)
}

fun TvmStack.doXchg3(i: Int, j: Int, k: Int) {
    swap(2, i)
    swap(1, j)
    swap(0, k)
}
