package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.KTheory
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriter.SignednessMode
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriterConfig
import io.ksmt.solver.wrapper.bv2int.KBv2IntSolver
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KBoolSort
import mu.KLogging
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeExprProvider
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.intblast.Bv2IntExprFilter
import org.usvm.machine.intblast.Bv2IntSolverWrapper
import org.usvm.machine.state.TvmPathConstraints
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USolverBase
import org.usvm.solver.USolverResult
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
        val intSolver =
            KZ3Solver(ctx).apply {
                configure {
                    optimizeForTheories(setOf(KTheory.UF, KTheory.Array, KTheory.LIA, KTheory.NIA))
//                    setZ3Option("arith.nl.horner_subs_fixed", 1)
                }
            }
        val solver =
            Bv2IntSolverWrapper(
                options = options,
                bv2intSolver =
                    KBv2IntSolver(
                        ctx,
                        intSolver,
                        KBv2IntRewriterConfig(signednessMode = SignednessMode.SIGNED),
                    ),
                regularSolver = bvSolver,
                exprFilter =
                    Bv2IntExprFilter(
                        ctx,
                        excludeNonConstBvand = true,
                        excludeNonConstShift = true,
                        excludeNonlinearArith = false,
                    ),
            )

        val wrappedSolver =
            if (logger.isDebugEnabled) {
                LoggingSolver(solver)
            } else {
                solver
            }

        closeableResources += solver

        val typeSolver = UTypeSolver(typeSystem)
        return TvmSolver(ctx, wrappedSolver, typeSolver, translator, decoder, options.solverTimeout)
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
                if (status == KSolverStatus.UNKNOWN) {
                    println("here")
                }
            }

        override fun checkWithAssumptions(assumptions: List<KExpr<KBoolSort>>, timeout: Duration): KSolverStatus {
            return internalSolver.checkWithAssumptions(assumptions, timeout).also { status ->
                logger.debug("Forked with assumptions with status: {}", status)
            }
        }
    }

    class TvmSolver(
        ctx: UContext<*>,
        smtSolver: KSolver<*>,
        typeSolver: UTypeSolver<TvmType>,
        translator: UExprTranslator<TvmType, *>,
        decoder: UModelDecoder<UModelBase<TvmType>>,
        timeout: Duration,
    ) : USolverBase<TvmType>(
            ctx,
            smtSolver,
            typeSolver,
            translator,
            decoder,
            timeout,
        ) {
        override fun check(query: UPathConstraints<TvmType>): USolverResult<UModelBase<TvmType>> {
            require(query is TvmPathConstraints) {
                "Unexpected path constraints: $query"
            }
            return super.check(query)
//            val softConstraints = query.tvmSoftConstraints
//            return super.checkWithSoftConstraints(query, softConstraints)
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}

var DEBUG_FLAG = false
