import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter

private const val TOLK_PATH_IN_TEST_RESOURCES = "tsa-test/src/test/resources/imports/tsa_functions.tolk"
private val pathsForTsaFunctionsTolk = listOf(TOLK_PATH_IN_TEST_RESOURCES).map(::Path)

private const val TOLK_MAX_PARAMETERS = 10

private fun typeParamLetters(count: Int): List<Char> = ('A'..'Z').take(count)

private fun inGenericBracketsIfNotEmpty(typeParams: List<Char>): String =
    if (typeParams.isEmpty()) "" else typeParams.joinToString(prefix = "<", postfix = ">")

fun main() {
    val prefix =
        """
        // generated

        // auxiliary functions
        """.trimIndent()

    val returnWildcardAuxiliaryFunctions =
        List(TOLK_MAX_PARAMETERS) { i ->
            // e.g. "@pure fun return3<A, B, C>(): (A, B, C)"
            val params = i + 1
            val typeParams = typeParamLetters(params)
            val generics = inGenericBracketsIfNotEmpty(typeParams)
            val returnType = typeParams.joinToString(prefix = "(", postfix = ")")
            "@pure\nfun return$params$generics(): $returnType asm \"NOP\";"
        }.joinToString(separator = "\n\n")

    val tsaAnyDefinition =
        """
        struct TsaAny{ dummy: int; };
        
        fun wrap<T>(x: T): TsaAny asm "NOP";
        fun unwrap<T>(x: TsaAny): T asm "NOP";
        """.trimIndent()

    val apiFunctions =
        """
        // API functions

        @method_id(1)
        fun tsaForbidFailures() {
            // do nothing
        }

        @method_id(2)
        fun tsaAllowFailures() {
            // do nothing
        }

        @method_id(3)
        fun tsaAssert(condition: int) {
            // do nothing
        }

        @method_id(4)
        fun tsaAssertNot(condition: int) {
            // do nothing
        }

        // @method_id(5)
        fun tsaFetchValue<A>(value: A, valueId: int) {
            // do nothing
        }

        @method_id(6)
        fun tsaSendInternalMessage(contractId: int, inputId: int) {
            // do nothing
        }

        @method_id(7)
        fun tsaGetC4(contractId: int): cell {
            return return1();
        }

        @method_id(8)
        fun tsaSendExternalMessage(contractId: int, inputId: int) {
            // do nothing
        }

        @method_id(9)
        fun tsaGetBalance(contractId: int): int {
            return return1();
        }

        // this function shouldn't be used while executing contract [contractId] (inside checker handlers)
        @method_id(10)
        fun tsaSetC4(contractId: int, value: cell) {
            // do nothing
        }

        @method_id(11)
        fun tsaMakeAddressRandom(address: address) {
            // do nothing
        }

        @method_id(12)
        fun tsaMakeSliceIndependentFromRandomAddresses(independentSlice: slice) {
            // do nothing
        }

        @method_id(13)
        fun tsaInputWasAccepted(inputId: int): int {
            return return1();
        }

        @method_id(14)
        fun tsaSendImplMessageWithBody(body: slice, contractId: int, inputId: int) {
            // do nothing
        }

        @method_id(15)
        fun tsaSendExternalMessageWithBody(body: slice, contractId: int, inputId: int) {
            // do nothing
        }
        """.trimIndent()

    val mkSymbolicApiFunctions =
        """
        // making symbolic values API functions

        @method_id(100)
        fun tsaMkInt(bits: int, signed: int): int {
            return return1();
        }
        """.trimIndent()

    val callFunctions = generateTolkCallFunctions()

    val code =
        listOf(
            prefix,
            returnWildcardAuxiliaryFunctions,
            tsaAnyDefinition,
            apiFunctions,
            mkSymbolicApiFunctions,
            callFunctions,
        ).joinToString(separator = "\n\n")

    pathsForTsaFunctionsTolk.forEach { path ->
        path.bufferedWriter().use {
            it.append(code)
        }
    }
}

private fun generateTolkCallFunctions(): String {
    val functions = mutableListOf<String>()

    for (retParams in 0..TOLK_MAX_PARAMETERS) {
        for (putParams in 0..TOLK_MAX_PARAMETERS) {
            val methodId = 10000 + retParams * 100 + putParams
            functions += generateApiCallAndImplCallFunctions(retParams, putParams, methodId)
        }
    }

    return functions.joinToString(
        prefix = "// calling methods functions\n\n",
        separator = "\n\n",
    )
}

private fun generateApiCallAndImplCallFunctions(
    retParams: Int,
    inputParams: Int,
    methodId: Int,
): String {
    val internalFn =
        generateImplGetterCall(inputParams, retParams, methodId)
    val wrapperFn =
        generateApiGetterCall(retParams, inputParams)

    return "$internalFn\n\n$wrapperFn"
}

private fun String.indented(): String = " ".repeat(4) + this

/**
 * Typical example:
 * ```
 * fun tsaCallGetterIn1Ret1<A, B>(p0: B, idContract: int, idMethod: int): (A) {
 *     val result = tsaCallGetterIn1Ret1Impl(wrap(p0), idContract, idMethod);
 *     return (unwrap<A>(result.0));
 * }
 * ```
 */
private fun generateApiGetterCall(
    retParams: Int,
    inputParams: Int,
): String {
    val retTypeParams = typeParamLetters(retParams)
    val allTypeParams = typeParamLetters(retParams + inputParams)
    val inputTypeParams = allTypeParams.drop(retParams)
    val generics = inGenericBracketsIfNotEmpty(allTypeParams)

    val getterApiParams =
        inputTypeParams
            .mapIndexed { index, paramType -> "p$index: $paramType, " }
            .joinToString(separator = "")

    val getterApiRetType =
        if (retParams > 0) {
            ": " + retTypeParams.joinToString(prefix = "(", postfix = ")")
        } else {
            ""
        }

    val wrappedArgs =
        (0 until inputParams)
            .joinToString(separator = "") { "wrap(p$it), " }

    val internalCall = "tsaCallGetterIn${inputParams}Ret${retParams}Impl(${wrappedArgs}idContract, idMethod)"

    val body =
        if (retParams == 0) {
            internalCall.indented()
        } else {
            val unwrappedFields = (0 until retParams).joinToString { "unwrap<${retTypeParams[it]}>(result.$it)" }
            buildString {
                appendLine("val result = $internalCall;".indented())
                appendLine("return ($unwrappedFields);".indented())
            }
        }

    val wrapperFn =
        """
        |fun tsaCallGetterIn${inputParams}Ret$retParams$generics(${getterApiParams}idContract: int, idMethod: int)$getterApiRetType {
        |$body
        |}
        """.trimMargin()
    return wrapperFn
}

/**
 * Typical example (for 1 in 2 out):
 * ```
 * fun tsaCallGetterIn1Ret2<A, B, C>(p0: C, idContract: int, idMethod: int): (A, B) {
 *     val result = tsaCallGetterIn1Ret2Impl(wrap(p0), idContract, idMethod);
 *     return (unwrap<A>(result.0), unwrap<B>(result.1));
 * }
 * ```
 */
private fun generateImplGetterCall(
    inputParams: Int,
    retParams: Int,
    methodId: Int,
): String {
    val internalParams = (0 until inputParams).joinToString(separator = "") { "p$it: TsaAny, " }

    val internalReturnType =
        if (retParams > 0) {
            ": " + (0 until retParams).joinToString(prefix = "(", postfix = ")") { "TsaAny" }
        } else {
            ""
        }

    val internalBody =
        if (retParams > 0) {
            "return return$retParams();"
        } else {
            "// do nothing"
        }.indented()

    val internalFn =
        """
        |@method_id($methodId)
        |fun tsaCallGetterIn${inputParams}Ret${retParams}Impl(${internalParams}idContract: int, idMethod: int)$internalReturnType {
        |$internalBody
        |}
        """.trimMargin()
    return internalFn
}
