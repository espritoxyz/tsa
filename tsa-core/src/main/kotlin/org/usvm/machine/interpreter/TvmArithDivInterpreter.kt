package org.usvm.machine.interpreter

import io.ksmt.sort.KBvSort
import org.ton.bytecode.TvmArithmDivAdddivmodInst
import org.ton.bytecode.TvmArithmDivAdddivmodcInst
import org.ton.bytecode.TvmArithmDivAdddivmodrInst
import org.ton.bytecode.TvmArithmDivAddrshiftcmodInst
import org.ton.bytecode.TvmArithmDivAddrshiftmodInst
import org.ton.bytecode.TvmArithmDivAddrshiftmodcInst
import org.ton.bytecode.TvmArithmDivAddrshiftmodrInst
import org.ton.bytecode.TvmArithmDivAddrshiftrmodInst
import org.ton.bytecode.TvmArithmDivDivInst
import org.ton.bytecode.TvmArithmDivDivcInst
import org.ton.bytecode.TvmArithmDivDivmodInst
import org.ton.bytecode.TvmArithmDivDivmodcInst
import org.ton.bytecode.TvmArithmDivDivmodrInst
import org.ton.bytecode.TvmArithmDivDivrInst
import org.ton.bytecode.TvmArithmDivInst
import org.ton.bytecode.TvmArithmDivLshiftadddivmodInst
import org.ton.bytecode.TvmArithmDivLshiftadddivmodVarInst
import org.ton.bytecode.TvmArithmDivLshiftadddivmodcInst
import org.ton.bytecode.TvmArithmDivLshiftadddivmodcVarInst
import org.ton.bytecode.TvmArithmDivLshiftadddivmodrInst
import org.ton.bytecode.TvmArithmDivLshiftadddivmodrVarInst
import org.ton.bytecode.TvmArithmDivLshiftdivInst
import org.ton.bytecode.TvmArithmDivLshiftdivVarInst
import org.ton.bytecode.TvmArithmDivLshiftdivcInst
import org.ton.bytecode.TvmArithmDivLshiftdivcVarInst
import org.ton.bytecode.TvmArithmDivLshiftdivmodInst
import org.ton.bytecode.TvmArithmDivLshiftdivmodVarInst
import org.ton.bytecode.TvmArithmDivLshiftdivmodcInst
import org.ton.bytecode.TvmArithmDivLshiftdivmodcVarInst
import org.ton.bytecode.TvmArithmDivLshiftdivmodrInst
import org.ton.bytecode.TvmArithmDivLshiftdivmodrVarInst
import org.ton.bytecode.TvmArithmDivLshiftdivrInst
import org.ton.bytecode.TvmArithmDivLshiftdivrVarInst
import org.ton.bytecode.TvmArithmDivLshiftmodInst
import org.ton.bytecode.TvmArithmDivLshiftmodVarInst
import org.ton.bytecode.TvmArithmDivLshiftmodcInst
import org.ton.bytecode.TvmArithmDivLshiftmodcVarInst
import org.ton.bytecode.TvmArithmDivLshiftmodrInst
import org.ton.bytecode.TvmArithmDivLshiftmodrVarInst
import org.ton.bytecode.TvmArithmDivModInst
import org.ton.bytecode.TvmArithmDivModcInst
import org.ton.bytecode.TvmArithmDivModpow2Inst
import org.ton.bytecode.TvmArithmDivModpow2VarInst
import org.ton.bytecode.TvmArithmDivModpow2cInst
import org.ton.bytecode.TvmArithmDivModpow2cVarInst
import org.ton.bytecode.TvmArithmDivModpow2rInst
import org.ton.bytecode.TvmArithmDivModpow2rVarInst
import org.ton.bytecode.TvmArithmDivModrInst
import org.ton.bytecode.TvmArithmDivMuladddivmodInst
import org.ton.bytecode.TvmArithmDivMuladddivmodcInst
import org.ton.bytecode.TvmArithmDivMuladddivmodrInst
import org.ton.bytecode.TvmArithmDivMuladdrshiftcmodInst
import org.ton.bytecode.TvmArithmDivMuladdrshiftmodInst
import org.ton.bytecode.TvmArithmDivMuladdrshiftrmodInst
import org.ton.bytecode.TvmArithmDivMuldivInst
import org.ton.bytecode.TvmArithmDivMuldivcInst
import org.ton.bytecode.TvmArithmDivMuldivmodInst
import org.ton.bytecode.TvmArithmDivMuldivmodcInst
import org.ton.bytecode.TvmArithmDivMuldivmodrInst
import org.ton.bytecode.TvmArithmDivMuldivrInst
import org.ton.bytecode.TvmArithmDivMulmodInst
import org.ton.bytecode.TvmArithmDivMulmodcInst
import org.ton.bytecode.TvmArithmDivMulmodpow2Inst
import org.ton.bytecode.TvmArithmDivMulmodpow2VarInst
import org.ton.bytecode.TvmArithmDivMulmodpow2cInst
import org.ton.bytecode.TvmArithmDivMulmodpow2cVarInst
import org.ton.bytecode.TvmArithmDivMulmodpow2rInst
import org.ton.bytecode.TvmArithmDivMulmodpow2rVarInst
import org.ton.bytecode.TvmArithmDivMulmodrInst
import org.ton.bytecode.TvmArithmDivMulrshiftInst
import org.ton.bytecode.TvmArithmDivMulrshiftVarInst
import org.ton.bytecode.TvmArithmDivMulrshiftcInst
import org.ton.bytecode.TvmArithmDivMulrshiftcVarInst
import org.ton.bytecode.TvmArithmDivMulrshiftcmodInst
import org.ton.bytecode.TvmArithmDivMulrshiftcmodVarInst
import org.ton.bytecode.TvmArithmDivMulrshiftmodInst
import org.ton.bytecode.TvmArithmDivMulrshiftmodVarInst
import org.ton.bytecode.TvmArithmDivMulrshiftrInst
import org.ton.bytecode.TvmArithmDivMulrshiftrVarInst
import org.ton.bytecode.TvmArithmDivMulrshiftrmodInst
import org.ton.bytecode.TvmArithmDivMulrshiftrmodVarInst
import org.ton.bytecode.TvmArithmDivRshiftcInst
import org.ton.bytecode.TvmArithmDivRshiftcVarInst
import org.ton.bytecode.TvmArithmDivRshiftcmodInst
import org.ton.bytecode.TvmArithmDivRshiftmodInst
import org.ton.bytecode.TvmArithmDivRshiftmodVarInst
import org.ton.bytecode.TvmArithmDivRshiftmodcVarInst
import org.ton.bytecode.TvmArithmDivRshiftmodrVarInst
import org.ton.bytecode.TvmArithmDivRshiftrInst
import org.ton.bytecode.TvmArithmDivRshiftrVarInst
import org.ton.bytecode.TvmArithmDivRshiftrmodInst
import org.ton.bytecode.TvmIntegerType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.INT_BITS
import org.usvm.machine.state.TvmIntegerOutOfRange
import org.usvm.machine.state.TvmIntegerOverflow
import org.usvm.machine.state.bvMaxValueSignedExtended
import org.usvm.machine.state.bvMinValueSignedExtended
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.setFailure
import org.usvm.machine.state.takeLastInt

class TvmArithDivInterpreter(private val ctx: TvmContext) {

    fun visitArithmeticDivInst(scope: TvmStepScope, stmt: TvmArithmDivInst) {
        with(ctx) {
            when (stmt) {
                is TvmArithmDivDivInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    val (div, noOverflow) = makeDiv(x, y)
                    checkOverflow(noOverflow, scope) ?: return
                    scope.calcOnState {
                        stack.add(div, TvmIntegerType)
                    }
                }
                is TvmArithmDivModInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    scope.calcOnState {
                        stack.add(makeMod(x, y), TvmIntegerType)
                    }
                }
                is TvmArithmDivDivcInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    val (divc, noOverflow) = makeDivc(x, y)
                    checkOverflow(noOverflow, scope) ?: return
                    scope.calcOnState {
                        stack.add(divc, TvmIntegerType)
                    }
                }
                is TvmArithmDivDivrInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    val (divr, noOverflow) = makeDivr(x, y)
                    checkOverflow(noOverflow, scope) ?: return
                    scope.calcOnState {
                        stack.add(divr, TvmIntegerType)
                    }
                }
                is TvmArithmDivDivmodInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    val (div, mod) = makeDivMod(x, y)
                    checkOverflow(div.noOverflow, scope) ?: return
                    scope.calcOnState {
                        stack.add(div.value, TvmIntegerType)
                        stack.add(mod, TvmIntegerType)
                    }
                }
                is TvmArithmDivDivmodcInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    val (divc, modc) = makeDivModc(x, y)
                    checkOverflow(divc.noOverflow, scope) ?: return
                    scope.calcOnState {
                        stack.add(divc.value, TvmIntegerType)
                        stack.add(modc, TvmIntegerType)
                    }
                }
                is TvmArithmDivDivmodrInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    val (divr, modr) = makeDivModr(x, y)
                    checkOverflow(divr.noOverflow, scope) ?: return
                    scope.calcOnState {
                        stack.add(divr.value, TvmIntegerType)
                        stack.add(modr, TvmIntegerType)
                    }
                }
                is TvmArithmDivModcInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    scope.calcOnState {
                        stack.add(makeModc(x, y), TvmIntegerType)
                    }
                }
                is TvmArithmDivModrInst -> {
                    val (x, y) = takeOperandsAndCheckForZero(scope) ?: return
                    scope.calcOnState {
                        stack.add(makeModr(x, y), TvmIntegerType)
                    }
                }
                is TvmArithmDivAdddivmodInst -> {
                    doAdddivmodX(scope) { x, y -> makeDivMod(x, y) } ?: return
                }
                is TvmArithmDivAdddivmodcInst -> {
                    doAdddivmodX(scope) { x, y -> makeDivModc(x, y) } ?: return
                }
                is TvmArithmDivAdddivmodrInst -> {
                    doAdddivmodX(scope) { x, y -> makeDivModr(x, y) } ?: return
                }
                is TvmArithmDivModpow2Inst -> scope.doWithState {
                    doModpow2XNoVar(scope, stmt.t) { x, y -> makeMod(x, y) }
                }
                is TvmArithmDivModpow2cInst -> scope.doWithState {
                    doModpow2XNoVar(scope, stmt.t) { x, y -> makeModc(x, y) }
                }
                is TvmArithmDivModpow2rInst -> scope.doWithState {
                    doModpow2XNoVar(scope, stmt.t) { x, y -> makeModr(x, y) }
                }
                is TvmArithmDivModpow2VarInst -> {
                    doModpow2XVar(scope) { x, y -> makeMod(x, y) } ?: return
                }
                is TvmArithmDivModpow2cVarInst -> {
                    doModpow2XVar(scope) { x, y -> makeModc(x, y) } ?: return
                }
                is TvmArithmDivModpow2rVarInst -> {
                    doModpow2XVar(scope) { x, y -> makeModr(x, y) } ?: return
                }
                is TvmArithmDivRshiftcInst -> {
                    doRshiftXNoVar(stmt.t, scope) { x, y -> listOf(makeDivc(x, y).value) }
                }
                is TvmArithmDivRshiftrInst -> {
                    doRshiftXNoVar(stmt.t, scope) { x, y -> listOf(makeDivr(x, y).value) }
                }
                is TvmArithmDivRshiftcVarInst -> {
                    doRshiftXVar(scope) { x, y -> listOf(makeDivc(x, y).value) } ?: return
                }
                is TvmArithmDivRshiftrVarInst -> {
                    doRshiftXVar(scope) { x, y -> listOf(makeDivr(x, y).value) } ?: return
                }
                is TvmArithmDivRshiftmodInst -> {
                    doRshiftXNoVar(stmt.t, scope) { x, y ->
                        val (div, mod) = makeDivMod(x, y)
                        listOf(div.value, mod)
                    }
                }
                is TvmArithmDivRshiftcmodInst -> {
                    doRshiftXNoVar(stmt.t, scope) { x, y ->
                        val (div, mod) = makeDivModc(x, y)
                        listOf(div.value, mod)
                    }
                }
                is TvmArithmDivRshiftrmodInst -> {
                    doRshiftXNoVar(stmt.t, scope) { x, y ->
                        val (div, mod) = makeDivModr(x, y)
                        listOf(div.value, mod)
                    }
                }
                is TvmArithmDivRshiftmodVarInst -> {
                    doRshiftXVar(scope) { x, y ->
                        val (div, mod) = makeDivMod(x, y)
                        listOf(div.value, mod)
                    } ?: return
                }
                is TvmArithmDivRshiftmodcVarInst -> {
                    doRshiftXVar(scope) { x, y ->
                        val (div, mod) = makeDivModc(x, y)
                        listOf(div.value, mod)
                    } ?: return
                }
                is TvmArithmDivRshiftmodrVarInst -> {
                    doRshiftXVar(scope) { x, y ->
                        val (div, mod) = makeDivModr(x, y)
                        listOf(div.value, mod)
                    } ?: return
                }

                // << //
                is TvmArithmDivLshiftdivInst -> TODO()
                is TvmArithmDivLshiftdivcInst -> TODO()
                is TvmArithmDivLshiftdivrInst -> TODO()

                // << //
                is TvmArithmDivLshiftdivVarInst -> TODO()
                is TvmArithmDivLshiftdivcVarInst -> TODO()
                is TvmArithmDivLshiftdivrVarInst -> TODO()

                // << /%
                is TvmArithmDivLshiftdivmodInst -> TODO()
                is TvmArithmDivLshiftdivmodcInst -> TODO()
                is TvmArithmDivLshiftdivmodrInst -> TODO()

                // << /%
                is TvmArithmDivLshiftdivmodVarInst -> TODO()
                is TvmArithmDivLshiftdivmodcVarInst -> TODO()
                is TvmArithmDivLshiftdivmodrVarInst -> TODO()

                // << %
                is TvmArithmDivLshiftmodInst -> TODO()
                is TvmArithmDivLshiftmodcInst -> TODO()
                is TvmArithmDivLshiftmodrInst -> TODO()

                // << %
                is TvmArithmDivLshiftmodVarInst -> TODO()
                is TvmArithmDivLshiftmodcVarInst -> TODO()
                is TvmArithmDivLshiftmodrVarInst -> TODO()

                // + >> %
                is TvmArithmDivAddrshiftcmodInst -> TODO()
                is TvmArithmDivAddrshiftrmodInst -> TODO()

                // + >> %
                is TvmArithmDivAddrshiftmodInst -> TODO()
                is TvmArithmDivAddrshiftmodcInst -> TODO()
                is TvmArithmDivAddrshiftmodrInst -> TODO()

                // << + /%
                is TvmArithmDivLshiftadddivmodInst -> TODO()
                is TvmArithmDivLshiftadddivmodcInst -> TODO()
                is TvmArithmDivLshiftadddivmodrInst -> TODO()

                // << + /%
                is TvmArithmDivLshiftadddivmodVarInst -> TODO()
                is TvmArithmDivLshiftadddivmodcVarInst -> TODO()
                is TvmArithmDivLshiftadddivmodrVarInst -> TODO()

                // * //
                is TvmArithmDivMuldivInst -> TODO()
                is TvmArithmDivMuldivcInst -> TODO()
                is TvmArithmDivMuldivrInst -> TODO()

                // * /%
                is TvmArithmDivMuldivmodInst -> TODO()
                is TvmArithmDivMuldivmodcInst -> TODO()
                is TvmArithmDivMuldivmodrInst -> TODO()

                // * %
                is TvmArithmDivMulmodInst -> TODO()
                is TvmArithmDivMulmodcInst -> TODO()
                is TvmArithmDivMulmodrInst -> TODO()

                // * >>
                is TvmArithmDivMulrshiftInst -> TODO()
                is TvmArithmDivMulrshiftcInst -> TODO()
                is TvmArithmDivMulrshiftrInst -> TODO()

                // * //
                is TvmArithmDivMulrshiftVarInst -> TODO()
                is TvmArithmDivMulrshiftcVarInst -> TODO()
                is TvmArithmDivMulrshiftrVarInst -> TODO()

                // * + /%
                is TvmArithmDivMuladddivmodInst -> TODO()
                is TvmArithmDivMuladddivmodcInst -> TODO()
                is TvmArithmDivMuladddivmodrInst -> TODO()

                // * + >> %
                is TvmArithmDivMuladdrshiftmodInst -> TODO()
                is TvmArithmDivMuladdrshiftcmodInst -> TODO()
                is TvmArithmDivMuladdrshiftrmodInst -> TODO()

                // * % **2
                is TvmArithmDivMulmodpow2Inst -> TODO()
                is TvmArithmDivMulmodpow2cInst -> TODO()
                is TvmArithmDivMulmodpow2rInst -> TODO()

                // * % **2
                is TvmArithmDivMulmodpow2VarInst -> TODO()
                is TvmArithmDivMulmodpow2cVarInst -> TODO()
                is TvmArithmDivMulmodpow2rVarInst -> TODO()

                // * >> %
                is TvmArithmDivMulrshiftmodInst -> TODO()
                is TvmArithmDivMulrshiftcmodInst -> TODO()
                is TvmArithmDivMulrshiftrmodInst -> TODO()

                // * >> %
                is TvmArithmDivMulrshiftmodVarInst -> TODO()
                is TvmArithmDivMulrshiftcmodVarInst -> TODO()
                is TvmArithmDivMulrshiftrmodVarInst -> TODO()
            }

            scope.doWithState {
                newStmt(stmt.nextStmt())
            }
        }
    }

    private fun requireOperandBitSizesAreTheSame(x: UExpr<KBvSort>, y: UExpr<KBvSort>): UInt {
        require(x.sort.sizeBits == y.sort.sizeBits) {
            "Operands $x and $y are incompatible (different sizeBits)"
        }
        return x.sort.sizeBits
    }

    private data class DivResult(
        val value: UExpr<KBvSort>,
        val noOverflow: UBoolExpr
    )

    /**
     * Takes 257-bit constant as extends it to bitSize-bit constant
     */
    private fun TvmContext.getConst(value: UExpr<KBvSort>, bitSize: UInt): UExpr<KBvSort> {
        require(bitSize >= INT_BITS)
        return if (bitSize > INT_BITS) {
            mkBvSignExtensionExpr(bitSize.toInt() - INT_BITS.toInt(), value)
        } else {
            value
        }
    }

    private fun TvmContext.makeDivMod(x: UExpr<KBvSort>, y: UExpr<KBvSort>): Pair<DivResult, UExpr<KBvSort>> {
        requireOperandBitSizesAreTheSame(x, y)
        return makeDiv(x, y) to makeMod(x, y)
    }

    // shorter version, but unfortunately overflows in a test: mkBvSignedDivExpr(mkBvSubExpr(x, makeMod(x, y)), y)
    private fun TvmContext.makeDiv(x: UExpr<KBvSort>, y: UExpr<KBvSort>): DivResult {
        val bits = requireOperandBitSizesAreTheSame(x, y)
        val zero = getConst(zeroValue, bits)
        val minusOne = getConst(minusOneValue, bits)

        val isNegative = mkBvSignedLessExpr(x, zero) xor mkBvSignedLessExpr(y, zero)
        val computedDiv = mkBvSignedDivExpr(x, y)
        val computedMod = mkBvSignedModExpr(x, y)
        val needToCorrect = isNegative and (computedMod neq zero)
        val noOverflow = mkBvDivNoOverflowExpr(x, y)  // only one case: MIN_VALUE / MINUS_ONE

        val result = mkIte(
            needToCorrect,
            trueBranch = { mkBvAddExpr(computedDiv, minusOne) },
            falseBranch = { computedDiv }
        )
        return DivResult(result, noOverflow)
    }

    // invariant: makeMod(x, y) == mkBvSubExpr(x, mkBvMulExpr(makeDiv(x, y), y))
    private fun TvmContext.makeMod(x: UExpr<KBvSort>, y: UExpr<KBvSort>): UExpr<KBvSort> {
        requireOperandBitSizesAreTheSame(x, y)
        return mkBvSignedModExpr(x, y)
    }

    private fun TvmContext.makeDivModc(x: UExpr<KBvSort>, y: UExpr<KBvSort>): Pair<DivResult, UExpr<KBvSort>> {
        requireOperandBitSizesAreTheSame(x, y)
        val divc = makeDivc(x, y)
        val modc = mkBvSubExpr(x, mkBvMulExpr(y, divc.value))
        return divc to modc
    }

    private fun TvmContext.makeDivc(x: UExpr<KBvSort>, y: UExpr<KBvSort>): DivResult {
        val bits = requireOperandBitSizesAreTheSame(x, y)
        val zero = getConst(zeroValue, bits)
        val one = getConst(oneValue, bits)

        val isPositive = mkBvSignedLessExpr(x, zero) eq mkBvSignedLessExpr(y, zero)
        val computedDiv = mkBvSignedDivExpr(x, y)
        val computedMod = mkBvSignedModExpr(x, y)
        val needToCorrect = isPositive and (computedMod neq zero)

        val value = mkIte(
            needToCorrect,
            trueBranch = { mkBvAddExpr(computedDiv, one) },
            falseBranch = { computedDiv }
        )
        return DivResult(value, mkBvDivNoOverflowExpr(x, y))
    }

    private fun TvmContext.makeModc(x: UExpr<KBvSort>, y: UExpr<KBvSort>): UExpr<KBvSort> {
        requireOperandBitSizesAreTheSame(x, y)
        return makeDivModc(x, y).second
    }

    private fun TvmContext.makeDivModr(x: UExpr<KBvSort>, y: UExpr<KBvSort>): Pair<DivResult, UExpr<KBvSort>> {
        requireOperandBitSizesAreTheSame(x, y)
        val divr = makeDivr(x, y)
        val modr = mkBvSubExpr(x, mkBvMulExpr(y, divr.value))
        return divr to modr
    }

    private fun TvmContext.makeDivr(x: UExpr<KBvSort>, y: UExpr<KBvSort>): DivResult {
        val bits = requireOperandBitSizesAreTheSame(x, y)
        val zero = getConst(zeroValue, bits)
        val two = getConst(twoValue, bits)

        val isYPositive = mkBvSignedGreaterExpr(y, zero)
        val absY = mkIte(
            isYPositive,
            trueBranch = { y },
            falseBranch = { mkBvNegationExpr(y) }
        )
        val computedMod = makeMod(x, absY)
        val halfMod = makeDivc(absY, two).value
        val chooseFloor = isYPositive xor mkBvSignedGreaterOrEqualExpr(computedMod, halfMod)

        val value = mkIte(
            chooseFloor,
            trueBranch = { makeDiv(x, y).value },  // floor
            falseBranch = { makeDivc(x, y).value }  // ceil
        )
        return DivResult(value, mkBvDivNoOverflowExpr(x, y))
    }

    private fun TvmContext.makeModr(x: UExpr<KBvSort>, y: UExpr<KBvSort>): UExpr<KBvSort> {
        requireOperandBitSizesAreTheSame(x, y)
        return makeDivModr(x, y).second
    }

    private fun checkDivisionByZero(expr: UExpr<KBvSort>, scope: TvmStepScope) = with(ctx) {
        val neqZero = mkEq(expr, zeroValue).not()
        scope.fork(
            neqZero,
            blockOnFalseState = setFailure(TvmIntegerOverflow)
        )
    }

    private val min257BitValue = with(ctx) { bvMinValueSignedExtended(intBitsValue) }
    private val max257BitValue = with(ctx) { bvMaxValueSignedExtended(intBitsValue) }

    /**
     * Checks whether 258-bit signed integer fits in range -2^256..(2^256 - 1).
     * If not, sets TvmIntegerOverflow.
     */
    private fun checkInBounds(expr: UExpr<KBvSort>, scope: TvmStepScope) = with(ctx) {
        require(expr.sort.sizeBits == 258u)
        val minValue = mkBvSignExtensionExpr(1, min257BitValue)
        val maxValue = mkBvSignExtensionExpr(1, max257BitValue)
        val inBounds = mkBvSignedLessOrEqualExpr(minValue, expr) and mkBvSignedLessOrEqualExpr(expr, maxValue)
        scope.fork(
            inBounds,
            blockOnFalseState = setFailure(TvmIntegerOverflow)
        )
    }

    private fun checkOverflow(noOverflowExpr: UBoolExpr, scope: TvmStepScope): Unit? = scope.fork(
        noOverflowExpr,
        blockOnFalseState = setFailure(TvmIntegerOverflow)
    )

    @Suppress("SameParameterValue")
    private fun checkInRange(expr: UExpr<KBvSort>, scope: TvmStepScope, min: Int, max: Int) = with(ctx) {
        val cond = mkBvSignedLessOrEqualExpr(min.toBv257(), expr) and mkBvSignedLessOrEqualExpr(expr, max.toBv257())
        scope.fork(
            cond,
            blockOnFalseState = setFailure(TvmIntegerOutOfRange)
        )
    }

    private fun takeOperandsAndCheckForZero(scope: TvmStepScope): Pair<UExpr<KBvSort>, UExpr<KBvSort>>? {
        val (secondOperand, firstOperand) = scope.calcOnState {
            stack.takeLastInt() to stack.takeLastInt()
        }
        checkDivisionByZero(secondOperand, scope) ?: return null
        return firstOperand to secondOperand
    }

    private fun doAdddivmodX(
        scope: TvmStepScope,
        makeDivmodX: (UExpr<KBvSort>, UExpr<KBvSort>) -> Pair<DivResult, UExpr<KBvSort>>
    ): Unit? = with(ctx) {
        val z = scope.calcOnState { stack.takeLastInt() }
        val w = scope.calcOnState { stack.takeLastInt() }
        val x = scope.calcOnState { stack.takeLastInt() }
        checkDivisionByZero(z, scope) ?: return null
        val xExtended = mkBvSignExtensionExpr(1, x)
        val wExtended = mkBvSignExtensionExpr(1, w)
        val zExtended = mkBvSignExtensionExpr(1, z)
        val addExtended = mkBvAddExpr(xExtended, wExtended)
        val (divExtended, modExtended) = makeDivmodX(addExtended, zExtended)
        checkOverflow(divExtended.noOverflow, scope) ?: return null
        checkInBounds(divExtended.value, scope) ?: return null
        val div = mkBvExtractExpr(high = 256, low = 0, divExtended.value)
        val mod = mkBvExtractExpr(high = 256, low = 0, modExtended)
        scope.calcOnState {
            stack.add(div, TvmIntegerType)
            stack.add(mod, TvmIntegerType)
        }
    }

    private fun doRshiftXNoVar(
        stmtT: Int,
        scope: TvmStepScope,
        makeModOrDivX: (UExpr<KBvSort>, UExpr<KBvSort>) -> List<UExpr<KBvSort>>
    ): Unit = with(ctx) {
        val t = (stmtT + 1).toBv257()
        val x = scope.calcOnState { stack.takeLastInt() }
        doRshiftX(x, t, scope, makeModOrDivX)
    }

    private fun doRshiftXVar(
        scope: TvmStepScope,
        makeModOrDivX: (UExpr<KBvSort>, UExpr<KBvSort>) -> List<UExpr<KBvSort>>
    ): Unit? = with(ctx) {
        val t = scope.calcOnState { stack.takeLastInt() }
        checkInRange(t, scope, min = 0, max = 256) ?: return null
        val x = scope.calcOnState { stack.takeLastInt() }
        doRshiftX(x, t, scope, makeModOrDivX)
    }

    private fun doRshiftX(
        x: UExpr<KBvSort>,
        t: UExpr<KBvSort>,
        scope: TvmStepScope,
        makeModOrDivX: (UExpr<KBvSort>, UExpr<KBvSort>) -> List<UExpr<KBvSort>>
    ): Unit = with(ctx) {
        val xExtended = mkBvSignExtensionExpr(1, x)
        val y = mkBvShiftLeftExpr(oneValue, t)
        val yExtended = mkBvZeroExtensionExpr(1, y)
        // overflow cannot happen (y > 0)
        // checkInBounds is not needed: overflow cannot happen (y > 0, x is in bounds)
        val resultExtended = makeModOrDivX(xExtended, yExtended)
        resultExtended.forEach {
            val result = mkBvExtractExpr(high = 256, low = 0, it)
            scope.doWithState {
                stack.add(result, TvmIntegerType)
            }
        }
    }

    private fun doModpow2XNoVar(
        scope: TvmStepScope,
        stmtT: Int,
        makeModX: (UExpr<KBvSort>, UExpr<KBvSort>) -> UExpr<KBvSort>
    ) = with(ctx) {
        val x = scope.calcOnState { stack.takeLastInt() }
        doModpow2X(scope, x, (stmtT + 1).toBv257(), makeModX)
    }

    private fun doModpow2XVar(
        scope: TvmStepScope,
        makeModX: (UExpr<KBvSort>, UExpr<KBvSort>) -> UExpr<KBvSort>
    ): Unit? = with(ctx) {
        val t = scope.calcOnState { stack.takeLastInt() }
        checkInRange(t, scope, min = 0, max = 256) ?: return null
        val x = scope.calcOnState { stack.takeLastInt() }
        doModpow2X(scope, x, t, makeModX)
    }

    private fun doModpow2X(
        scope: TvmStepScope,
        x: UExpr<KBvSort>,
        t: UExpr<KBvSort>,
        makeModX: (UExpr<KBvSort>, UExpr<KBvSort>) -> UExpr<KBvSort>
    ) = with(ctx) {
        val div = mkBvShiftLeftExpr(oneValue, t)
        val xExtended = mkBvSignExtensionExpr(1, x)
        val divExtended = mkBvZeroExtensionExpr(1, div)
        val resultExtended = makeModX(xExtended, divExtended)
        // no need for checkInBounds: overflow cannot happen
        val result = mkBvExtractExpr(high = 256, low = 0, resultExtended)
        scope.calcOnState { stack.add(result, TvmIntegerType) }
    }
}