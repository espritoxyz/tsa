package org.ton.test.utils

import org.usvm.machine.getResourcePath
import java.nio.file.Path

const val FIFT_STDLIB_PATH = "/fiftstdlib"

val FIFT_STDLIB_RESOURCE: Path = getResourcePath(object {}.javaClass, FIFT_STDLIB_PATH)
