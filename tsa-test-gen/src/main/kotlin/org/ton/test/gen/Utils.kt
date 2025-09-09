package org.ton.test.gen

import org.ton.boc.BagOfCells
import org.ton.test.gen.dsl.render.TsRenderedTest
import org.ton.test.gen.dsl.render.TsRenderer
import org.usvm.machine.TvmContext.Companion.CONFIG_KEY_LENGTH
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.transformTestDictCellIntoCell
import java.io.File
import java.math.BigInteger
import java.nio.file.Files.createDirectories
import java.nio.file.Path

fun String.binaryToHex(): String = BigInteger(this, 2).toString(16)

fun String.binaryToUnsignedBigInteger(): BigInteger = BigInteger("0$this", 2)

fun String.binaryToSignedDecimal(): String {
    val binaryString = this

    val signBit = binaryString.first().digitToInt()
    val sign = BigInteger.valueOf(signBit.toLong()).shiftLeft(length - 1)
    val resultBigInteger = BigInteger("0" + binaryString.drop(1), 2) - sign

    return resultBigInteger.toString(10)
}

fun writeRenderedTest(
    projectPath: Path,
    test: TsRenderedTest,
): List<File> {
    val wrapperFolder = projectPath.resolve(TsRenderer.WRAPPERS_DIR_NAME)
    val testsFolder = projectPath.resolve(TsRenderer.TESTS_DIR_NAME)

    createDirectories(wrapperFolder)
    createDirectories(testsFolder)

    val result = mutableListOf<File>()

    val testsFile = testsFolder.resolve(test.fileName).toFile()
    testsFile.writeText(test.code)
    result += testsFile

    test.wrappers.forEach { (fileName, code) ->
        val wrapperFile = wrapperFolder.resolve(fileName).toFile()
        wrapperFile.writeText(code)
        result += wrapperFile
    }

    return result
}

@OptIn(ExperimentalStdlibApi::class)
fun transformTestConfigIntoHex(config: TvmTestDictCellValue): String {
    val keyLength = config.keyLength
    require(keyLength == CONFIG_KEY_LENGTH) {
        "Unexpected config dict key length: $keyLength"
    }
    val configCell = transformTestDictCellIntoCell(config)
    val configBoc = BagOfCells(configCell)

    return configBoc.toByteArray().toHexString()
}
