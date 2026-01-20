package org.ton

import io.ktor.util.encodeBase64
import org.ton.boc.BagOfCells
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.transformTestCellIntoCell
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class CellAsFileContent(
    val cellText: String,
    val bocBinary: ByteArray,
    val bocHex: String,
    val bocBase64: String,
)

@OptIn(ExperimentalStdlibApi::class)
fun TvmTestCellValue.toCellAsFileContent(): CellAsFileContent {
    val cell = transformTestCellIntoCell(this)
    val cellText = cell.toString()
    val bocBinary = BagOfCells(cell).toByteArray()
    val bocHex = bocBinary.toHexString()
    val bocBase64 = bocBinary.encodeBase64()
    return CellAsFileContent(
        cellText,
        bocBinary,
        bocHex,
        bocBase64,
    )
}

/**
 * @param folder must be an existing folder
 */
fun CellAsFileContent.dumpCellToFolder(folder: java.nio.file.Path) {
    if (!folder.exists() || !folder.isDirectory()) {
        error("Path $folder does not exist")
    }

    val cellTextPath = folder / "cell-text.txt"
    val bocPath = folder / "cell.boc"
    val bocHexPath = folder / "boc-hex.txt"
    val bocBase64Path = folder / "box-base64.txt"
    cellTextPath.createFile().writeText(cellText, Charsets.UTF_8)
    bocPath.createFile().writeBytes(bocBinary)
    bocHexPath.createFile().writeText(bocHex, Charsets.UTF_8)
    bocBase64Path.createFile().writeText(bocBase64, Charsets.UTF_8)
}
