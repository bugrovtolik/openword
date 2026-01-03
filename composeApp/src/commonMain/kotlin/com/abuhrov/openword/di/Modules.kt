package com.abuhrov.openword.di

import com.abuhrov.openword.home.presentation.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

//TODO use for settings storage impl ?
//expect val platformModule: Module

val sharedModule = module {

    viewModelOf(::HomeViewModel)
}