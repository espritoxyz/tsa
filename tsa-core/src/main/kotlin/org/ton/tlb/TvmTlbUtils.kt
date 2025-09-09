package org.ton.tlb

import kotlinx.serialization.json.Json
import org.ton.TlbLabel
import java.nio.file.Path

fun readFromJson(
    path: Path,
    name: String,
    onlyBasicAddresses: Boolean = false,
): TlbLabel? {
    val text = path.toFile().readText()
    val defs = Json.decodeFromString<List<TvmTlbTypeDefinition>>(text)
    val transformer = TvmTlbTransformer(defs, onlyBasicAddresses)
    val def = defs.first { it.name == name }
    return transformer.transformTypeDefinition(def)
}
