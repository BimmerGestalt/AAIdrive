package me.hufman.androidautoidrive.carapp.assistant

interface AssistantController {
    fun getAssistants(): Set<AssistantAppInfo>

    fun triggerAssistant(assistant: AssistantAppInfo)
    fun supportsSettings(assistant: AssistantAppInfo): Boolean
    fun openSettings(assistant: AssistantAppInfo)
}
