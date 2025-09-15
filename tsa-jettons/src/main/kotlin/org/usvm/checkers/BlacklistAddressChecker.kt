package org.usvm.checkers

import org.ton.TlbCompositeLabel
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo
import org.ton.bytecode.TsaContractCode
import org.ton.tlb.readFromJson
import org.usvm.FIFT_STDLIB_PATH
import org.usvm.machine.TvmContext.Companion.stdMsgAddrSize
import org.usvm.machine.getFuncContract
import org.usvm.resolveResourcePath
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestCellDataMsgAddrRead
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestSliceValue
import java.nio.file.Path

data class BlacklistAddressChecker(
    private val resourcesDir: Path?,
) : TvmChecker {
    private val checkerResourcePath = resourcesDir.resolveResourcePath(CHECKER_PATH)
    private val tlbResourcePath = resourcesDir.resolveResourcePath(TLB_PATH)
    private val fiftStdlibPath = resourcesDir.resolveResourcePath(FIFT_STDLIB_PATH)

    private val tlbFormat =
        readFromJson(tlbResourcePath, "InternalMsgBody") as? TlbCompositeLabel
            ?: error("Couldn't parse TL-B structure")
    private val inputInfo =
        TvmParameterInfo.SliceInfo(
            TvmParameterInfo.DataCellInfo(
                tlbFormat,
            ),
        )

    override fun findConflictingExecutions(
        contractUnderTest: TsaContractCode,
        stopWhenFoundOneConflictingExecution: Boolean,
    ): List<TvmSymbolicTest> {
        val checkerContract = getFuncContract(checkerResourcePath, fiftStdlibPath, isTSAChecker = true)
        return runAnalysisAndExtractFailingExecutions(
            listOf(checkerContract, contractUnderTest),
            stopWhenFoundOneConflictingExecution,
            inputInfo = TvmInputInfo(mapOf(0 to inputInfo)),
        )
    }

    private fun extractMsgBody(test: TvmSymbolicTest): TvmTestDataCellValue? {
        val msgBodySlice =
            test.input.usedParameters.lastOrNull() as? TvmTestSliceValue
                ?: return null
        return msgBodySlice.cell
    }

    fun getDescription(conflictingExecutions: List<TvmSymbolicTest>): ResultDescription {
        val blacklistedAddresses =
            conflictingExecutions.mapNotNullTo(mutableSetOf()) { test ->
                check(test.result is TvmMethodFailure) {
                    "Unexpected execution: $test"
                }
                val msgBody =
                    extractMsgBody(test)
                        ?: return@mapNotNullTo null
                val firstAddressTypeLoad =
                    msgBody.knownTypes.firstOrNull { it.type is TvmTestCellDataMsgAddrRead }
                        ?: return@mapNotNullTo null

                val firstAddress =
                    msgBody.data.substring(
                        firstAddressTypeLoad.offset,
                        firstAddressTypeLoad.offset + stdMsgAddrSize,
                    )

                firstAddress.takeLast(MSG_ADDR_MAIN_PART_LENGTH)
            }

        return ResultDescription(blacklistedAddresses)
    }

    data class ResultDescription(
        val blacklistedAddresses: Set<String>,
    )

    companion object {
        private const val CHECKER_PATH = "/checkers/symbolic_transfer.fc"
        private const val TLB_PATH = "/checkers/symbolic_transfer_scheme.json"
    }
}

private const val MSG_ADDR_MAIN_PART_LENGTH = 256
