package com.selfspace.lockzipas.service

import com.selfspace.lockzipas.model.CrackSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CrackSessionBus {
    private val mutableSession = MutableStateFlow(CrackSession())
    val session: StateFlow<CrackSession> = mutableSession

    fun update(session: CrackSession) {
        mutableSession.value = session
    }
}
