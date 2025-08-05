package org.ton.examples.registers

import org.ton.bytecode.MethodId
import java.math.BigInteger
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.compileFuncToFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Test

class RegistersTest {
    private val registerC0TestPath: String = "/registers/register-c0.fc"
    private val registerC1TestPath: String = "/registers/register-c1.fc"
    private val registerC2TestPath: String = "/registers/register-c2.fc"
    private val registerC4TestPath: String = "/registers/register-c4.fc"
    private val registerC5TestPath: String = "/registers/register-c5.fc"
    private val registerC7TestPath: String = "/registers/register-c7.fc"

    @Test
    fun testC0Register() {
        val methodsBlackList = hashSetOf(BigInteger.ONE)
        analyzeContract(registerC0TestPath, methodsBlackList)
    }

    @Test
    fun testC1Register() {
        val methodsBlackList = hashSetOf(BigInteger.ONE)
        analyzeContract(registerC1TestPath, methodsBlackList)
    }

    @Test
    fun testC2Register() {
        analyzeContract(registerC2TestPath)
    }

    @Test
    fun testC4Register() {
        analyzeContract(registerC4TestPath)
    }

    @Test
    fun testC5Register() {
        analyzeContract(registerC5TestPath)
    }

    @Test
    fun testC7Register() {
        analyzeContract(registerC7TestPath)
    }

    private fun analyzeContract(
        contractPath: String,
        methodsBlackList: Set<MethodId> = hashSetOf(),
    ) {
        val resourcePath = this::class.java.getResource(contractPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $contractPath")
        val tmpFiftFile = createTempFile(suffix = ".boc")

        try {
            compileFuncToFift(resourcePath, tmpFiftFile)

            val symbolicResult = compileAndAnalyzeFift(
                tmpFiftFile,
                methodsBlackList = methodsBlackList,
                tvmOptions = testConcreteOptions
            )

            compareSymbolicAndConcreteResults(setOf(0), symbolicResult) { methodId ->
                runFiftMethod(tmpFiftFile, methodId)
            }
        } finally {
            tmpFiftFile.deleteIfExists()
        }
    }
}