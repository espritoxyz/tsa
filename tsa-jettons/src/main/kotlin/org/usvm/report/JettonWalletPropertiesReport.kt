package org.usvm.report

import kotlinx.serialization.Serializable
import org.ton.bytecode.TsaContractCode
import org.ton.extractJettonContractInfo
import org.usvm.checkers.BlacklistAddressChecker
import org.usvm.getContractFromBytes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension

@Serializable
data class JettonWalletPropertiesReport(
    val analyzedAddress: String,
    val jettonWalletCodeHashBase64: String,
    val blacklistedAddresses: Set<String>,
)

fun runAnalysisAndCreateReport(address: String): JettonWalletPropertiesReport {
    val contractInfo = extractJettonContractInfo(address)
    val contractBytes = contractInfo.contractBytes
    val contract = getContractFromBytes(contractBytes)

    return JettonWalletPropertiesReport(
        analyzedAddress = address,
        jettonWalletCodeHashBase64 = contractInfo.jettonWalletCodeHashBase64,
        blacklistedAddresses = runAnalysisAndCreateReport(contract)
    )
}

@OptIn(ExperimentalPathApi::class)
fun runAnalysisAndCreateReport(contract: TsaContractCode): Set<String> {
    val targetResourcesDir = makeTmpDirForResourcesForJarEnvironmentOrNull()

    try {
        val blacklistAddressChecker = BlacklistAddressChecker(targetResourcesDir)
        val blacklistedAddressesExecutions =
            blacklistAddressChecker.findConflictingExecutions(
                contract,
                stopWhenFoundOneConflictingExecution = false
            )

        val description =
            if (blacklistedAddressesExecutions.isNotEmpty()) {
                val blacklistedAddressesDescription =
                    blacklistAddressChecker.getDescription(
                        blacklistedAddressesExecutions
                    )

                blacklistedAddressesDescription.blacklistedAddresses
            } else {
                null
            }

        return description ?: emptySet()
    } finally {
        targetResourcesDir?.deleteRecursively()
    }
}

private fun makeTmpDirForResourcesForJarEnvironmentOrNull(): Path? {
    val uri =
        JettonWalletPropertiesReport::class.java.protectionDomain.codeSource.location
            .toURI()
    val extension = Path(uri.path).extension

    if (extension != "jar") {
        return null
    }

    JarFile(uri.schemeSpecificPart).use { jar ->
        val resourcesPrefix = "resources"
        val targetResourcesDir = createTempDirectory()
        jar
            .entries()
            .asSequence()
            .filter { it.name.startsWith(resourcesPrefix) }
            .forEach { entry ->
                // Determine the target path for each entry
                val targetPath = targetResourcesDir.resolve(entry.name.removePrefix("$resourcesPrefix/"))
                if (entry.isDirectory) {
                    Files.createDirectories(targetPath)
                } else {
                    // Create parent directories if necessary
                    Files.createDirectories(targetPath.parent)
                    // Copy the file from the JAR to the target path
                    jar.getInputStream(entry).use { input ->
                        Files.copy(input, targetPath, REPLACE_EXISTING)
                    }
                }
            }

        return targetResourcesDir
    }
}
