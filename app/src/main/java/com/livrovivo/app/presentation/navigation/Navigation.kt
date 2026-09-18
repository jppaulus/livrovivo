package com.livrovivo.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object EditProfile : Screen("onboarding/edit")

    /** Cadastro de um irmão, a partir da Área dos Pais. */
    data object AddChild : Screen("onboarding/new")
    data object Home : Screen("home")
    data object Creation : Screen("creation")
    data object Reader : Screen("reader/{storyId}") {
        fun createRoute(storyId: String): String = "reader/$storyId"
    }
    data object Paywall : Screen("paywall")

    /** Álbum de figurinhas da criança [childId]. */
    data object Album : Screen("album/{childId}") {
        fun createRoute(childId: String): String = "album/$childId"
    }

    /** Ritual de dormir da criança [childId]. */
    data object Bedtime : Screen("bedtime/{childId}") {
        fun createRoute(childId: String): String = "bedtime/$childId"
    }
    data object ParentDashboard : Screen("parent_dashboard")
    data object Settings : Screen("settings")
}
