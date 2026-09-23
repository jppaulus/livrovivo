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

    // Trilha da Leitura
    data object LiteracyTrail : Screen("literacy_trail")
    data object LiteracyPhases : Screen("literacy_phases/{moduleId}") {
        fun createRoute(moduleId: String): String = "literacy_phases/$moduleId"
    }
    data object LiteracyActivity : Screen("literacy_activity/{phaseId}") {
        fun createRoute(phaseId: String): String = "literacy_activity/$phaseId"
    }
    data object LiteracyResult : Screen("literacy_result/{phaseId}/{stars}?newBook={newBook}") {
        /** [newBook]: livro "Eu leio" que a criança acabou de ganhar (mostra o aviso). */
        fun createRoute(phaseId: String, stars: Int, newBook: String? = null): String =
            "literacy_result/$phaseId/$stars" + (newBook?.let { "?newBook=$it" } ?: "")
    }
    data object EuLeioReader : Screen("literacy_book/{storyId}") {
        fun createRoute(storyId: String): String = "literacy_book/$storyId"
    }
}
