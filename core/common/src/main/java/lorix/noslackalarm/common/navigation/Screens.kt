// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 应用路由层级。实现 miuix-nav 的 [NavKey]（纯标记接口），并整体标注 `@Serializable`：
 * miuix-nav 的 `rememberNavBackStack` 依赖 kotlinx.serialization 在配置变更与进程恢复时
 * 保存/还原返回栈，因此可序列化是该层级的硬性要求。
 *
 * 各路由均使用 `data object` / `data class`：其 `toString()` 由值派生且互不相同，
 * 满足 miuix-nav 对 contentKey「整栈唯一、可跨进程稳定」的要求。
 */
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

    /**
     * 重复规则设置页。
     *
     * [repeatDays] 是进入本页时编辑页的当前选中值（按位表示周一~周日），随路由一起进入返回栈，
     * 作为本页的初始选中态真相源；用户最终选择经结果通道回传给编辑页，不再依赖共享 ViewModel。
     *
     * 默认值 `0` 同时用于兼容升级前已持久化在 DataStore 中的旧 JSON（旧 `Repeat` 为 data object，
     * 没有该字段，解码时按默认值补齐，避免整条返回栈解析失败而回退到首页）。
     */
    @Serializable
    data class Repeat(
        val repeatDays: Int = 0,
    ) : Screen
}

/** 导航返回栈持久化专用 JSON 序列化器：用于把返回栈（List<Screen>）序列化后存入 DataStore，
 * 在同进程内 Activity 被重建时由 MainActivity 读回以恢复用户最后停留的页面。Screen 为 sealed
 * interface，自带多态序列化器，默认 Json 即可正确处理各子类的序列化与反序列化。 */
val NavJson: Json = Json
