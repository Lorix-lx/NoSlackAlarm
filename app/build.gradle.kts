// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "lorix.noslackalarm"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "lorix.noslackalarm"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    androidResources {
        localeFilters += listOf("zh")
    }

    signingConfigs {
        create("release") {
            // 本地：读取 app/keystore.properties；
            // CI：通过环境变量注入（GitHub Actions secrets）
            val propsFile = rootProject.file("app/keystore.properties")
            val props = Properties()
            if (propsFile.exists()) props.load(propsFile.inputStream())
            storeFile = file(
                props.getProperty("STORE_FILE")
                    ?: System.getenv("RELEASE_STORE_FILE") ?: "release-keystore.jks"
            )
            storePassword = props.getProperty("STORE_PASSWORD")
                ?: System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = props.getProperty("KEY_ALIAS")
                ?: System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = props.getProperty("KEY_PASSWORD")
                ?: System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    // 将签名校验延迟到 assembleRelease 任务执行前（doFirst），避免 debug 构建（如 CI 的 assembleDebug）
    // 在配置阶段就因 buildTypes.named("release") 被 realize 而崩溃。
    afterEvaluate {
        tasks.named("assembleRelease") {
            doFirst {
                val sc = android.buildTypes.getByName("release").signingConfig
                require(
                    sc?.storePassword != null &&
                    sc?.keyAlias != null &&
                    sc?.keyPassword != null
                ) {
                    "缺少 release 签名配置：请在 app/keystore.properties 或环境变量中提供 RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val releaseApkOutputDir = layout.buildDirectory.dir("outputs/apk/release")
val releaseApkTargetName = provider {
    "NoSlackAlarm-v${android.defaultConfig.versionName}.apk"
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        val apkOutputDir = releaseApkOutputDir
        val targetName = releaseApkTargetName.get()
        val renameTask = tasks.register("renameReleaseApk") {
            doLast {
                val dir = apkOutputDir.get().asFile
                val apk = dir.listFiles { f -> f.isFile && f.extension == "apk" }
                    ?.firstOrNull()
                checkNotNull(apk) { "未在 ${dir.absolutePath} 找到 release APK" }
                val target = File(dir, targetName)
                if (apk.name != target.name) {
                    apk.renameTo(target)
                }
            }
        }
        tasks.matching { it.name == "assembleRelease" }.configureEach {
            finalizedBy(renameTask)
        }
    }
}

val releaseApkPath = releaseApkOutputDir.map { it.file(releaseApkTargetName.get()) }

val installReleaseApk = tasks.register<Exec>("installReleaseApk") {
    dependsOn(tasks.named("assembleRelease"))
    commandLine("adb", "install", "-r", releaseApkPath.get().asFile.absolutePath)
}

val startReleaseApp = tasks.register<Exec>("startReleaseApp") {
    dependsOn(installReleaseApk)
    commandLine(
        "adb", "shell", "am", "start",
        "-n", "lorix.noslackalarm/.MainActivity"
    )
}

tasks.register("installReleaseAdb") {
    dependsOn(startReleaseApp)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:system"))
    implementation(project(":feature-alarm"))
    implementation(project(":feature-ringing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.navigationevent.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // miuix Navigation3
    implementation(libs.miuix.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 暴露 versionName 给 CI（如 GitHub Actions 生成带版本号的 artifact 名），单一真相源来自 defaultConfig.versionName
tasks.register("printVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}