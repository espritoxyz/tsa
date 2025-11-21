package org.usvm.machine.incremental

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.sort.KBoolSort
import kotlinx.collections.immutable.persistentHashSetOf
import kotlin.time.Duration

private typealias KBoolExpr = KExpr<KBoolSort>

class IncrementalKSolverWrapper(
    val underlying: KSolver<out KSolverConfiguration>,
) : KSolver<KSolverConfiguration> {
    var assertedExprs = persistentHashSetOf<KBoolExpr>()
    var pushedLevels = 0

    override fun assert(expr: KBoolExpr) {
        error("invalid call")
    }

    override fun assertAndTrack(expr: KExpr<KBoolSort>) {
        underlying.assertAndTrack(expr)
    }

    override fun check(timeout: Duration): KSolverStatus = underlying.check(timeout)

    override fun assert(exprs: List<KExpr<KBoolSort>>) {
        val newExprs = persistentHashSetOf<KBoolExpr>().addAll(exprs)
        val assertedExprsToRemove = assertedExprs - newExprs

        if (assertedExprsToRemove.isEmpty()) {
            val exprsToAdd = newExprs - assertedExprs
            underlying.push()
            underlying.assert(exprsToAdd.toList())
            pushedLevels++
        } else {
            underlying.pop(pushedLevels.toUInt())
            pushedLevels = 0
        }
        assertedExprs = newExprs
        return underlying.assert(exprs)
    }

    override fun assertAndTrack(exprs: List<KExpr<KBoolSort>>) = underlying.assertAndTrack(exprs)

    override fun checkWithAssumptions(
        assumptions: List<KExpr<KBoolSort>>,
        timeout: Duration,
    ): KSolverStatus = underlying.checkWithAssumptions(assumptions, timeout)

    override fun close() {
        underlying.close()
    }

    override fun configure(configurator: KSolverConfiguration.() -> Unit) {
        underlying.configure(configurator)
    }

    override fun interrupt() {
        underlying.interrupt()
    }

    override fun model(): KModel = underlying.model()

    override fun pop(n: UInt) = underlying.pop(n)

    override fun push() = underlying.push()

    override fun reasonOfUnknown(): String = underlying.reasonOfUnknown()

    override fun unsatCore(): List<KExpr<KBoolSort>> = underlying.unsatCore()
}
