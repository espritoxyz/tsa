package org.usvm

import org.ton.bytecode.TsaContractCode
import org.usvm.checkers.TvmChecker
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.TvmAdditionalStopStrategy
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.TvmFailureType
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmState
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

fun getContractFromBytes(bytes: ByteArray): TsaContractCode {
    val tmpBocFile = createTempFile(suffix = ".boc")
    try {
        tmpBocFile.writeBytes(bytes)
        return BocAnalyzer.loadContractFromBoc(tmpBocFile)
    } finally {
        tmpBocFile.deleteIfExists()
    }
}

const val FIFT_STDLIB_PATH = "/fiftstdlib"

class FirstFailureTerminator : TvmAdditionalStopStrategy {
    private var shouldStop: Boolean = false

    override fun shouldStop(): Boolean = shouldStop

    override fun onStateTerminated(
        state: TvmState,
        stateReachable: Boolean,
    ) {
        if (!stateReachable) {
            return
        }
        val result = state.result
        if (result is TvmResult.TvmFailure && result.type !in forbiddenTypes) {
            shouldStop = true
        }
    }

    override fun stopReason(): String = "Found conflicting execution"

    companion object {
        val forbiddenTypes = listOf(TvmFailureType.StructuralError)
    }
}

internal fun Path?.resolveResourcePath(resourceName: String): Path =
    // For this path presenting, drop the first `/` char in the resource name to make it a relative path
    this?.resolve(resourceName.substring(startIndex = 1)) ?: getResourcePath<TvmChecker>(resourceName)
