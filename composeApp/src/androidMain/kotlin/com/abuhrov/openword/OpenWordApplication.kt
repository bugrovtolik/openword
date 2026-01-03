package com.abuhrov.openword

import android.app.Application
import com.abuhrov.openword.di.initKoin
import org.koin.android.ext.koin.androidContext

class OpenWordApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@OpenWordApplication)
        }
    }
}