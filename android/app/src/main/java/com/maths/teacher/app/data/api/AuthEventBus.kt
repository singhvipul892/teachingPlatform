package com.maths.teacher.app.data.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide bus for authentication events raised from the network layer.
 *
 * The OkHttp interceptor runs off the main thread and cannot navigate, so when it
 * detects an expired/invalid token (HTTP 401 on an authenticated request) it emits
 * here. The UI layer collects [events] and performs logout + navigation to login.
 */
object AuthEventBus {

    /** Buffered so an emission from a background thread is never dropped if there is no active collector yet. */
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /** Signal that the session is no longer valid and the user must be logged out. */
    fun notifyUnauthorized() {
        _events.tryEmit(Unit)
    }
}
