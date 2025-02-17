import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.math.max

private val pathInSafetyPropertiesMainResources = Path("tsa-safety-properties/src/main/resources/imports/tsa_functions.fc")
private val pathInSafetyPropertiesTestResources = Path("tsa-safety-properties/src/test/resources/imports/tsa_functions.fc")
private val pathsForTsaFunctions = listOf(
    pathInSafetyPropertiesMainResources,
    pathInSafetyPropertiesTestResources,
)

private const val MAX_PARAMETERS = 10

fun main() {
    val prefix = """
        ;; generated

        ;; auxiliary functions
    """.trimIndent()

    val auxiliaryFunctions = List(MAX_PARAMETERS) { i ->
        val params = i + 1
        val typeParams = ('A'..'Z').take(params).joinToString()
        "forall $typeParams -> ($typeParams) return_$params() asm \"NOP\";"
    }.joinToString(separator = "\n")

    val firstAPIFunctions = """
        ;; API functions

        () tsa_forbid_failures() impure method_id(1) {
            ;; do nothing
        }

        () tsa_allow_failures() impure method_id(2) {
            ;; do nothing
        }

        () tsa_assert(int condition) impure method_id(3) {
            ;; do nothing
        }

        () tsa_assert_not(int condition) impure method_id(4) {
            ;; do nothing
        }

        forall A -> () tsa_fetch_value(A value, int value_id) impure method_id(5) {
            ;; do nothing
        }
    """.trimIndent()

    val callFunctions = List(MAX_PARAMETERS + 1) { retParams ->
        List(MAX_PARAMETERS + 1) { putParams ->
            val typeParams = ('A'..'Z').take(max(retParams + putParams, 1))
            val retTypeParams = typeParams.take(retParams).joinToString(prefix = "(", postfix = ")")
            val putParamsRendered = typeParams.takeLast(putParams).mapIndexed { index, paramType ->
                "$paramType p$index, "
            }.joinToString(separator = "")
            val methodId = 10000 + retParams * 100 + putParams
            val returnStmt = if (retParams > 0) {
                "return return_$retParams();"
            } else {
                ";; do nothing"
            }
            """
                forall ${typeParams.joinToString()} -> $retTypeParams tsa_call_${retParams}_$putParams(${putParamsRendered}int id_contract, int id_method) impure method_id($methodId) {
                    $returnStmt
                }
            """.trimIndent()
        }
    }.flatten().joinToString(separator = "\n\n")

    val code = prefix + "\n\n" + auxiliaryFunctions + "\n\n" + firstAPIFunctions + "\n\n" + callFunctions

    pathsForTsaFunctions.forEach { path ->
        path.bufferedWriter().use {
            it.append(code)
        }
    }
}