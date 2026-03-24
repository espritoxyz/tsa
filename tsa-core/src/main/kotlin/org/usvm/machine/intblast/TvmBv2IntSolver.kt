package org.usvm.machine.intblast

import io.ksmt.KContext
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriter
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriterConfig
import io.ksmt.solver.wrapper.bv2int.KBv2IntSolver
import org.usvm.machine.TvmContext

class TvmBv2IntSolver<C : KSolverConfiguration>(
    ctx: KContext,
    solver: KSolver<C>,
    rewriterConfig: KBv2IntRewriterConfig,
) : KBv2IntSolver<C>(ctx, solver, rewriterConfig) {
    override fun createBv2IntRewriter(config: KBv2IntRewriterConfig): KBv2IntRewriter {
        return TvmBv2IntRewriter(ctx, bv2IntContext, splitter.dsu, config)
    }
}
