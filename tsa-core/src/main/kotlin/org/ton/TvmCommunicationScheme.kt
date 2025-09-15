package org.ton

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.usvm.machine.state.ContractId

// TODO: refactor communication scheme, so that it is more usable

@Serializable
data class TvmContractHandlers(
    val id: ContractId,
    val handlers: List<TvmHandlerDestinations>,
)

@Serializable
sealed interface DestinationDescription

@Serializable
@SerialName("linear")
data class LinearDestinations(
    val destinations: List<ContractId>,
) : DestinationDescription

@Serializable
@SerialName("out_opcodes")
data class OpcodeToDestination(
    val outOpcodeToDestination: Map<String, ContractId>,
    val other: ContractId? = null,
) : DestinationDescription

@Serializable
data class TvmHandlerDestinations(
    val op: String,
    val destinations: List<ContractId>,
)

fun communicationSchemeFromJson(json: String): Map<ContractId, TvmContractHandlers> =
    Json.decodeFromString<List<TvmContractHandlers>>(json).associateBy { it.id }

fun main() {

}
