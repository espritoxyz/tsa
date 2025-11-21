package org.usvm.machine

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.KTheory
import io.ksmt.solver.yices.KYicesSolver
import mu.KLogging
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeExprProvider
import org.usvm.machine.incremental.IncrementalKSolverWrapper
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class TvmComponents(
    private val options: TvmOptions,
) : UComponents<TvmType, TvmSizeSort>,
    AutoCloseable {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override val useSolverForForks: Boolean
        get() = true

    override fun <Context : UContext<TvmSizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<TvmSizeSort> =
        UBv32SizeExprProvider(ctx)

    val typeSystem = TvmTypeSystem()

    override fun <Context : UContext<TvmSizeSort>> mkSolver(ctx: Context): USolverBase<TvmType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val bvSolver =
            KYicesSolver(ctx).apply {
                configure {
                    optimizeForTheories(setOf(KTheory.UF, KTheory.Array, KTheory.BV))
                }
            }

        val wrappedSolver =
            if (logger.isDebugEnabled) {
                LoggingSolver(bvSolver)
            } else {
                bvSolver
            }
        val incrementalKSolver = IncrementalKSolverWrapper(wrappedSolver)

        closeableResources += incrementalKSolver

        val typeSolver = UTypeSolver(typeSystem)
        return USolverBase(ctx, incrementalKSolver, typeSolver, translator, decoder, options.solverTimeout)
    }

    override fun mkTypeSystem(ctx: UContext<TvmSizeSort>): UTypeSystem<TvmType> = typeSystem

    override fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    class LoggingSolver<T : KSolverConfiguration>(
        private val internalSolver: KSolver<T>,
    ) : KSolver<T> by internalSolver {
        override fun check(timeout: Duration): KSolverStatus =
            internalSolver.check(timeout).also { status ->
                logger.debug("Forked with status: {}", status)
            }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
