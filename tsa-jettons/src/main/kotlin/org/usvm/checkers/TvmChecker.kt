package org.usvm.checkers

import org.ton.bytecode.TsaContractCode
import org.usvm.test.resolver.TvmSymbolicTest

interface TvmChecker {
    fun findConflictingExecutions(
        contractUnderTest: TsaContractCode,
        stopWhenFoundOneConflictingExecution: Boolean = false
    ): List<TvmSymbolicTest>
}
