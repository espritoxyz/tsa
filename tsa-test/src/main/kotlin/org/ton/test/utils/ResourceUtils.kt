package org.ton.test.utils

import java.nio.file.Path
import kotlin.io.path.Path

const val FIFT_STDLIB_PATH = "/fiftstdlib"

val FIFT_STDLIB_RESOURCE: Path = object {}.javaClass.getResource(FIFT_STDLIB_PATH)?.path?.let { Path(it) }
    ?: error("Cannot find fift stdlib in $FIFT_STDLIB_PATH")
