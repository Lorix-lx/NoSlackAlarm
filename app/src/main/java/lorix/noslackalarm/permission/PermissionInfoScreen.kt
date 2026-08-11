// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.SettingsDefaults
import lorix.noslackalarm.common.permissionIcon
import lorix.noslackalarm.system.permission.PermissionUiItem
import lorix.noslackalarm.system.permission.collectPermissionUiItems

/**
 * 刷新周期与按钮延迟常量（仅本文件使用，就近放置）。
 *
 * - [REFRESH_INTERVAL_MS]：停留在权限页时自动重检所有权限状态并刷新 UI 的间隔；
 * - [ACK_BUTTON_DELAY_MS]：用户点进对应系统设置页后，「我已开启」按钮的延迟显示时间，
 *   防止一进页面就误触；与刷新周期解耦。
 */
private const val REFRESH_INTERVAL_MS = 500L
private const val ACK_BUTTON_DELAY_MS = 2000L

/**
 * 权限信息页
 *
 * 以列表形式展示应用所需的全部权限项，每项显示图标、名称、说明与授权状态
 * （已开启 / 未开启 / 建议开启）。
 *
 * - 支持系统弹窗申请的权限（如通知）：点击整行直接调起系统授权弹窗；
 *   若被拒绝（含「不再询问」），回退跳转对应系统设置页，避免卡死。
 * - 无标准检测 API 的权限（自启动 / 锁屏显示 / 后台弹出界面）：点按整行跳转系统设置页；
 *   用户点进对应系统页 2 秒后，行末才出现「我已开启」按钮，点击将其标记为已确认（持久化），
 *   使其从闹钟列表的权限提醒中消失；点击后提供「撤销」按钮回退。省电白名单不提供手动按钮，依赖系统检测。
 * 进入页与返回（onResume）时刷新状态；在页面停留期间按刷新周期自动重检所有权限状态并即时更新。
 *
 * @param onNavigateBack 返回上一页的回调
 */
@Composable
fun PermissionInfoScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsRepository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()

    // 返回设置页后刷新：onResume 时累加刷新计数，触发权限重新检测
    var refreshKey by remember { mutableIntStateOf(0) }
    val items by produceState(initialValue = emptyList<PermissionUiItem>(), refreshKey) {
        value = collectPermissionUiItems(context)
    }

    // 用户手动确认「已开启」的权限集合（无检测 API / 无法可靠检测的权限）
    val acknowledged by settingsRepository.acknowledgedPermissions.collectAsState(
        initial = SettingsDefaults.ACKNOWLEDGED_PERMISSIONS
    )

    // 记录用户点击各权限行（进入系统设置页）的时间戳，用于「我已开启」按钮在点进系统页
    // [ACK_BUTTON_DELAY_MS] 后才显示，避免误触
    var visitedAt by remember { mutableStateOf(emptyMap<String, Long>()) }
    // 周期性刷新的当前时间戳：随刷新周期更新，驱动「我已开启」按钮的延迟判定
    var nowMillis by remember { mutableStateOf(0L) }

    // 停留在权限页期间，按 [REFRESH_INTERVAL_MS] 自动重检所有权限的授权状态并刷新按钮延迟判定，
    // 使在系统设置中开启的权限能及时反映为「已开启」（含省电白名单等检测项）。
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            refreshKey++
            nowMillis = System.currentTimeMillis()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 应用内系统弹窗请求（仅支持弹窗的权限，如通知）。被拒（含永久拒绝）时回退到设置页。
    var pendingInAppItem by remember { mutableStateOf<PermissionUiItem?>(null) }
    val inAppPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val item = pendingInAppItem
        pendingInAppItem = null
        refreshKey++ // 重新检测授权状态，更新行内徽标
        if (!granted && item != null) {
            // 拒绝后跳转设置页，方便用户手动开启，避免流程卡死
            item.launch(context)
        }
    }

    // 整行点击：可弹窗的权限优先弹系统授权，其余跳转设置页
    val handleItemClick: (PermissionUiItem) -> Unit = { item ->
        visitedAt = visitedAt + (item.key to System.currentTimeMillis()) // 记录进入系统页时刻，2 秒后显示按钮
        val perm = item.inAppPermission
        if (perm != null) {
            pendingInAppItem = item
            inAppPermissionLauncher.launch(perm)
        } else {
            item.launch(context)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "权限信息",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onBackground
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
        modifier = Modifier.graphicsLayer()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "permission_intro") {
                Text(
                    text = "以下权限有助于闹钟在设定时间正常响铃。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 4.dp)
                )
            }

            items(items, key = { it.key }) { item ->
                PermissionRow(
                    item = item,
                    isAcknowledged = item.key in acknowledged,
                    canShowAcknowledge = item.allowManualConfirm &&
                        (visitedAt[item.key]?.let { nowMillis - it >= ACK_BUTTON_DELAY_MS } ?: false),
                    onRowClick = { handleItemClick(item) },
                    onAcknowledge = {
                        scope.launch { settingsRepository.acknowledgePermission(item.key) }
                    },
                    onRevoke = {
                        scope.launch { settingsRepository.revokeAcknowledgedPermission(item.key) }
                    }
                )
            }
        }
    }
}

/**
 * 单条权限行：图标 + 名称 + 说明 + 状态徽标。
 *
 * - 整行可点击：可弹窗权限调起系统授权，其余跳转系统设置页。
 * - 仅 [PermissionUiItem.allowManualConfirm] 为 true 的权限（自启动 / 锁屏显示），在用户点进过
 *   对应系统设置页 2 秒后（[canShowAcknowledge]，延迟由 [ACK_BUTTON_DELAY_MS] 控制），才在行末展示
 *   「我已开启」按钮；点击后标记为已确认（持久化），状态变为「已确认」并显示「撤销」按钮，便于误点时回退。
 *   省电白名单不提供该按钮，完全依赖周期自动重检刷新为「已开启」。
 */
@Composable
private fun PermissionRow(
    item: PermissionUiItem,
    isAcknowledged: Boolean,
    canShowAcknowledge: Boolean,
    onRowClick: () -> Unit,
    onAcknowledge: () -> Unit,
    onRevoke: () -> Unit
) {
    val statusText: String
    val statusColor: androidx.compose.ui.graphics.Color
    when {
        isAcknowledged -> {
            statusText = "已确认"
            statusColor = MiuixTheme.colorScheme.primary
        }
        item.granted == true -> {
            statusText = "已开启"
            statusColor = MiuixTheme.colorScheme.primary
        }
        item.granted == false -> {
            statusText = "未开启"
            statusColor = MiuixTheme.colorScheme.error
        }
        else -> {
            statusText = "建议开启"
            statusColor = MiuixTheme.colorScheme.error
        }
    }

    // 已进入对应系统页满 2 秒且允许手动确认、且尚未确认时，才展示「我已开启」
    val showAcknowledge = canShowAcknowledge && !isAcknowledged
    val showRevoke = isAcknowledged

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = permissionIcon(item.iconKey),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = if (item.granted == true || isAcknowledged) {
                MiuixTheme.colorScheme.onSurfaceSecondary
            } else {
                MiuixTheme.colorScheme.error
            }
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.title,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = item.description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧：状态徽标 + （允许手动确认且已进入系统页的权限）「我已开启 / 撤销」按钮
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = statusText,
                style = MiuixTheme.textStyles.body2,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )

            if (showAcknowledge) {
                TextButton(
                    text = "我已开启",
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            } else if (showRevoke) {
                TextButton(
                    text = "撤销",
                    onClick = onRevoke
                )
            }
        }
    }
}
