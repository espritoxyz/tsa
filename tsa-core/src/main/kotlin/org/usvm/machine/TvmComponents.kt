package org.usvm.machine

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.KTheory
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriter.SignednessMode
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriterConfig
import io.ksmt.solver.wrapper.bv2int.KBv2IntSolver
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import mu.KLogging
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.USizeExprProvider
import org.usvm.machine.intblast.Bv2IntExprFilter
import org.usvm.machine.intblast.Bv2IntSolverWrapper
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class TvmComponents(
    private val options: TvmOptions,
) : UComponents<TvmType, TvmSizeSort>, AutoCloseable {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override val useSolverForForks: Boolean
        get() = true

    override fun <Context : UContext<TvmSizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<TvmSizeSort> {
        return UBv32SizeExprProvider(ctx)
    }

    val typeSystem = TvmTypeSystem()

    override fun <Context : UContext<TvmSizeSort>> mkSolver(ctx: Context): SolverWithIntBlasting {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val bvSolver = KYicesSolver(ctx).apply {
            configure {
                optimizeForTheories(setOf(KTheory.UF, KTheory.Array, KTheory.BV))
            }
        }
        val intSolver = KZ3Solver(ctx).apply {
            configure {
                optimizeForTheories(setOf(KTheory.UF, KTheory.Array, KTheory.LIA, KTheory.NIA))
            }
        }
        val solver = if (!options.turnOffIntBlasting) {
            Bv2IntSolverWrapper(
                bv2intSolver = KBv2IntSolver(
                    ctx,
                    intSolver,
                    KBv2IntRewriterConfig(signednessMode = SignednessMode.SIGNED)
                ),
                regularSolver = bvSolver,
                exprFilter = Bv2IntExprFilter(
                    ctx,
                    excludeNonConstBvand = true,
                    excludeNonConstShift = true,
                    excludeNonlinearArith = false
                ),
            )
        } else {
            bvSolver
        }

        lateinit var intBlastingIsTurnedOff: (KSolver<*>) -> Boolean

        val wrappedSolver = if (logger.isDebugEnabled) {
            intBlastingIsTurnedOff = {
                ((it as? LoggingSolver<*>)?.internalSolver as? Bv2IntSolverWrapper<*, *>)?.intBlastingTurnedOff != false
            }
            LoggingSolver(solver)
        } else {
            intBlastingIsTurnedOff = {
                (it as? Bv2IntSolverWrapper<*, *>)?.intBlastingTurnedOff != false
            }
            solver
        }

        closeableResources += solver

        val typeSolver = UTypeSolver(typeSystem)
        return SolverWithIntBlasting(
            ctx,
            wrappedSolver,
            typeSolver,
            translator,
            decoder,
            options.solverTimeout,
            intBlastingIsTurnedOff,
        )
    }

    override fun mkTypeSystem(ctx: UContext<TvmSizeSort>): UTypeSystem<TvmType> {
        return typeSystem
    }

    override fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    class LoggingSolver<T : KSolverConfiguration>(
        val internalSolver: KSolver<T>,
    ) : KSolver<T> by internalSolver {
        override fun check(timeout: Duration): KSolverStatus {
            return internalSolver.check(timeout).also { status ->
                logger.debug("Forked with status: {}", status)
            }
        }
    }

    class SolverWithIntBlasting(
        ctx: UContext<*>,
        smtSolver: KSolver<*>,
        typeSolver: UTypeSolver<TvmType>,
        translator: UExprTranslator<TvmType, *>,
        decoder: UModelDecoder<UModelBase<TvmType>>,
        timeout: Duration,
        private val intBlastingIsTurnedOff: (KSolver<*>) -> Boolean,
    ) : USolverBase<TvmType>(ctx, smtSolver, typeSolver, translator, decoder, timeout) {
        fun intBlastingIsTurnedOff(): Boolean {
            return intBlastingIsTurnedOff(smtSolver)
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
