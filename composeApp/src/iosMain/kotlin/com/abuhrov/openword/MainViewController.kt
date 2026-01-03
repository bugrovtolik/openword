package com.abuhrov.openword

import androidx.compose.ui.window.ComposeUIViewController
import com.abuhrov.openword.app.App
import com.abuhrov.openword.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) {
    App()
}