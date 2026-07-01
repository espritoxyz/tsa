package org.usvm.machine.ps

import kotlin.time.Duration

interface TvmExtendingTimeStrategy {
    fun requestMoreTime(): Boolean

    fun notifyAboutTimeExtension(newTimeout: Duration)
}
