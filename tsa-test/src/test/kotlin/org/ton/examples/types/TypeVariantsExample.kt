package org.ton.examples.types

import org.ton.Endian
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.test.resolver.TvmCellDataTypeLoad
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestCellDataBitArrayRead
import org.usvm.test.resolver.TvmTestCellDataIntegerRead
import org.usvm.test.resolver.TvmTestCellDataMaybeConstructorBitRead
import org.usvm.test.resolver.TvmTestSliceValue
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeVariantsExample {
    private val path = "/types/variants.fc"

    @Test
    fun testVariants() {
        val resourcePath = this::class.java.getResource(path)?.path?.let { Path(it) }
            ?: error("Cannot find resource $path")

        val result = funcCompileAndAnalyzeAllMethods(
            resourcePath,
            tvmOptions = testOptionsToAnalyzeSpecificMethod
        )
        assertEquals(1, result.testSuites.size)
        val testSuite = result.testSuites.first()

        val expectedTypeSet1 = { bitArraySize: Int ->
            listOf(
                TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 0),
                TvmCellDataTypeLoad(TvmTestCellDataBitArrayRead(bitArraySize), 8)
            )
        }

        val expectedTypeSet2 = { intSize: Int ->
            listOf(
                TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 0),
                TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(intSize, true, Endian.BigEndian), 8)
            )
        }

        propertiesFound(
            testSuite,
            listOf(
                generatePredicate1(listOf(TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 0))),
                generatePredicate1(
                    listOf(
                        TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 0),
                        TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(10, true, Endian.BigEndian), 8)
                    )
                ),
                generatePredicate1(
                    listOf(
                        TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 0),
                        TvmCellDataTypeLoad(TvmTestCellDataMaybeConstructorBitRead, 8)
                    )
                ),
                generatePredicate1(
                    listOf(
                        TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(8, true, Endian.BigEndian), 0),
                        TvmCellDataTypeLoad(TvmTestCellDataBitArrayRead(100), 8)
                    )
                ),
                generatePredicate(
                    listOf(
                        expectedTypeSet1(11),
                        expectedTypeSet1(12)
                    )
                ),
                generatePredicate(
                    listOf(
                        expectedTypeSet2(3),
                        expectedTypeSet2(4)
                    )
                )
            )
        )
    }

    private fun generatePredicate1(variant: List<TvmCellDataTypeLoad>): (TvmSymbolicTest) -> Boolean =
        generatePredicate(listOf(variant))

    private fun generatePredicate(variants: List<List<TvmCellDataTypeLoad>>): (TvmSymbolicTest) -> Boolean =
        result@{ test ->
            val casted = (test.input.usedParameters.last() as? TvmTestSliceValue)?.cell ?: return@result false
            variants.any { variant ->
                casted.knownTypes == variant
            }
        }
}