package org.ton

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.usvm.machine.state.ContractId

@Serializable
data class TvmContractHandlers(
    val id: ContractId,
    val handlers: List<TvmHandlerDestinations>,
)

@Serializable
data class TvmHandlerDestinations(
    val op: String,
    val destinations: List<ContractId>,
)

fun communicationSchemeFromJson(json: String): Map<ContractId, TvmContractHandlers> =
    Json.decodeFromString<List<TvmContractHandlers>>(json).associateBy { it.id }
