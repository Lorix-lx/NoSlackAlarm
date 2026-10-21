// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    val onCi = System.getenv("CI") == "true"
    repositories {
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    val onCi = System.getenv("CI") == "true"
    repositories {
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "noslack-alarm"

include(":app")
include(":core:common")
include(":core:data")
include(":core:system")
include(":feature-alarm")
include(":feature-ringing")
