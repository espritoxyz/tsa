import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.usvm.report.runAnalysisAndCreateReport

class JettonWalletPropertiesAnalyzer : CliktCommand() {
    private val address by option("-a", "--address")
        .required()
        .help("TON address of the jetton master contract.")

    override fun run() {
        val report = runAnalysisAndCreateReport(address)
        val prettyJson = Json { prettyPrint = true }
        println(prettyJson.encodeToString(report))
    }
}

fun main(args: Array<String>) = JettonWalletPropertiesAnalyzer().main(args)
