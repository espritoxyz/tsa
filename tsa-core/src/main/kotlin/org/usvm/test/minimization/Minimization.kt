package org.usvm.test.minimization

import org.ton.bytecode.TvmInst
import org.usvm.machine.state.TvmUserDefinedFailure
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulActionPhase
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestResult
import java.util.IdentityHashMap

/**
 * Minimizes [tests] in each test suite independently. Test suite is computed with [executionToTestSuite] function.
 *
 * We have 4 different test suites:
 * * Successful suite
 * * Tlb Structural error suite
 * * User defined error suite
 * * Tvm error suite
 *
 * We want to minimize tests independently in each of these suites.
 *
 * @return flatten minimized executions in each test suite.
 */
fun minimizeTestCase(
    tests: List<TvmSymbolicTest>,
    branchInstructionsNumber: Int = 3,
): List<TvmSymbolicTest> {
    val executions = buildExecutions(tests)
    val groupedExecutionsByTestSuite = groupExecutionsByTestSuite(executions)
    val groupedExecutionsByBranchInstructions =
        groupedExecutionsByTestSuite.flatMap { execution ->
            groupByBranchInstructions(
                execution,
                branchInstructionsNumber,
            )
        }
    return groupedExecutionsByBranchInstructions.map { minimizeExecutions(it) }.flatten().map { it.test }
}

private fun minimizeExecutions(executions: List<TvmExecution>): List<TvmExecution> {
    val filteredExecutions = filterOutDuplicateCoverages(executions)
    val (mapping, executionToPriorityMapping) = buildMapping(filteredExecutions)

    val usedFilteredExecutionIndexes = GreedyEssential.minimize(mapping, executionToPriorityMapping).toSet()
    val usedFilteredExecutions = filteredExecutions.filterIndexed { idx, _ -> idx in usedFilteredExecutionIndexes }

    return usedFilteredExecutions
}

private fun filterOutDuplicateCoverages(executions: List<TvmExecution>): List<TvmExecution> {
    val (executionIdxToCoveredEdgesMap, _) = buildMapping(executions)
    return executions
        .withIndex()
        // we need to group by coveredEdges and not just Coverage to not miss exceptional edges that buildMapping() function adds
        .groupBy(
            keySelector = { indexedExecution -> executionIdxToCoveredEdgesMap[indexedExecution.index] },
            valueTransform = { indexedExecution -> indexedExecution.value },
        ).values
        .map { executionsWithEqualCoverage -> executionsWithEqualCoverage.chooseOneExecution() }
}

/**
 * Groups the [executions] by their `paths` on `first` [branchInstructionsNumber] `branch` instructions.
 *
 * An instruction is called as a `branch` instruction iff there are two or more possible next instructions after this
 * instructions. For example, if we have two test cases with these instructions list:
 * * `{1, 2, 3, 2, 4, 5, 6}`
 * * `{1, 2, 4, 5, 7}`
 *
 * Then `2` and `5` instuctions are branch instructions, because we can go either `2` -> `3` or `2` -> `5`. Similarly,
 * we can go either `5` -> `6` or `5` -> `7`.
 *
 * `First` means that we will find first [branchInstructionsNumber] `branch` instructions in the order of the appearing
 * in the instruction list of a certain execution.
 *
 * A `path` on a `branch` instruction is a concrete next instruction, which we have chosen.
 */

private fun groupByBranchInstructions(
    executions: List<TvmExecution>,
    branchInstructionsNumber: Int,
): Collection<List<TvmExecution>> {
    val instructionToPossibleNextInstructions = mutableMapOf<Int, MutableSet<Int?>>()

    for (execution in executions) {
        val coveredInstructionIds = execution.coveredInstructionIds

        for (i in coveredInstructionIds.indices) {
            instructionToPossibleNextInstructions
                .getOrPut(coveredInstructionIds[i]) { mutableSetOf() }
                .add(coveredInstructionIds.getOrNull(i + 1))
        }
    }

    val branchInstructions =
        instructionToPossibleNextInstructions
            .filterValues { it.size > 1 } // here we take only real branch instruction.
            .keys

    /**
     * here we group executions by their behaviour on the branch instructions
     * e.g., we have these executions and [branchInstructionsNumber] == 2:
     * 1. {2, 3, 2, 4, 2, 5}
     * 2. {2, 3, 2, 6}
     * 3. {2, 3, 4, 3}
     * branch instructions are {2 -> (3, 4, 5, 6), 3 -> (2, 4), 4 -> (2, 3)}
     *
     * we will build these lists representing their behaviour:
     * 1. {2 -> 3, 3 -> 2} (because of {__2__, __3__, 2, 4, 2, 5})
     * 2. {2 -> 3, 3 -> 2} (because of {__2__, __3__, 2, 6})
     * 3. {2 -> 3, 3 -> 4} (because of {__2__, __3__, 4, 3})
     */
    val groupedExecutions =
        executions.groupBy { execution ->
            val branchInstructionToBranch = mutableListOf<Pair<Int, Int>>() // we group executions by this variable
            val coveredInstructionIds = execution.coveredInstructionIds
            // collect the behaviour on the branch instructions
            for (i in 0 until coveredInstructionIds.size - 1) {
                if (coveredInstructionIds[i] in branchInstructions) {
                    branchInstructionToBranch.add(coveredInstructionIds[i] to coveredInstructionIds[i + 1])
                }
                if (branchInstructionToBranch.size == branchInstructionsNumber) {
                    break
                }
            }
            branchInstructionToBranch
        }

    return groupedExecutions.values
}

private fun groupExecutionsByTestSuite(executions: List<TvmExecution>): Collection<List<TvmExecution>> =
    executions.groupBy { executionToTestSuite(it) }.values

private fun executionToTestSuite(execution: TvmExecution): Int =
    when (val result = execution.test.result) {
        is TvmSuccessfulExecution -> 0
        is TvmExecutionWithStructuralError -> 1
        is TvmExecutionWithSoftFailure -> 2
        is TvmTestFailure -> if (result.failure.exit is TvmUserDefinedFailure) 3 else 4
        is TvmSuccessfulActionPhase -> error("Unexpected result: $execution")
    }

/**
 * Builds a mapping from execution id to edges id and from execution id to its priority.
 */
private fun buildMapping(executions: List<TvmExecution>): Pair<Map<Int, List<Int>>, Map<Int, Int>> {
    // (inst1, instr2) -> edge id --- edge represents as a pair of instructions, which are connected by this edge
    val allCoveredEdges = mutableMapOf<Pair<Int, Int?>, Int>()
    val thrownExceptions = mutableMapOf<String, Int>()
    val mapping = mutableMapOf<Int, List<Int>>()
    val executionToPriorityMapping = mutableMapOf<Int, Int>()

    executions.forEachIndexed { idx, execution ->
        val instructionsWithoutExtra = execution.coveredInstructionIds
        addExtraIfLastInstructionIsException( // here we add one more instruction to represent an exception.
            instructionsWithoutExtra,
            execution.test.result,
            thrownExceptions,
        ).let { instructions ->
            val edges =
                instructions.indices.map { i ->
                    allCoveredEdges.getOrPut(instructions[i] to instructions.getOrNull(i + 1)) { allCoveredEdges.size }
                }

            mapping[idx] = edges
            executionToPriorityMapping[idx] = getExecutionPriority()
        }
    }

    return Pair(mapping, executionToPriorityMapping)
}

// TODO use some heuristic
private fun List<TvmExecution>.chooseOneExecution(): TvmExecution = first()

/**
 * Extends the [instructionsWithoutExtra] with one extra instruction if the [result] is
 * [TvmTestFailure] or [TvmExecutionWithStructuralError].
 *
 * Also adds this exception to the [thrownExceptions] if it is not already there.
 *
 * @return the extended list of instructions or
 * initial list if [result] is not [TvmTestFailure] or [TvmExecutionWithStructuralError].
 */
private fun addExtraIfLastInstructionIsException(
    instructionsWithoutExtra: List<Int>,
    result: TvmTestResult,
    thrownExceptions: MutableMap<String, Int>,
): List<Int> =
    if (result is TvmTestFailure || result is TvmExecutionWithStructuralError) {
        val exceptionInfo = failedResultToInfo(result, instructionsWithoutExtra.last())
        thrownExceptions.putIfAbsent(exceptionInfo, (-thrownExceptions.size - 1))
        val exceptionId = thrownExceptions.getValue(exceptionInfo)
        instructionsWithoutExtra.toMutableList().apply { add(exceptionId) }
    } else {
        instructionsWithoutExtra
    }

private fun getExecutionPriority(): Int = 0

private fun failedResultToInfo(
    result: TvmTestResult,
    lastInst: Int,
): String =
    when (result) {
        is TvmTestFailure -> "TvmError-${result.failure.exit.ruleName}-${result.failure.type}-$lastInst"
        is TvmExecutionWithStructuralError -> "StructuralError-${result.exit.ruleId}-$lastInst"
        is TvmExecutionWithSoftFailure -> "TvmSoftError-${result.failure.exit.ruleId}-$lastInst"
        is TvmSuccessfulExecution -> error("Failed execution result is expected")
        is TvmSuccessfulActionPhase -> error("Unexpected result: $result")
    }

private fun buildExecutions(tests: List<TvmSymbolicTest>): List<TvmExecution> {
    val instToId = IdentityHashMap<TvmInst, Int>()

    return tests.map { test ->
        val coveredInstructionsIds =
            test.coveredInstructions.map { inst ->
                instToId.getOrPut(inst) { instToId.size }
            }

        TvmExecution(test, coveredInstructionsIds)
    }
}

private data class TvmExecution(
    val test: TvmSymbolicTest,
    val coveredInstructionIds: List<Int>,
)
