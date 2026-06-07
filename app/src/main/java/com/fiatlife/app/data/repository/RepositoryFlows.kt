package com.fiatlife.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Run upstream flow work (e.g. JSON decode) off the main thread. */
internal fun <T> Flow<T>.decodeOnBackground(): Flow<T> = flowOn(Dispatchers.Default)

const val VIEWMODEL_SUBSCRIBE_TIMEOUT_MS = 5_000L

/** Stop upstream collection shortly after the UI unsubscribes (tab switched away). */
fun <T> Flow<T>.stateWhileSubscribed(
    scope: CoroutineScope,
    initialValue: T
): StateFlow<T> = stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(VIEWMODEL_SUBSCRIBE_TIMEOUT_MS),
    initialValue = initialValue
)
