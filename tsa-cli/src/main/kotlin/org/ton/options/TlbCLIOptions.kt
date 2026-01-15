package org.ton.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import org.ton.TlbCompositeLabel
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo
import org.ton.tlb.readFromJson
import java.math.BigInteger
import java.nio.file.Path

class TlbCLIOptions : OptionGroup("TlB scheme options") {
    val tlbJsonPath by option("-t", "--tlb")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the parsed TL-B scheme.")

    val doNotPerformTlbChecks by option("--no-tlb-checks")
        .flag()
        .help("Turn off TL-B parsing checks")

    companion object {
        fun extractInputInfo(path: Path?): Map<BigInteger, TvmInputInfo> =
            if (path == null) {
                emptyMap()
            } else {
                val struct =
                    readFromJson(path, "InternalMsgBody") as? TlbCompositeLabel
                        ?: error("Couldn't parse `InternalMsgBody` structure from $path")
                val info =
                    TvmParameterInfo.SliceInfo(
                        TvmParameterInfo.DataCellInfo(
                            struct,
                        ),
                    )
                mapOf(BigInteger.ZERO to TvmInputInfo(mapOf(0 to info)))
            }
    }
}
