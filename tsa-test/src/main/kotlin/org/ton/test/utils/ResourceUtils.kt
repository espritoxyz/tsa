package org.ton.test.utils

import org.usvm.machine.getResourcePath
import java.nio.file.Path

const val FIFT_STDLIB_PATH = "/fiftstdlib"
const val FIFT_STDLIB_V12_PATH = "/fiftstdlib-v12"

val FIFT_STDLIB_RESOURCE: Path = getResourcePath(object {}.javaClass, FIFT_STDLIB_PATH)
val FIFT_STDLIB_V12_RESOURCE: Path = getResourcePath(object {}.javaClass, FIFT_STDLIB_V12_PATH)

enum class FiftStdlibVersion {
    DEFAULT,
    V12,
}
