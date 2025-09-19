package org.ton.examples.types

import org.ton.Endian
import org.ton.TlbCoinsLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbEmptyLabel
import org.ton.TlbIntegerLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbMaybeRefLabel
import org.ton.TlbMsgAddrLabel
import org.ton.TlbStructure
import org.ton.TlbStructureIdProvider
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo.DataCellInfo
import org.ton.TvmParameterInfo.SliceInfo
import org.ton.bytecode.MethodId
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TlbOptions
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.machine.types.TvmReadingSwitchWithUnexpectedType
import org.usvm.machine.types.TvmUnexpectedDataReading
import org.usvm.machine.types.TvmUnexpectedEndOfReading
import org.usvm.machine.types.TvmUnexpectedRefReading
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestCellDataCoinsRead
import org.usvm.test.resolver.TvmTestCellDataIntegerRead
import org.usvm.test.resolver.TvmTestCellDataMaybeConstructorBitRead
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestInput.RecvInternalInput
import java.math.BigInteger
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputParameterInfoTests {
    private val maybePath = "/types/maybe.fc"
    private val endOfCellPath = "/types/end_of_cell.fc"
    private val simpleLoadRefPath = "/types/simple_load_ref.fc"
    private val coinsPath = "/types/load_coins.fc"
    private val msgAddrPath = "/types/load_msg_addr.fc"
    private val dictPath = "/types/dict.fc"
    private val seqLoadIntPath = "/types/seq_load_int.fc"
    private val seqLoadInt2Path = "/types/seq_load_int_2.fc"
    private val seqLoadInt3Path = "/types/seq_load_int_3.fc"
    private val intSwitchPath = "/types/switch_int.fc"
    private val intSwitch2Path = "/types/switch_int_2.fc"
    private val iterateRefsPath = "/types/iterate_refs.fc"
    private val loadRefThenLoadIntPath = "/types/load_ref_then_load_int.fc"
    private val int32FromRefPath = "/types/int32_from_ref.fc"
    private val int64FromRefPath = "/types/int64_from_ref.fc"
    private val varIntPath = "/types/load_var_int.fc"
    private val varInt2Path = "/types/load_var_int_2.fc"
    private val doubleVarIntPath = "/types/load_double_var_int.fc"
    private val coinsByPartsPath = "/types/load_coins_by_parts.fc"
    private val coinsByParts2Path = "/types/load_coins_by_parts_2.fc"
    private val coinsByPartsWrongPath = "/types/load_coins_by_parts_wrong.fc"
    private val skipEmptyLoadPath = "/types/skip_empty_load.fc"
    private val skipEmptyLoad2Path = "/types/skip_empty_load_2.fc"
    private val skipAndLoadPath = "/types/skip_and_load.fc"
    private val skipConstPath = "/types/skip_consts.fc"
    private val readStoredInconsistentPath = "/types/read_stored_inconsistent.fc"
    private val readStoredConstPath = "/types/read_stored_const.fc"
    private val readStoredConstNegativePath = "/types/read_stored_const_negative.fc"
    private val readStoredIntInsteadOfUIntPath = "/types/read_stored_int_instead_of_uint.fc"
    private val readStoredCoinsInconsistentPath = "/types/read_stored_coins_inconsistent.fc"
    private val readStoredCoinsPath = "/types/read_stored_coins.fc"
    private val readStoredSymbolicIntPath = "/types/read_stored_symbolic_int.fc"
    private val storeEmptyCoinsPath = "/types/store_empty_coins.fc"
    private val load3CoinsPath = "/types/load_3_coins.fc"
    private val severalCoinsInC4Path = "/types/c4/several_coins_in_c4.fc"
    private val severalCoinsInRefOfC4Path = "/types/c4/several_coins_in_ref_of_c4.fc"
    private val addrAfterCoinsPath = "/types/c4/address_after_coins.fc"
    private val addrAfterCoinsNoModifyPath = "/types/c4/address_after_coins_no_modify.fc"
    private val intAfterCoinsPath = "/types/c4/int_after_coins.fc"
    private val fixedSizeSliceAfterCoinsPath = "/types/c4/fixed_size_slice_after_coins.fc"
    private val fixedSizeSliceAfterCoinsNoModifyPath = "/types/c4/fixed_size_slice_after_coins_no_modify.fc"
    private val readStoredSlicePath = "/types/read_stored_slice.fc"
    private val zeroCoinsPath = "/types/c4/zero_coins.fc"
    private val constIntAfterCoinsPath = "/types/c4/const_int_after_coins.fc"
    private val readStoredConstAddrNonePath = "/types/read_stored_const_addr_none.fc"
    private val storeAndComparePath = "/types/store_and_compare.fc"

    @Test
    fun testCorrectMaybe() {
        val resourcePath = extractResource(maybePath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(maybeStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testMaybeInsteadOfInt() {
        val resourcePath = extractResource(maybePath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(int64Structure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                val error = exit.exit as? TvmReadingOfUnexpectedType ?: return@listOf false
                error.actualType is TvmTestCellDataMaybeConstructorBitRead && error.expectedLabel is TlbIntegerLabel
            },
        )
    }

    @Test
    fun testUnexpectedRead() {
        val resourcePath = extractResource(maybePath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(TlbEmptyLabel))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                val error = exit.exit as? TvmUnexpectedDataReading ?: return@listOf false
                error.readingType is TvmTestCellDataMaybeConstructorBitRead
            },
        )
    }

    @Test
    fun testTurnOff() {
        val resourcePath = extractResource(maybePath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(TlbCoinsLabel))))
        val options =
            TvmOptions(
                turnOnTLBParsingChecks = false,
                performAdditionalChecksWhileResolving = true,
                analyzeBouncedMessaged = false,
            )
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result !is TvmMethodFailure })
        assertTrue(tests.any { it.result !is TvmExecutionWithStructuralError })
    }

    @Test
    fun testExpectedEndOfCell() {
        val resourcePath = extractResource(endOfCellPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(TlbEmptyLabel))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })
    }

    @Test
    fun testUnexpectedEndOfCell() {
        val resourcePath = extractResource(endOfCellPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(int64Structure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                exit.exit is TvmUnexpectedEndOfReading
            },
        )
    }

    @Test
    fun testUnexpectedEndOfCell2() {
        val resourcePath = extractResource(endOfCellPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(someRefStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                exit.exit is TvmUnexpectedEndOfReading
            },
        )
    }

    @Test
    fun testExpectedLoadRef() {
        val resourcePath = extractResource(simpleLoadRefPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(someRefStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })
    }

    @Test
    fun testPossibleLoadRef() {
        val resourcePath = extractResource(simpleLoadRefPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(prefixInt64Structure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result is TvmMethodFailure })
    }

    @Test
    fun testUnexpectedLoadRef() {
        val resourcePath = extractResource(simpleLoadRefPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(int64Structure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                exit.exit is TvmUnexpectedRefReading
            },
        )
    }

    @Test
    fun testMaybeInsteadOfCoins() {
        val resourcePath = extractResource(maybePath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(coinsStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                val error = exit.exit as? TvmReadingOfUnexpectedType ?: return@listOf false
                error.actualType is TvmTestCellDataMaybeConstructorBitRead && error.expectedLabel is TlbCoinsLabel
            },
        )
    }

    @Test
    fun testCorrectCoins() {
        val resourcePath = extractResource(coinsPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(coinsStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testCoinsInsteadOfMsgAddr() {
        val resourcePath = extractResource(coinsPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(wrappedMsgStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf(
                { test ->
                    val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                    val error = exit.exit as? TvmReadingOfUnexpectedType ?: return@listOf false
                    error.actualType is TvmTestCellDataCoinsRead && error.expectedLabel is TlbMsgAddrLabel
                },
                { test ->
                    val param = (test.input as RecvInternalInput).msgBody
                    val cell = param.cell
                    cell.data.startsWith("100")
                },
            ),
        )
    }

    @Test
    fun testCorrectMsgAddr() {
        val resourcePath = extractResource(msgAddrPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(wrappedMsgStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testCorrectDict() {
        val resourcePath = extractResource(dictPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(dict256Structure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testCoinsInsteadOfDict() {
        val resourcePath = extractResource(coinsPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(dict256Structure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                val error = exit.exit as? TvmReadingOfUnexpectedType ?: return@listOf false
                error.actualType is TvmTestCellDataCoinsRead && error.expectedLabel is TlbMaybeRefLabel
            },
        )
    }

    @Test
    fun testIntSwitchError() {
        val resourcePath = extractResource(seqLoadIntPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(intSwitchStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                val error = exit.exit as? TvmReadingOfUnexpectedType ?: return@listOf false
                val expectedType = error.expectedLabel
                val actualType = error.actualType as? TvmTestCellDataIntegerRead ?: return@listOf false
                actualType.bitSize == 64 &&
                    expectedType is TlbIntegerLabelOfConcreteSize &&
                    expectedType.concreteSize == 32
            },
        )
    }

    @Test
    fun testIntSwitchCorrect() {
        val resourcePath = extractResource(intSwitchPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(intSwitchStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testIntSwitch2Correct() {
        val resourcePath = extractResource(intSwitch2Path)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(intSwitchStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testYStructureError() {
        val resourcePath = extractResource(seqLoadInt2Path)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                structureY,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                val error = exit.exit as? TvmReadingOfUnexpectedType ?: return@listOf false
                val expectedType = error.expectedLabel
                val actualType = error.actualType as? TvmTestCellDataIntegerRead ?: return@listOf false
                actualType.bitSize == 32 &&
                    expectedType is TlbIntegerLabelOfConcreteSize &&
                    expectedType.concreteSize == 16
            },
        )
    }

    @Test
    fun testYStructureCorrect() {
        val resourcePath = extractResource(seqLoadInt3Path)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                structureY,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testIterateRefsRecursiveChain() {
        val resourcePath = extractResource(iterateRefsPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                refListStructure,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure && it.result !is TvmExecutionWithStructuralError })
    }

    @Test
    fun testIterateRefsNonRecursiveChain() {
        val resourcePath = extractResource(iterateRefsPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                nonRecursiveChainStructure,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)

        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure && it.result !is TvmExecutionWithStructuralError })

        checkInvariants(
            tests,
            listOf(
                { test ->
                    val depth = (test.input as RecvInternalInput).msgBody.cell.dataCellDepth()
                    depth == 3
                },
                { test ->
                    val cell = (test.input as RecvInternalInput).msgBody.cell
                    cell.data.isEmpty() && cell.refs.size == 1
                },
                { test ->
                    var cell = (test.input as RecvInternalInput).msgBody.cell
                    while (cell.refs.isNotEmpty()) {
                        cell = cell.refs.first() as TvmTestDataCellValue
                    }
                    cell.data == "11011"
                },
            ),
        )
    }

    @Test
    fun testLoadRefOnNonRecursiveChain() {
        val resourcePath = extractResource(simpleLoadRefPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                nonRecursiveChainStructure,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)

        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure && it.result !is TvmExecutionWithStructuralError })

        checkInvariants(
            tests,
            listOf(
                { test ->
                    val depth = (test.input as RecvInternalInput).msgBody.cell.dataCellDepth()
                    depth == 3
                },
                { test ->
                    val cell = (test.input as RecvInternalInput).msgBody.cell
                    cell.data.isEmpty() && cell.refs.size == 1
                },
                { test ->
                    var cell = (test.input as RecvInternalInput).msgBody.cell
                    while (cell.refs.isNotEmpty()) {
                        cell = cell.refs.first() as TvmTestDataCellValue
                    }
                    cell.data == "11011"
                },
            ),
        )
    }

    @Test
    fun testEOPInsteadOfInt() {
        val resourcePath = extractResource(endOfCellPath)

        val label =
            TlbCompositeLabel(
                name = "X",
            ).also {
                it.internalStructure =
                    TlbStructure.KnownTypePrefix(
                        id = TlbStructureIdProvider.provideId(),
                        typeLabel = TlbIntegerLabelOfConcreteSize(100, true, Endian.BigEndian),
                        typeArgIds = emptyList(),
                        rest = TlbStructure.Empty,
                        owner = it,
                    )
            }

        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(label))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = (test.result as? TvmExecutionWithStructuralError)?.exit ?: return@listOf false
                exit is TvmUnexpectedEndOfReading
            },
        )
    }

    @Test
    fun testUnexpectedLoadAfterRef() {
        val resourcePath = extractResource(loadRefThenLoadIntPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                someRefStructure,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = (test.result as? TvmExecutionWithStructuralError)?.exit ?: return@listOf false
                exit is TvmUnexpectedDataReading
            },
        )
    }

    @Test
    fun testLoadWrongIntFromRef() {
        val resourcePath = extractResource(int32FromRefPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                structIntRef,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = (test.result as? TvmExecutionWithStructuralError)?.exit ?: return@listOf false
                exit is TvmReadingOfUnexpectedType
            },
        )
    }

    @Test
    fun testLoadCorrectIntFromRef() {
        val resourcePath = extractResource(int64FromRefPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                structIntRef,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmMethodFailure && it.result !is TvmExecutionWithStructuralError })
    }

    @Ignore
    @Test
    fun testLoadWrongIntFromRefWithUnknown() {
        val resourcePath = extractResource(int32FromRefPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                structInRefAndUnknownSuffix,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = (test.result as? TvmExecutionWithStructuralError)?.exit ?: return@listOf false
                exit is TvmReadingOfUnexpectedType
            },
        )
    }

    @Test
    fun testLoadVarInt() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(varIntPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                customVarInteger,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmExecutionWithStructuralError })
        assertTrue(tests.all { (it.result as? TvmMethodFailure)?.exitCode != 1001 })
    }

    @Test
    fun testLoadVarInt2() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(varInt2Path)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                customVarInteger,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmExecutionWithStructuralError })
        assertTrue(tests.all { (it.result as? TvmMethodFailure)?.exitCode != 1001 })
    }

    @Test
    fun testLoadDoubleVarInt() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(doubleVarIntPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                doubleCustomVarInteger,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmExecutionWithStructuralError })
        assertTrue(tests.all { (it.result as? TvmMethodFailure)?.exitCode != 1000 })

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1002 },
            ),
        )
    }

    @Test
    fun testLoadCoinsByParts() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(coinsByPartsPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(coinsStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError
            },
        )
    }

    @Test
    fun testLoadCoinsByParts2() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(coinsByParts2Path)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(coinsStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        checkInvariants(
            tests,
            listOf(
                { test -> test.result !is TvmExecutionWithStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1001 },
            ),
        )

        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
            ),
        )
    }

    @Test
    fun testLoadCoinsByPartsWrong() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(coinsByPartsWrongPath)

        val inputInfo =
            TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(coinsStructure))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        propertiesFound(
            tests,
            listOf { test ->
                val exit = (test.result as? TvmExecutionWithStructuralError)?.exit ?: return@listOf false
                exit is TvmReadingOfUnexpectedType
            },
        )
    }

    @Test
    fun testSkipEmptyLoad() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(skipEmptyLoadPath)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                doubleCustomVarInteger,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmExecutionWithStructuralError })

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
            ),
        )
    }

    @Test
    fun testReadStoredInconsistent() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredInconsistentPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        propertiesFound(
            tests,
            listOf { test -> test.result is TvmExecutionWithStructuralError },
        )
    }

    @Test
    fun testSkipEmptyLoad2() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(skipEmptyLoad2Path)

        val inputInfo =
            TvmInputInfo(
                mapOf(
                    0 to
                        SliceInfo(
                            DataCellInfo(
                                customVarIntegerWithSuffix,
                            ),
                        ),
                ),
            )

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.all { it.result !is TvmExecutionWithStructuralError })

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testReadStoredConst() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredConstPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result is TvmSuccessfulExecution } }
    }

    @Test
    fun testReadStoredConstAddrNone() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredConstAddrNonePath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.isNotEmpty() }
        assertTrue { tests.all { it.result is TvmSuccessfulExecution } }
    }

    @Test
    fun testSkipAndLoadWrong() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(skipAndLoadPath)

        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(intAndInt))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        propertiesFound(
            tests,
            listOf { test ->
                val exit =
                    (test.result as? TvmExecutionWithStructuralError)?.exit as? TvmReadingOfUnexpectedType
                        ?: return@listOf false
                exit.actualType is TvmTestCellDataCoinsRead
            },
        )
    }

    @Test
    fun testSkipAndLoadCorrect() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(skipAndLoadPath)

        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(intAndCoins))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result is TvmSuccessfulExecution } }
    }

    @Test
    fun testAllocatedCellChecksOption() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredInconsistentPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = false),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }
    }

    @Test
    fun testReadStoredConstNegative() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredConstNegativePath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result is TvmSuccessfulExecution } }
    }

    @Test
    fun testSkipAndLoadCorrect2() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(skipAndLoadPath)

        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(doubleIntAndCoins))))
        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(BigInteger.ZERO to inputInfo),
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result is TvmSuccessfulExecution } }
    }

    @Test
    fun testReadStoredIntInsteadOfUInt() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredIntInsteadOfUIntPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        propertiesFound(
            tests,
            listOf { test -> test.result is TvmExecutionWithStructuralError },
        )
    }

    @Test
    fun testReadStoredCoinsInconsistent() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredCoinsInconsistentPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        propertiesFound(
            tests,
            listOf { test -> test.result is TvmExecutionWithStructuralError },
        )
    }

    @Test
    fun testReadStoredCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }

        checkInvariants(
            tests,
            listOf(
                { test -> test.result !is TvmExecutionWithStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1000 },
            ),
        )
    }

    @Test
    fun testReadStoredSymbolicInt() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredSymbolicIntPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmExecutionWithStructuralError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testStoreEmptyCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(storeEmptyCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }
    }

    @Test
    fun testSkipConst() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(skipConstPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        useReceiverInputs = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
                    ),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }
    }

    @Test
    fun test3Coins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(load3CoinsPath)

        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(threeCoins))))

        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false),
                inputInfo = mapOf(MethodId.ZERO to inputInfo),
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        checkInvariants(
            tests,
            listOf(
                { test -> test.result !is TvmExecutionWithStructuralError },
                { test -> test.numberOfAddressesWithAssertedDataConstraints == 0 },
            ),
        )

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testSeveralCoinsInC4() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(severalCoinsInC4Path)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testSeveralCoinsInRefOfC4() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(severalCoinsInRefOfC4Path)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testAddrAfterCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(addrAfterCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testZeroCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(zeroCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        loopIterationLimit = 4,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testConstIntAfterCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(constIntAfterCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        loopIterationLimit = 4,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testAddrAfterCoinsNoModify() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(addrAfterCoinsNoModifyPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        loopIterationLimit = 3,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        propertiesFound(
            tests,
            listOf { test -> test.result is TvmSuccessfulExecution },
        )

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1000 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1001 },
                { test -> test.result !is TvmExecutionWithStructuralError },
            ),
        )
    }

    @Test
    fun testFixedSizeSliceAfterCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(fixedSizeSliceAfterCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        loopIterationLimit = 4,
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        assertTrue { tests.all { it.result !is TvmExecutionWithStructuralError } }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testFixedSizeSliceAfterCoinsNoModify() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(fixedSizeSliceAfterCoinsNoModifyPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        loopIterationLimit = 4,
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        tests.any { it.result is TvmSuccessfulExecution }

        checkInvariants(
            tests,
            listOf(
                { test -> test.result !is TvmExecutionWithStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1000 },
            ),
        )
    }

    @Test
    fun testIntAfterCoins() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(intAfterCoinsPath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun readStoredSliceTest() {
        val resourcePath = getResourcePath<InputParameterInfoTests>(readStoredSlicePath)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions =
                    TvmOptions(
                        analyzeBouncedMessaged = false,
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        checkInvariants(
            tests,
            listOf(
                { test -> test.result !is TvmExecutionWithStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1001 },
            ),
        )

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun maybeInLongTagTest() {
        // loading dict == load_maybe_ref
        val resourcePath = getResourcePath<InputParameterInfoTests>(dictPath)
        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(intSwitchStructure))))
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(MethodId.ZERO to inputInfo),
                tvmOptions =
                    TvmOptions(
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        checkInvariants(
            tests,
            listOf(
                { test -> test.result !is TvmSuccessfulExecution },
                { test -> test.result !is TvmMethodFailure },
            ),
        )

        propertiesFound(
            tests,
            listOf { test ->
                (test.result as? TvmExecutionWithStructuralError)?.exit is TvmReadingSwitchWithUnexpectedType
            },
        )
    }

    @Test
    fun loadMaybeRefWhenNoRefsExpectedTest() {
        // loading dict == load_maybe_ref
        val resourcePath = getResourcePath<InputParameterInfoTests>(dictPath)
        val inputInfo = TvmInputInfo(mapOf(0 to SliceInfo(DataCellInfo(recursiveStructure))))
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                inputInfo = mapOf(MethodId.ZERO to inputInfo),
                tvmOptions =
                    TvmOptions(
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        checkInvariants(
            tests,
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )

        propertiesFound(
            tests,
            listOf { test ->
                (test.result as? TvmExecutionWithStructuralError)?.exit is TvmUnexpectedRefReading
            },
        )
    }

    @Test
    fun testEqualityAfterStore() {
        val path = extractResource(storeAndComparePath)

        val results =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions =
                    TvmOptions(
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                        TlbOptions(
                            performTlbChecksOnAllocatedCells = true,
                        ),
                    ),
            )

        val tests = results.first { it.methodId == MethodId.ZERO }

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 999 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
            )
        )

        TvmTestExecutor.executeGeneratedTests(tests, path, TsRenderer.ContractType.Func)
    }
}
