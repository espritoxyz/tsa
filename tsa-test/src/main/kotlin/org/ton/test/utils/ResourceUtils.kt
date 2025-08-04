package org.ton.test.utils

import java.nio.file.Path
import kotlin.io.path.Path

const val FUNC_STDLIB_PATH = "/imports"
const val FIFT_STDLIB_PATH = "/fiftstdlib"

val FUNC_STDLIB_RESOURCE: Path = object {}.javaClass.getResource(FUNC_STDLIB_PATH)?.path?.let { Path(it) }
    ?: error("Cannot find func stdlib in $FUNC_STDLIB_PATH")

val FIFT_STDLIB_RESOURCE: Path = object {}.javaClass.getResource(FIFT_STDLIB_PATH)?.path?.let { Path(it) }
    ?: error("Cannot find fift stdlib in $FIFT_STDLIB_PATH")
