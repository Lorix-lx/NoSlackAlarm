// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// JVM target 21：Miuix 0.9.4 的 AAR 以 JVM 21 构建，其 inline 函数（如 rememberNavBackStack）
// 无法内联进 target 17 的字节码（INLINE_FROM_HIGHER_PLATFORM），故全部模块统一为 21。
// 在这里集中配置一份，避免 6 个模块各自复制同一段 compileOptions / compilerOptions。
subprojects {
    val targetJvm = JavaVersion.VERSION_21

    // Java 侧字节码目标（Android DSL，application 与 library 是两个独立扩展类型）
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
            compileOptions {
                sourceCompatibility = targetJvm
                targetCompatibility = targetJvm
            }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
            compileOptions {
                sourceCompatibility = targetJvm
                targetCompatibility = targetJvm
            }
        }
    }

    // Kotlin 侧字节码目标：按任务类型统一，绕开 AGP 内建 Kotlin 的嵌套 DSL 类型
    // （android { kotlin { compilerOptions { } } } 无法从外部稳定的 configure<T>）。
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
