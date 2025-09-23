package org.ton.examples.dict

import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class DictExampleTest {
    private val dictExamplePath: String = "/dict/dict_examples.fc"
    private val addAndRemovePath: String = "/dict/add_and_remove.fc"

    @Test
    fun testDictExamples() {
        val resourcePath = getResourcePath<DictExampleTest>(dictExamplePath)

        val symbolicResult = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertTrue(symbolicResult.isNotEmpty())
    }

    @Test
    fun testAddAndRemove() {
        val resourcePath = getResourcePath<DictExampleTest>(addAndRemovePath)

        val symbolicResult =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = testOptionsToAnalyzeSpecificMethod,
            )
        val tests = symbolicResult.testSuites.single()
        assertTrue { tests.all { it.result !is TvmSuccessfulExecution } }
    }
}
