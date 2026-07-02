package org.usvm.machine.ps

import kotlin.time.Duration

interface TvmExtendingTimeStrategy {
    fun requestMoreTime(): TimeExtension

    fun notifyAboutTimeExtension(newTimeout: Duration)

    enum class TimeExtension {
        NONE,
        ONE_STEP,
        TIME_STEP,
    }
}
