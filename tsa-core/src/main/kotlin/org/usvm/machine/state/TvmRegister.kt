package org.usvm.machine.state

import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmCellValue
import org.ton.bytecode.TvmContinuation
import org.ton.bytecode.TvmExceptionContinuation
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew

interface TvmRegister

// TODO registers should contain symbolic values, not concrete?
data class C0Register(
    val value: TvmContinuation,
) : TvmRegister

data class C1Register(
    val value: TvmContinuation,
) : TvmRegister

data class C2Register(
    val value: TvmContinuation,
) : TvmRegister

data class C3Register(
    val value: TvmContinuation,
    val code: TsaContractCode,
) : TvmRegister

data class C4Register(
    val value: TvmCellValue,
) : TvmRegister

data class C5Register(
    val value: TvmCellValue,
) : TvmRegister

data class C7Register(
    val value: TvmStackTupleValueConcreteNew,
)

// TODO make them not-null?
data class TvmRegisters(
    private val ctx: TvmContext,
    var c0: C0Register = C0Register(ctx.quit0Cont),
    var c1: C1Register = C1Register(ctx.quit1Cont),
    var c2: C2Register = C2Register(TvmExceptionContinuation),
    var c3: C3Register,
) {
    lateinit var c4: C4Register
    lateinit var c5: C5Register
    lateinit var c7: C7Register

    constructor(
        ctx: TvmContext,
        c0: C0Register,
        c1: C1Register,
        c2: C2Register,
        c3: C3Register,
        c4: C4Register,
        c5: C5Register,
        c7: C7Register,
    ) : this(ctx, c0, c1, c2, c3) {
        this.c4 = c4
        this.c5 = c5
        this.c7 = c7
    }

    fun clone(): TvmRegisters = TvmRegisters(ctx, c0, c1, c2, c3, c4, c5, c7.copy())
}
