package com.abuhrov.openword

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.abuhrov.openword.db.AndroidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidContext.applicationContext = applicationContext

        setContent {
            App()
        }
    }
}