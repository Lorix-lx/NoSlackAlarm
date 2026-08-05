// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object AlarmList : Screen

    @Serializable
    data class AlarmEdit(
        val alarmId: Long = -1L,
        val hour: Int = 7,
        val minute: Int = 30,
    ) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object About : Screen

    @Serializable
    data object PermissionInfo : Screen

    @Serializable
    data object Repeat : Screen
}

/** 导航返回栈持久化专用 JSON 序列化器：用于把返回栈（List<Screen>）序列化后存入 DataStore，
 * 在同进程内 Activity 被重建时由 MainActivity 读回以恢复用户最后停留的页面。Screen 为 sealed
 * interface，自带多态序列化器，默认 Json 即可正确处理各子类的序列化与反序列化。 */
val NavJson: Json = Json