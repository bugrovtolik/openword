import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
    kotlin("plugin.serialization") version "2.3.0"
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
                sourceMaps = false
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

            implementation(libs.compose.navigation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            
            api(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android)
            implementation(libs.ktor.client.android)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native)
            implementation(libs.ktor.client.darwin)
        }

        jsMain.dependencies {
            implementation(libs.sqldelight.web)
            implementation(libs.ktor.client.js)
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqldelight.get()))
            implementation(npm("sql.js", "1.13.0"))
        }
    }
}

sqldelight {
    databases {
        register("BibleDb") {
            packageName = "com.abuhrov.openword.db"
            generateAsync = true
            srcDirs.setFrom("src/commonMain/sqldelight/bibleDb")
        }
        register("LexiconDb") {
            packageName = "com.abuhrov.openword.db"
            generateAsync = true
            srcDirs.setFrom("src/commonMain/sqldelight/lexiconDb")
        }
        register("CommentaryDb") {
            packageName = "com.abuhrov.openword.db"
            generateAsync = true
            srcDirs.setFrom("src/commonMain/sqldelight/commentaryDb")
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