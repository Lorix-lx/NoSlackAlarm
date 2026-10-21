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

# 导航：0.9.4 起改用 miuix-nav（自研运行时，reflection-free，不依赖反射查找类），
# 返回栈的序列化/反序列化全部走 kotlinx-serialization 的编译期生成序列化器
# （由 kotlinx-serialization 运行期自带的分发消费者规则保障），故无需保留规则。
