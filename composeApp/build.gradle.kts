import app.cash.sqldelight.gradle.SqlDelightDatabase
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(25)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    val jsResourcesPath = project.file("src/jsMain/resources").path
    js(IR) {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: DevServer()).apply {
                    static(jsResourcesPath)
                }
            }
        }
        binaries.executable()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }

        // Renamed to jsMain (was wasmJsMain)
        jsMain.dependencies {
            implementation(libs.sqldelight.web)
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqldelight.get()))
            implementation(npm("sql.js", "1.13.0"))
        }
    }
}

sqldelight {
    databases {
        register("BibleDb") {
            packageName.set("com.abuhrov.openword.db")
            generateAsync.set(true)
            srcDirs.setFrom("src/commonMain/sqldelight/bibleDb")
        }
        register("LexiconDb") {
            packageName.set("com.abuhrov.openword.db")
            generateAsync.set(true)
            srcDirs.setFrom("src/commonMain/sqldelight/lexiconDb")
        }
    }
}

android {
    namespace = "com.abuhrov.openword"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abuhrov.openword"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}