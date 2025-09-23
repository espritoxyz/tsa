package org.ton.examples.types

import org.ton.Endian
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.test.resolver.TvmCellDataTypeLoad
import org.usvm.test.resolver.TvmTestCellDataIntegerRead
import org.usvm.test.resolver.TvmTestCellDataMaybeConstructorBitRead
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestSliceValue
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class IteExample {
    private val path = "/types/ite_example.fc"

    @Ignore("ksmt bug https://github.com/UnitTestBot/ksmt/issues/160")
    @Test
    fun testIteExample() {
        val resourcePath = extractResource(path)

        val result =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = testOptionsToAnalyzeSpecificMethod,
            )
        assertEquals(1, result.testSuites.size)
        val testSuite = result.testSuites.first()
        propertiesFound(
            testSuite,
            listOf(
                { test ->
                    val casted =
                        (test.input.usedParameters.lastOrNull() as? TvmTestSliceValue)?.cell
                            ?: return@listOf false
                    var predicateResult =
                        casted.knownTypes ==
                            listOf(
                                TvmCellDataTypeLoad(TvmTestCellDataMaybeConstructorBitRead, 0),
                                TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 1),
                            )
                    // the input dict is empty => there is no dict ref
                    predicateResult = predicateResult && casted.data.firstOrNull() == '0'
                    predicateResult = predicateResult && casted.refs.size >= 2
                    if (predicateResult) {
                        // the only two refs are data cells, that have dicts is them
                        val firstIsDict =
                            (casted.refs[0] as? TvmTestDataCellValue)?.knownTypes?.firstOrNull()?.type ==
                                TvmTestCellDataMaybeConstructorBitRead
                        val secondIsDict =
                            (casted.refs[1] as? TvmTestDataCellValue)?.knownTypes?.firstOrNull()?.type ==
                                TvmTestCellDataMaybeConstructorBitRead
                        predicateResult = firstIsDict || secondIsDict
                    }
                    predicateResult
                },
                { test ->
                    val casted =
                        (test.input.usedParameters.lastOrNull() as? TvmTestSliceValue)?.cell
                            ?: return@listOf false
                    var predicateResult =
                        casted.knownTypes ==
                            listOf(
                                TvmCellDataTypeLoad(TvmTestCellDataMaybeConstructorBitRead, 0),
                                TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 1),
                            )
                    // the input dict is not empty => the first ref is dict cell
                    predicateResult = predicateResult && casted.data.firstOrNull() == '1'
                    predicateResult = predicateResult && casted.refs.size >= 3
                    if (predicateResult) {
                        // the dict cell
                        predicateResult = casted.refs.first() is TvmTestDictCellValue
                        // other two refs are data cells, that have dicts is them
                        val firstIsDict =
                            (casted.refs[1] as? TvmTestDataCellValue)?.knownTypes?.firstOrNull()?.type ==
                                TvmTestCellDataMaybeConstructorBitRead
                        val secondIsDict =
                            (casted.refs[2] as? TvmTestDataCellValue)?.knownTypes?.firstOrNull()?.type ==
                                TvmTestCellDataMaybeConstructorBitRead
                        predicateResult = predicateResult && (firstIsDict || secondIsDict)
                    }
                    predicateResult
                },
            ),
        )
    }
}
