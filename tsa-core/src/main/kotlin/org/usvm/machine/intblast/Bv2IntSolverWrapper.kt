package org.usvm.machine.intblast

import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.wrapper.bv2int.KBv2IntSolver
import io.ksmt.sort.KBoolSort
import mu.KLogging
import org.usvm.machine.TvmOptions
import kotlin.time.Duration

class Bv2IntSolverWrapper<C1 : KSolverConfiguration, C2 : KSolverConfiguration>(
    private val options: TvmOptions,
    private val bv2intSolver: KBv2IntSolver<C1>,
    private val regularSolver: KSolver<C2>,
    private val exprFilter: KNonRecursiveVisitor<Boolean>,
    private val transformer: TvmBvNonRecursiveTransformer,
) : KSolver<KSolverConfiguration> {
    private val assertions = mutableListOf<KExpr<KBoolSort>>()
    private val trackedAssertions = mutableListOf<KExpr<KBoolSort>>()
    private var currentScope = 0

    private var isRewriteSolver = false
    private var encounterdBvExpr = false
    private val currentSolver: KSolver<*>
        get() = if (isRewriteSolver) bv2intSolver else regularSolver

    override fun configure(configurator: KSolverConfiguration.() -> Unit) {
        error("Forbidden call")
    }

    override fun assert(expr: KExpr<KBoolSort>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprFilter.applyVisitor(expr)) {
            reassertExprsToBvSolver()
        }

        val bvExpr = expr.accept(transformer)
        if (!isRewriteSolver && !encounterdBvExpr && transformer.visitedHardExpression) {
            reassertExprsToIntSolver()
        }

        assertions.add(expr)

        if (isRewriteSolver) {
            currentSolver.assert(expr)
        } else {
            currentSolver.assert(bvExpr)
        }
    }

    override fun assert(exprs: List<KExpr<KBoolSort>>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprs.all { exprFilter.applyVisitor(it) }) {
            reassertExprsToBvSolver()
        }

        val bvExprs = exprs.map { transformer.apply(it) }
        if (!isRewriteSolver && !encounterdBvExpr && transformer.visitedHardExpression) {
            reassertExprsToIntSolver()
        }

        assertions.addAll(exprs)

        if (isRewriteSolver) {
            currentSolver.assert(exprs)
        } else {
            currentSolver.assert(bvExprs)
        }
    }

    override fun assertAndTrack(expr: KExpr<KBoolSort>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprFilter.applyVisitor(expr)) {
            reassertExprsToBvSolver()
        }

        val bvExpr = expr.accept(transformer)
        if (!isRewriteSolver && !encounterdBvExpr && transformer.visitedHardExpression) {
            reassertExprsToIntSolver()
        }

        trackedAssertions.add(expr)

        if (isRewriteSolver) {
            currentSolver.assertAndTrack(expr)
        } else {
            currentSolver.assertAndTrack(bvExpr)
        }
    }

    override fun assertAndTrack(exprs: List<KExpr<KBoolSort>>) {
        require(currentScope == 1)

        if (isRewriteSolver && !exprs.all { exprFilter.applyVisitor(it) }) {
            reassertExprsToBvSolver()
        }

        val bvExprs = exprs.map { transformer.apply(it) }
        if (!isRewriteSolver && !encounterdBvExpr && transformer.visitedHardExpression) {
            reassertExprsToIntSolver()
        }

        trackedAssertions.addAll(exprs)

        if (isRewriteSolver) {
            currentSolver.assertAndTrack(exprs)
        } else {
            currentSolver.assertAndTrack(bvExprs)
        }
    }

    private fun reassertExprsToBvSolver() {
        currentSolver.pop()

        logger.debug("Switched to bv solver")

        isRewriteSolver = false
        encounterdBvExpr = true
        currentSolver.push()

        val bvAssertions = assertions.map { it.accept(transformer) }
        val trackedBvAssertions = trackedAssertions.map { it.accept(transformer) }

        currentSolver.assert(bvAssertions)
        currentSolver.assertAndTrack(trackedBvAssertions)
    }

    private fun reassertExprsToIntSolver() {
        currentSolver.pop()

        logger.debug("Switched to int solver")

        isRewriteSolver = true
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

        transformer.visitedHardExpression = false
        encounterdBvExpr = false
    }

    private inline fun wrappedCheck(check: () -> KSolverStatus): KSolverStatus {
        if (!isRewriteSolver) {
            return check()
        }

        if (options.useIntBlasting) {
            val result = check()
            if (result != KSolverStatus.UNKNOWN) return result

            // Retry with different random seeds to stabilize NLA solving
            for (retrySeed in RETRY_SEEDS) {
                logger.debug("UNKNOWN result, retrying with random_seed={}", retrySeed)
                bv2intSolver.configure { setIntParameter("random_seed", retrySeed) }
                val retryResult = check()
                if (retryResult != KSolverStatus.UNKNOWN) {
                    // Reset seed back to default for subsequent checks
                    bv2intSolver.configure { setIntParameter("random_seed", DEFAULT_SEED) }
                    return retryResult
                }
            }
            // Reset seed back to default even if all retries failed
            bv2intSolver.configure { setIntParameter("random_seed", DEFAULT_SEED) }
            return KSolverStatus.UNKNOWN
        }

        reassertExprsToBvSolver()
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
            reassertExprsToBvSolver()
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

    companion object {
        private val logger = object : KLogging() {}.logger

        private const val DEFAULT_SEED = 0
        private val RETRY_SEEDS = intArrayOf(1, 42, 123, 500)
    }
}
