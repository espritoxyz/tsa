package org.ton.examples.tact

import org.ton.test.utils.tactCompileAndAnalyzeAllMethods
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue

class TactJettonTest {
    private val tactConfigPath = getResourcePath<TactJettonTest>("/contracts/tact-jetton_b3608ca/tact.config.json")

    @Test
    fun testMinter() {
        val symbolicResult =
            tactCompileAndAnalyzeAllMethods(
                TactSourcesDescription(tactConfigPath, "Jetton", "JettonMinter"),
                takeEmptyTests = true
            )
        assertTrue {
            symbolicResult.testSuites.all { it.tests.isNotEmpty() }
        }
    }

    @Test
    fun testWallet() {
        val symbolicResult =
            tactCompileAndAnalyzeAllMethods(
                TactSourcesDescription(tactConfigPath, "Jetton", "JettonWallet"),
                takeEmptyTests = true
            )
        assertTrue {
            symbolicResult.testSuites.all { it.tests.isNotEmpty() }
        }
    }
}
