import org.usvm.machine.interpreter.ALLOW_FAILURES_METHOD_ID
import org.usvm.machine.interpreter.ASSERT_METHOD_ID
import org.usvm.machine.interpreter.ASSERT_NOT_METHOD_ID
import org.usvm.machine.interpreter.FETCH_VALUE_METHOD_ID
import org.usvm.machine.interpreter.FORBID_FAILURES_METHOD_ID
import org.usvm.machine.interpreter.GET_BALANCE_METHOD_ID
import org.usvm.machine.interpreter.GET_C4_METHOD_ID
import org.usvm.machine.interpreter.INPUT_WAS_ACCEPTED_METHOD_ID
import org.usvm.machine.interpreter.MAKE_ADDRESS_RANDOM_METHOD_ID
import org.usvm.machine.interpreter.MAKE_SLICE_INDEPENDENT_METHOD_ID
import org.usvm.machine.interpreter.MK_SYMBOLIC_INT_METHOD_ID
import org.usvm.machine.interpreter.SEND_EXTERNAL_MESSAGE_METHOD_ID
import org.usvm.machine.interpreter.SEND_EXTERNAL_MESSAGE_WITH_BODY_METHOD_ID
import org.usvm.machine.interpreter.SEND_INTERNAL_MESSAGE_METHOD_ID
import org.usvm.machine.interpreter.SEND_INTERNAL_MESSAGE_WITH_BODY_METHOD_ID
import org.usvm.machine.interpreter.SET_C4_METHOD_ID
import kotlin.math.max

internal const val MAX_PARAMETERS = 10
private const val DOUBLE_SEPARATOR = "\n\n"

internal fun generateFuncCheckerFile(): String {
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

        () tsa_forbid_failures() impure method_id($FORBID_FAILURES_METHOD_ID) {
            ;; do nothing
        }

        () tsa_allow_failures() impure method_id($ALLOW_FAILURES_METHOD_ID) {
            ;; do nothing
        }

        () tsa_assert(int condition) impure method_id($ASSERT_METHOD_ID) {
            ;; do nothing
        }

        () tsa_assert_not(int condition) impure method_id($ASSERT_NOT_METHOD_ID) {
            ;; do nothing
        }

        forall A -> () tsa_fetch_value(A value, int value_id) impure method_id($FETCH_VALUE_METHOD_ID) {
            ;; do nothing
        }

        () tsa_send_internal_message(int contract_id, int input_id) impure method_id($SEND_INTERNAL_MESSAGE_METHOD_ID) {
            ;; do nothing
        }

        cell tsa_get_c4(int contract_id) impure method_id($GET_C4_METHOD_ID) {
            return return_1();
        }

        () tsa_send_external_message(int contract_id, int input_id) impure method_id($SEND_EXTERNAL_MESSAGE_METHOD_ID) {
            ;; do nothing
        }

        int tsa_get_balance(int contract_id) impure method_id($GET_BALANCE_METHOD_ID) {
            return return_1();
        }

        ;; this function shouldn't be used while executing contract [contract_id] (inside checker handlers)
        () tsa_set_c4(int contract_id, cell value) impure method_id($SET_C4_METHOD_ID) {
            ;; do nothing
        }

        () tsa_make_address_random(slice address) impure method_id($MAKE_ADDRESS_RANDOM_METHOD_ID) {
            ;; do nothing
        }
        
        () tsa_make_slice_independent_from_random_addresses(slice independent_slice) impure method_id($MAKE_SLICE_INDEPENDENT_METHOD_ID) {
            ;; do nothing
        }
        
        int tsa_input_was_accepted(int input_id) impure method_id($INPUT_WAS_ACCEPTED_METHOD_ID) {
            return return_1();
        }
        
        () tsa_send_internal_message_with_body(slice body, int contract_id, int input_id) impure method_id($SEND_INTERNAL_MESSAGE_WITH_BODY_METHOD_ID) {
            ;; do nothing
        }
        
        () tsa_send_external_message_with_body(slice body, int contract_id, int input_id) impure method_id($SEND_EXTERNAL_MESSAGE_WITH_BODY_METHOD_ID) {
            ;; do nothing
        }
        """.trimIndent()

    val mkSymbolicApiFunctions =
        """
        ;; making symbolic values API functions

        int tsa_mk_int(int bits, int signed) impure method_id($MK_SYMBOLIC_INT_METHOD_ID) {
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

    return listOf(
        prefix,
        auxiliaryFunctions,
        firstApiFunctions,
        mkSymbolicApiFunctions,
        callFunctions,
    ).joinToString(separator = DOUBLE_SEPARATOR)
}
