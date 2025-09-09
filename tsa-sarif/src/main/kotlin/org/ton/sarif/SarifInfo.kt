package org.ton.sarif

import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent
import io.github.detekt.sarif4k.Version

internal object TsaSarifSchema {
    internal const val SCHEMA =
        "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json"
    internal val VERSION = Version.The210

    internal object TsaSarifTool {
        private val DRIVER =
            ToolComponent(
                name = "TSA", // TODO what name?
                organization = "Explyt" // TODO what organization?
            )

        internal val TOOL = Tool(driver = DRIVER)
    }

    internal object TsaSarifResult {
        internal val LEVEL = Level.Error
    }
}
