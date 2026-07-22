package com.billingps.aptv.utils

import com.billingps.aptv.models.AppNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotificationEventBus {
    private val _events = MutableSharedFlow<AppNotification>(extraBufferCapacity = 5)
    val events = _events.asSharedFlow()

    fun emit(notif: AppNotification) {
        _events.tryEmit(notif)
    }
}
