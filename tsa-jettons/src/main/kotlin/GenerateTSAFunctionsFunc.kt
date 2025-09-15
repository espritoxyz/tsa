import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.math.max

private const val PATH_IN_JETTON_RESOURCES = "tsa-jettons/src/main/resources/imports/tsa_functions.fc"
private const val PATH_IN_TEST_RESOURCES = "tsa-test/src/test/resources/imports/tsa_functions.fc"
private const val PATH_IN_SAFETY_PROPERTIES_EXAMPLES_TEST_RESOURCES =
    "tsa-safety-properties-examples/src/test/resources/imports/tsa_functions.fc"
private val pathsForTsaFunctions =
    listOf(
        PATH_IN_JETTON_RESOURCES,
        PATH_IN_SAFETY_PROPERTIES_EXAMPLES_TEST_RESOURCES,
        PATH_IN_TEST_RESOURCES,
        PATH_IN_SAFETY_PROPERTIES_EXAMPLES_TEST_RESOURCES,
    ).map(::Path)

private const val MAX_PARAMETERS = 10
private const val DOUBLE_SEPARATOR = "\n\n"

fun main() {
    val prefix =
        """
        ;; generated

        ;; auxiliary functions
        """.trimIndent()

    val auxiliaryFunctions =
        List(MAX_PARAMETERS) { i ->
            val params = i + 1
            val typeParams = ('A'..'Z').take(params).joinToString()
            "forall $typeParams -> ($typeParams) return_$params() asm \"NOP\";"
        }.joinToString(separator = "\n")

    val firstApiFunctions =
        """
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

        () tsa_send_internal_message(int contract_id, int input_id) impure method_id(6) {
            ;; do nothing
        }
        
        cell tsa_get_c4(int contract_id) impure method_id(7) {
            return return_1();
        }

        () tsa_send_external_message(int contract_id, int input_id) impure method_id(8) {
            ;; do nothing
        }
        """.trimIndent()

    val mkSymbolicApiFunctions =
        """
        ;; making symbolic values API functions
        
        int tsa_mk_int(int bits, int signed) impure method_id(100) {
            return return_1();
        }
        """.trimIndent()

    val callFunctions =
        List(MAX_PARAMETERS + 1) { retParams ->
            List(MAX_PARAMETERS + 1) { putParams ->
                val typeParams = ('A'..'Z').take(max(retParams + putParams, 1))
                val retTypeParams = typeParams.take(retParams).joinToString(prefix = "(", postfix = ")")
                val putParamsRendered =
                    typeParams
                        .takeLast(putParams)
                        .mapIndexed { index, paramType ->
                            "$paramType p$index, "
                        }.joinToString(separator = "")
                val methodId = 10000 + retParams * 100 + putParams
                val returnStmt =
                    if (retParams > 0) {
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
        }.flatten().joinToString(prefix = ";; calling methods functions$DOUBLE_SEPARATOR", separator = DOUBLE_SEPARATOR)

    val code =
        listOf(
            prefix,
            auxiliaryFunctions,
            firstApiFunctions,
            mkSymbolicApiFunctions,
            callFunctions,
        ).joinToString(separator = DOUBLE_SEPARATOR)

    pathsForTsaFunctions.forEach { path ->
        path.bufferedWriter().use {
            it.append(code)
        }
    }
}
