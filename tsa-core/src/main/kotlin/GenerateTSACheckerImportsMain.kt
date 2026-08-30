import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter

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

private const val TOLK_PATH_IN_TEST_RESOURCES = "tsa-test/src/test/resources/imports/tsa_functions.tolk"
private val pathsForTsaFunctionsTolk = listOf(TOLK_PATH_IN_TEST_RESOURCES).map(::Path)

fun main() {
    val funcCheckerFile = generateFuncCheckerFile()
    pathsForTsaFunctions.forEach { path ->
        path.bufferedWriter().use {
            it.append(funcCheckerFile)
        }
    }

    val tolkCheckerFile = generateTolkCheckerFile()
    pathsForTsaFunctionsTolk.forEach { path ->
        path.bufferedWriter().use {
            it.append(tolkCheckerFile)
        }
    }
}
