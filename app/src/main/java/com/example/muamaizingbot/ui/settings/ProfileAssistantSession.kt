package com.example.muamaizingbot.ui.settings

/**
 * Holds in-progress assistant draft across SpotPicker / elf-zone navigation
 * (NavHost disposes the wizard composable while those screens are open).
 */
object ProfileAssistantSession {
    var draft: AssistantDraft = AssistantDraft()
    var step: Int = 0
    var active: Boolean = false

    fun begin(initial: AssistantDraft = AssistantDraft()) {
        draft = initial
        step = 0
        active = true
    }

    fun clear() {
        draft = AssistantDraft()
        step = 0
        active = false
    }
}
