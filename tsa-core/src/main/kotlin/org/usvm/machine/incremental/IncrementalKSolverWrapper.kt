package org.usvm.machine.incremental

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.sort.KBoolSort
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentHashSet
import kotlin.time.Duration

private typealias KBoolExpr = KExpr<KBoolSort>

class IncrementalKSolverWrapper(
    val underlying: KSolver<out KSolverConfiguration>,
) : KSolver<KSolverConfiguration> {
    var assertedExprs = persistentHashSetOf<KBoolExpr>()

    /**
     * maps expression to the level of the lowest level where this expression was asserted.
     * The level of an expression E is the number of `underlying.push()` calls that happened before adding E.
     */
    private var exprToLevel = persistentHashMapOf<KBoolExpr, Int>()

    /**
     * Should always be equal to the number of active calls `underlying.push()`.
     */
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
        if (assertedExprsToRemove.isNotEmpty()) {
            val badLevel = assertedExprsToRemove.map { exprToLevel[it] ?: error("no level for expr $it") }.min()
            assert(pushedLevels >= badLevel)
            val diff = pushedLevels - badLevel + 1
            pushedLevels -= diff
            underlying.pop(diff.toUInt())
            exprToLevel = exprToLevel.filter { it.value < badLevel }.toPersistentHashMap()
            assertedExprs = exprToLevel.keys.toPersistentHashSet()
        }
        underlying.push()
        pushedLevels++

        val exprsToAdd = newExprs - assertedExprs
        underlying.assert(exprsToAdd.toList())
        for (expr in exprsToAdd) {
            if (!exprToLevel.containsKey(expr)) {
                exprToLevel = exprToLevel.put(expr, pushedLevels)
            }
        }
        assertedExprs = assertedExprs.addAll(exprsToAdd)
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
