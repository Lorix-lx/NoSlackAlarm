# Copyright 2026 Lorix and Contributors
# SPDX-License-Identifier: Apache-2.0

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class lorix.noslackalarm.data.model.** { *; }

# Keep Hilt components
-keepclassmembers class * extends dagger.hilt.android.HiltAndroidApp {
    <init>();
}

# Keep Navigation3 classes (used at runtime via reflection)
-keep class androidx.navigation3.** { *; }
