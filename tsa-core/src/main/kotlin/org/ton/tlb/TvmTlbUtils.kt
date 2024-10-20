package org.ton.tlb

import kotlinx.serialization.json.Json
import org.ton.TvmDataCellLabel
import org.ton.TvmDataCellStructure
import java.nio.file.Path

fun readFromJson(path: Path, name: String): TvmDataCellLabel? {
    val text = path.toFile().readText()
    val defs = Json.decodeFromString<List<TvmTlbTypeDefinition>>(text)
    val transformer = TvmTlbTransformer(defs)
    val def = defs.first { it.name == name }
    return transformer.transformTypeDefinition(def)
}