package com.fiatlife.app.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

internal object MonthAnchor {
    fun startUpdates(scope: CoroutineScope, anchor: MutableStateFlow<Long>) {
        scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                anchor.value = now
                delay(millisUntilNextMonth(now))
            }
        }
    }

    fun millisUntilNextMonth(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis - now
    }
}
