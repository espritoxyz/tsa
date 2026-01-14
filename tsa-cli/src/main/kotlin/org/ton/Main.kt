package org.ton

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.ton.commands.BocAnalysis
import org.ton.commands.CheckerAnalysis
import org.ton.commands.FiftAnalysis
import org.ton.commands.FuncAnalysis
import org.ton.commands.InterContractAnalysis
import org.ton.commands.TactAnalysis
import org.ton.commands.TestGeneration

class TonAnalysis : NoOpCliktCommand()

fun main(args: Array<String>) =
    TonAnalysis()
        .subcommands(
            TactAnalysis(),
            FuncAnalysis(),
            FiftAnalysis(),
            BocAnalysis(),
            TestGeneration(),
            CheckerAnalysis(),
            InterContractAnalysis(),
        ).main(args)
