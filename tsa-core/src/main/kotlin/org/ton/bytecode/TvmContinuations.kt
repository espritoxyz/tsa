package org.ton.bytecode

import org.usvm.UExpr
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.state.C0Register
import org.usvm.machine.state.C1Register
import org.usvm.machine.state.C2Register
import org.usvm.machine.state.C3Register
import org.usvm.machine.state.C4Register
import org.usvm.machine.state.C5Register
import org.usvm.machine.state.C7Register
import org.usvm.machine.state.TvmStack

data class TvmRegisterSavelist(
    val c0: C0Register? = null,
    val c1: C1Register? = null,
    val c2: C2Register? = null,
    val c3: C3Register? = null,
    val c4: C4Register? = null,
    val c5: C5Register? = null,
    val c7: C7Register? = null,
) {
    companion object {
        val EMPTY = TvmRegisterSavelist()
    }
}

sealed interface TvmContinuation {
    val savelist: TvmRegisterSavelist
    val stack: TvmStack?
    val nargs: UInt?

    fun update(
        newSavelist: TvmRegisterSavelist = savelist,
        newStack: TvmStack? = stack,
        newNargs: UInt? = nargs,
    ): TvmContinuation
}

/**
 * A continuation used to mark the end of a successful program execution with exit code [exitCode]
 */
data class TvmQuitContinuation(
    val exitCode: UInt,
) : TvmContinuation {
    override val savelist
        get() = TvmRegisterSavelist.EMPTY

    override val stack: TvmStack?
        get() = null

    override val nargs: UInt?
        get() = null

    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmQuitContinuation = this
}

/**
 * Default exception handler
 */
data object TvmExceptionContinuation : TvmContinuation {
    override val savelist: TvmRegisterSavelist
        get() = TvmRegisterSavelist.EMPTY

    override val stack: TvmStack?
        get() = null

    override val nargs: UInt?
        get() = null

    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmExceptionContinuation = this
}

data class TvmOrdContinuation(
    val stmt: TvmInst,
    val sourceCell: TvmCell?,
    override val savelist: TvmRegisterSavelist = TvmRegisterSavelist.EMPTY,
    override val stack: TvmStack? = null,
    override val nargs: UInt? = null,
) : TvmContinuation {
    constructor(
        codeBlock: TvmCodeBlock,
        sourceCell: TvmCell?,
        savelist: TvmRegisterSavelist = TvmRegisterSavelist.EMPTY,
        stack: TvmStack? = null,
        nargs: UInt? = null,
    ) : this(codeBlock.instList.first(), sourceCell, savelist, stack, nargs)

    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmOrdContinuation = copy(savelist = newSavelist, stack = newStack, nargs = newNargs)
}

/**
 * [TvmOrdContinuation] wrapper that marks the [method] return site
 */
data class TvmMethodReturnContinuation(
    val method: MethodId,
    val returnSite: TvmOrdContinuation,
) : TvmContinuation {
    override val savelist: TvmRegisterSavelist
        get() = returnSite.savelist
    override val stack: TvmStack?
        get() = returnSite.stack
    override val nargs: UInt?
        get() = returnSite.nargs

    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmMethodReturnContinuation = copy(returnSite = returnSite.update(newSavelist, newStack, newNargs))
}

/**
 * A continuation used to count loop iterations using [TsaArtificialLoopEntranceInst]
 */
data class TvmLoopEntranceContinuation(
    val loopBody: TvmContinuation,
    val id: UInt,
    val parentLocation: TvmInstLocation,
) : TvmContinuation {
    override val savelist: TvmRegisterSavelist
        get() = TvmRegisterSavelist.EMPTY

    override val stack: TvmStack?
        get() = null

    override val nargs: UInt?
        get() = null

    val codeBlock =
        TvmLambda(
            mutableListOf(
                TsaArtificialLoopEntranceInst(id, TvmInstLambdaLocation(0).also { it.parent = parentLocation }),
                TsaArtificialJmpToContInst(loopBody, TvmInstLambdaLocation(1).also { it.parent = parentLocation })
            )
        )

    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmLoopEntranceContinuation = error("Unexpected call")
}

data class TvmUntilContinuation(
    val body: TvmLoopEntranceContinuation,
    val after: TvmContinuation,
    override val savelist: TvmRegisterSavelist = TvmRegisterSavelist.EMPTY,
    override val stack: TvmStack? = null,
    override val nargs: UInt? = null,
) : TvmContinuation {
    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmUntilContinuation = copy(savelist = newSavelist, stack = newStack, nargs = newNargs)
}

data class TvmRepeatContinuation(
    val body: TvmLoopEntranceContinuation,
    val after: TvmContinuation,
    val count: UExpr<TvmInt257Sort>,
    override val savelist: TvmRegisterSavelist = TvmRegisterSavelist.EMPTY,
    override val stack: TvmStack? = null,
    override val nargs: UInt? = null,
) : TvmContinuation {
    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmRepeatContinuation = copy(savelist = newSavelist, stack = newStack, nargs = newNargs)
}

/**
 * @property isCondition flag indicating which continuation is currently running
 */
data class TvmWhileContinuation(
    val condition: TvmLoopEntranceContinuation,
    val body: TvmContinuation,
    val after: TvmContinuation,
    val isCondition: Boolean,
    override val savelist: TvmRegisterSavelist = TvmRegisterSavelist.EMPTY,
    override val stack: TvmStack? = null,
    override val nargs: UInt? = null,
) : TvmContinuation {
    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmWhileContinuation = copy(savelist = newSavelist, stack = newStack, nargs = newNargs)
}

data class TvmAgainContinuation(
    val body: TvmLoopEntranceContinuation,
    override val savelist: TvmRegisterSavelist = TvmRegisterSavelist.EMPTY,
    override val stack: TvmStack? = null,
    override val nargs: UInt? = null,
) : TvmContinuation {
    override fun update(
        newSavelist: TvmRegisterSavelist,
        newStack: TvmStack?,
        newNargs: UInt?,
    ): TvmAgainContinuation = copy(savelist = newSavelist, stack = newStack, nargs = newNargs)
}
