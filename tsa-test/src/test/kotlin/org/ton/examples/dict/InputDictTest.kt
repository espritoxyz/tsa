package org.ton.examples.dict

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.executionCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeSpecificMethod
import org.ton.test.utils.propertiesFound
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite
import java.nio.file.Path
import kotlin.test.Test

class InputDictTest {
    private val inputDictTests = "/dict/input-dict.fc"

    internal fun TvmSymbolicTestSuite.assertHasExitCodes(vararg exitCodes: Int) {
        val properties =
            exitCodes.map { exitCode ->
                { test: TvmSymbolicTest -> test.executionCode() == exitCode }
            }
        propertiesFound(this, properties)
    }

    internal fun TvmSymbolicTestSuite.assertDoesNotHaveExitCodes(vararg exitCodes: Int) {
        val properties =
            exitCodes.map { exitCode ->
                { test: TvmSymbolicTest -> test.executionCode() != exitCode }
            }
        checkInvariants(this, properties)
    }

    private fun analyzeMethodAndRun(
        methodId: Int,
        checker: (TvmSymbolicTestSuite) -> Unit,
    ) {
        val (path, tests) = analyzeMethod(methodId)
        checker(tests)
        TvmTestExecutor.executeGeneratedTests(tests, path, TsRenderer.ContractType.Func)
    }

    private fun analyzeMethod(method: Int): Pair<Path, TvmSymbolicTestSuite> {
        val path = extractResource(inputDictTests)
        val tests = funcCompileAndAnalyzeSpecificMethod(path, methodId = method.toMethodId())
        return path to tests
    }

    @Test
    fun `get_next is pure function`() =
        analyzeMethodAndRun(1) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_next is pure function when not found`() =
        analyzeMethodAndRun(8) { tests ->
            tests.assertHasExitCodes(300, 301, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_prev is pure function`() =
        analyzeMethodAndRun(2) { tests ->
            tests.assertHasExitCodes(300)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `next_prev is id`() =
        analyzeMethodAndRun(3) { tests ->
            tests.assertHasExitCodes(301)
            tests.assertDoesNotHaveExitCodes(400)
        }

    @Test
    fun `get is pure function when found`() =
        analyzeMethodAndRun(9) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_max returns the largest key`() =
        analyzeMethodAndRun(4) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_max returns the largest key 2`() =
        analyzeMethodAndRun(5) { tests ->
            tests.assertHasExitCodes(300, 301, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_max returns not found implies empty`() =
        analyzeMethodAndRun(10) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_next and get are consistent`() =
        analyzeMethodAndRun(6) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400, 401)
        }

    @Test
    fun `get_next and get are consistent reordered`() =
        analyzeMethodAndRun(7) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400, 401, 402)
        }

    @Test
    fun `set and get are consistent`() {
        analyzeMethodAndRun(11) { tests ->
            tests.assertHasExitCodes(500)
            tests.assertDoesNotHaveExitCodes(400)
        }
    }

    @Test
    fun `set overrides values`() {
        analyzeMethodAndRun(12) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400)
        }
    }

    @Test
    fun `get and delete are consistent`() {
        analyzeMethodAndRun(13) { tests ->
            tests.assertHasExitCodes(500)
            tests.assertDoesNotHaveExitCodes(400)
        }
    }

    @Test
    fun `delete overrides values`() {
        analyzeMethodAndRun(14) { tests ->
            tests.assertHasExitCodes(300, 500)
            tests.assertDoesNotHaveExitCodes(400)
        }
    }
}
