package com.copilka.app.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val REMOVE_ADS = "remove_ads"
    const val ARG_TYPE = "type"
    const val ADD = "add/{$ARG_TYPE}"
    const val ARG_TOOL_ID = "toolId"
    const val TOOL = "tool/{$ARG_TOOL_ID}"
    fun tool(id: String) = "tool/$id"
    fun add(type: String) = "add/$type"
}
