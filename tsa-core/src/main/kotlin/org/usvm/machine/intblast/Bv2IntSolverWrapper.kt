package org.usvm.machine.intblast

import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.wrapper.bv2int.KBv2IntSolver
import io.ksmt.sort.KBoolSort
import org.usvm.machine.TvmOptions
import kotlin.time.Duration

class Bv2IntSolverWrapper<C1 : KSolverConfiguration, C2 : KSolverConfiguration>(
    private val options: TvmOptions,
    private val bv2intSolver: KBv2IntSolver<C1>,
    private val regularSolver: KSolver<C2>,
    private val exprFilter: KNonRecursiveVisitor<Boolean>,
) : KSolver<KSolverConfiguration> {
    private val assertions = mutableListOf<KExpr<KBoolSort>>()
    private val trackedAssertions = mutableListOf<KExpr<KBoolSort>>()
    private var currentScope = 0

    private var isRewriteSolver = true
    private val currentSolver: KSolver<*>
        get() = if (isRewriteSolver) bv2intSolver else regularSolver

    override fun configure(configurator: KSolverConfiguration.() -> Unit) {
        error("Forbidden call")
    }

    override fun assert(expr: KExpr<KBoolSort>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprFilter.applyVisitor(expr)) {
            reassertExprs()
        }

        currentSolver.assert(expr)

        if (isRewriteSolver) {
            assertions.add(expr)
        }
    }

    override fun assert(exprs: List<KExpr<KBoolSort>>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprs.all { exprFilter.applyVisitor(it) }) {
            reassertExprs()
        }

        currentSolver.assert(exprs)

        if (isRewriteSolver) {
            assertions.addAll(exprs)
        }
    }

    override fun assertAndTrack(expr: KExpr<KBoolSort>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprFilter.applyVisitor(expr)) {
            reassertExprs()
        }

        currentSolver.assertAndTrack(expr)

        if (isRewriteSolver) {
            trackedAssertions.add(expr)
        }
    }

    override fun assertAndTrack(exprs: List<KExpr<KBoolSort>>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprs.all { exprFilter.applyVisitor(it) }) {
            reassertExprs()
        }

        currentSolver.assertAndTrack(exprs)

        if (isRewriteSolver) {
            trackedAssertions.addAll(exprs)
        }
    }

    private fun reassertExprs() {
        currentSolver.pop()

        isRewriteSolver = false
        currentSolver.push()

        currentSolver.assert(assertions)
        currentSolver.assertAndTrack(trackedAssertions)
    }

    override fun push() {
        require(currentScope == 0)

        currentScope++
        currentSolver.push()
    }

    override fun pop(n: UInt) {
        require(currentScope == 1 && n == 1u)

        currentSolver.pop(n)

        currentScope--
        assertions.clear()
        trackedAssertions.clear()
    }

    private inline fun wrappedCheck(check: () -> KSolverStatus): KSolverStatus {
        if (!isRewriteSolver) {
            return check()
        }

        if (options.useIntBlasting) {
            val bv2intRes = check()
            if (bv2intRes != KSolverStatus.UNKNOWN) {
                return bv2intRes
            }
        }

        reassertExprs()
        return check()
    }

    override fun check(timeout: Duration): KSolverStatus {
        require(currentScope == 1)

        return wrappedCheck { currentSolver.check(timeout) }
    }

    override fun checkWithAssumptions(
        assumptions: List<KExpr<KBoolSort>>,
        timeout: Duration,
    ): KSolverStatus {
        require(currentScope == 1)

        if (isRewriteSolver && !assumptions.all { exprFilter.applyVisitor(it) }) {
            reassertExprs()
        }

        return wrappedCheck { currentSolver.checkWithAssumptions(assumptions, timeout) }
    }

    override fun model(): KModel = currentSolver.model()

    override fun unsatCore(): List<KExpr<KBoolSort>> = currentSolver.unsatCore()

    override fun reasonOfUnknown(): String = currentSolver.reasonOfUnknown()

    override fun interrupt() = currentSolver.interrupt()

    override fun close() {
        bv2intSolver.close()
        regularSolver.close()
    }
}
