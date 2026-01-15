package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import io.ksmt.utils.uncheckedCast
import org.ton.common.performAnalysis
import org.ton.options.AnalysisOptions
import org.ton.options.ContractProperties
import org.ton.options.ContractSources
import org.ton.options.ContractType
import org.ton.options.FiftOptions
import org.ton.options.SinglePath
import org.ton.options.TactOptions
import org.ton.options.TactPath
import org.ton.options.TlbCLIOptions
import org.ton.options.analysisTargetOption
import org.ton.options.contractSourcesOption
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.gen.generateTests
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TactAnalyzer
import java.nio.file.Path
import kotlin.io.path.relativeTo

class TestGeneration : CliktCommand(name = "test-gen", help = "Options for test generation for FunC projects") {
    private val projectPath by option("-p", "--project")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .required()
        .help("The path to the sandbox project")

    private val pathOptionDescriptor = option().path(canBeFile = true, canBeDir = false)
    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)
    private val contractSources: ContractSources by contractSourcesOption(pathOptionDescriptor, typeOptionDescriptor) {
        require(!it.isAbsolute) {
            "File path must be relative (to project path)"
        }
    }.required()

    private val contractType by lazy {
        when (val optionSources = contractSources) {
            is SinglePath -> optionSources.type
            is TactPath -> ContractType.Tact
        }
    }

    private val contractProperties by ContractProperties()

    // TODO: make these optional (only for FunC)
    private val fiftOptions by FiftOptions()
    private val tactOptions by TactOptions()

    private val tlbOptions by TlbCLIOptions()

    private val analysisOptions by AnalysisOptions()

    private fun toAbsolutePath(relativePath: Path) = projectPath.resolve(relativePath).normalize()

    private val target by analysisTargetOption()

    override fun run() {
        val tactAnalyzer = TactAnalyzer(tactOptions.tactExecutable)

        val (sourcesAbsolutePath, sourcesRelativePath) =
            when (val optionSources = contractSources) {
                is SinglePath -> optionSources.path.let { toAbsolutePath(it) to it }

                is TactPath -> {
                    val configAbsolutePath = toAbsolutePath(optionSources.tactPath.configPath)
                    val sourcesAbsolutePath = optionSources.tactPath.copy(configPath = configAbsolutePath)
                    val bocAbsolutePath = tactAnalyzer.getBocAbsolutePath(sourcesAbsolutePath)
                    val bocRelativePath = bocAbsolutePath.relativeTo(projectPath)

                    sourcesAbsolutePath to bocRelativePath
                }
            }
        val analyzer =
            when (contractType) {
                ContractType.Func -> FuncAnalyzer(fiftOptions.fiftStdlibPath)
                ContractType.Boc -> BocAnalyzer
                ContractType.Tact -> tactAnalyzer

                ContractType.Fift -> error("Fift is not supported")
            }

        val results =
            performAnalysis(
                analyzer.uncheckedCast(),
                sourcesAbsolutePath,
                contractProperties.contractData,
                target,
                tlbOptions,
                analysisOptions,
            )

        val testGenContractType =
            when (contractType) {
                ContractType.Func -> TsRenderer.ContractType.Func
                else -> TsRenderer.ContractType.Boc
            }

        generateTests(
            results,
            projectPath,
            sourcesRelativePath,
            testGenContractType,
            useMinimization = true,
        )
    }
}
