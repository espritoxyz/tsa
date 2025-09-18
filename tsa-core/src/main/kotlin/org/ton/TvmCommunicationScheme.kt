package org.ton

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.usvm.machine.TvmContext
import org.usvm.machine.state.ContractId

@Serializable
data class TvmContractHandlers(
    val id: ContractId,
    val inOpcodeToDestination: Map<String, DestinationDescription>,
    /**
     * Where to send messages if input opcode was not given in [inOpcodeToDestination] map.
     * */
    val other: DestinationDescription? = null,
) {
    init {
        inOpcodeToDestination.keys.forEach {
            check(it.length == TvmContext.OP_BYTES.toInt()) {
                "Unexpected opcode length: $it"
            }
        }
    }
}

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
    /**
     * If several values for one key are given, fork on each variant.
     * */
    val outOpcodeToDestination: Map<String, List<ContractId>>,
    /**
     * Where to send messages if their opcode not given in [outOpcodeToDestination] map.
     * If several values are given, fork on each variant.
     * If no values are given, leave the message unreceived.
     * */
    val other: List<ContractId> = emptyList(),
) : DestinationDescription {
    init {
        outOpcodeToDestination.keys.forEach {
            check(it.length == TvmContext.OP_BYTES.toInt()) {
                "Unexpected opcode length: $it"
            }
        }
    }
}

fun communicationSchemeFromJson(json: String): Map<ContractId, TvmContractHandlers> =
    Json.decodeFromString<List<TvmContractHandlers>>(json).associateBy { it.id }
