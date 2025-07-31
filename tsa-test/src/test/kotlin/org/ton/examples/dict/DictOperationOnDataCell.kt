package org.ton.examples.dict

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.ton.boc.BagOfCells
import org.ton.bytecode.MethodId
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.ton.runHardTestsRegex
import org.ton.runHardTestsVar
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.TvmDictOperationOnDataCell
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class DictOperationOnDataCell {
    private val dictInC4Path: String = "/contracts/EQB7Orui1z_dKONoHuglvi2bMUpmD4fw0Z4C2gewD2FP0BpL.boc"
    private val dictInC4DataPath: String = "/contracts/EQB7Orui1z_dKONoHuglvi2bMUpmD4fw0Z4C2gewD2FP0BpL_data.boc"
    private val badOpPath = "/dict/dict_op_on_data_cell.fc"

    @Test
    fun testDictOperationOnDataCell() {
        val resourcePath = getResourcePath<DictExampleTest>(badOpPath)

        val symbolicResult = funcCompileAndAnalyzeAllMethods(
            resourcePath,
            tvmOptions = TvmOptions(useRecvInternalInput = false)
        )
        val tests = symbolicResult.single()

        checkInvariants(
            tests,
            listOf {
                test -> test.result !is TvmSuccessfulExecution
            }
        )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmExecutionWithSoftFailure)?.failure?.exit is TvmDictOperationOnDataCell }
        )
    }

    @EnabledIfEnvironmentVariable(named = runHardTestsVar, matches = runHardTestsRegex)
    @Test
    fun testConcreteDictInC4() {
        val resourcePath = getResourcePath<DictExampleTest>(dictInC4Path)
        val dataResourcePath = getResourcePath<DictExampleTest>(dictInC4DataPath)
        val data = dataResourcePath.toFile().readBytes()

        val tests = BocAnalyzer.analyzeSpecificMethod(
            resourcePath,
            methodId = MethodId.ZERO,
            concreteContractData = TvmConcreteContractData(contractC4 = BagOfCells(data).roots.single()),
            tvmOptions = TvmOptions(timeout = 3.minutes)
        )

        checkInvariants(
            tests,
            listOf {
                test -> test.result !is TvmExecutionWithSoftFailure
            }
        )
    }
}