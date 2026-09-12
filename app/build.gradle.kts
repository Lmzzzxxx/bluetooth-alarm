import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.demo.btalarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.demo.btalarm"
        minSdk = 26
        // targetSdk 34: Android 15/16 tighten background FGS starts and add
        // extra mediaPlayback requirements that hurt alarm reliability.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // targetSdk 34 是刻意选的：Android 15/16 针对 35+ 收紧了两件对本应用致命的事 ——
        // BOOT_COMPLETED 拉起前台服务的限制，以及 mediaPlayback 前台服务的额外前置条件。
        // 闹钟类应用留在 34 上可靠性明显更好。
        disable += "OldTargetApi"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation("androidx.compose.ui:ui:1.8.2")
    implementation("androidx.compose.ui:ui-graphics:1.8.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.2")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.2")

    testImplementation("junit:junit:4.13.2")
}
