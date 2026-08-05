// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
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
