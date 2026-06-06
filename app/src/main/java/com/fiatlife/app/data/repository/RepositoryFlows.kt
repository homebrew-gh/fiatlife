package com.fiatlife.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/** Run upstream flow work (e.g. JSON decode) off the main thread. */
internal fun <T> Flow<T>.decodeOnBackground(): Flow<T> = flowOn(Dispatchers.Default)
