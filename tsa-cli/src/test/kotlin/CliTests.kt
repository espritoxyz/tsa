import org.ton.main
import org.usvm.machine.getResourcePath
import kotlin.test.Test

class CliTests {
    val fiftStdLibPath = getResourcePath(object {}.javaClass, "fiftstdlib").toAbsolutePath()

    @Test
    fun `walletV5 cli tests`() {
        val x = getResourcePath(object {}.javaClass, "func-error-detection/main.fc").toAbsolutePath()
        val args = "func -i $x --fift-std $fiftStdLibPath --method 0".split(" ").toTypedArray()
        main(args)
    }
}
