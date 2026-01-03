package com.abuhrov.openword.app.navigation

import kotlinx.serialization.Serializable

interface Route {

    @Serializable
    data object Home : Route
}