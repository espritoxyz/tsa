package org.ton.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option

class ContractProperties : OptionGroup("Contract properties") {
    val contractData by option("-d", "--data").help("The serialized contract persistent data")
}
