package com.livrovivo.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object EditProfile : Screen("onboarding/edit")
    data object AddProfile : Screen("onboarding/add")
    data object Home : Screen("home")
    data object Creation : Screen("creation")
    data object Reader : Screen("reader/{storyId}") {
        fun createRoute(storyId: String): String = "reader/$storyId"
    }
    data object Paywall : Screen("paywall")
    data object ParentDashboard : Screen("parent_dashboard")
    data object Settings : Screen("settings")
}
