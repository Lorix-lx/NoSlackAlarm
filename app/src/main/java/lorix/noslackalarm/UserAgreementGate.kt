// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.theme.ScrimActive
import lorix.noslackalarm.common.ui.DialogTitle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动协议确认门控（全应用级）。
 *
 * 读取 DataStore 中「是否已同意《用户协议与隐私政策》」的状态：
 * - 未同意：渲染不可通过点击外部 / 返回键关闭的 [OverlayDialog] 确认弹窗，拦截应用所有页面入口
 *   （含桌面「创建闹钟」快捷方式直达创建页的场景，因为本组件由 MainActivity 的根 Scaffold 承载，
 *   弹窗默认 `renderInRootScaffold = true` 会渲染在最顶层全屏覆盖）；
 * - 点击「不同意」：立即退出应用（[Activity.finishAndRemoveTask]）；
 * - 点击「同意并继续」：写入 DataStore 持久化，弹窗关闭，后续启动不再提示。
 *
 * 协议全文弹窗复用 `res/raw/user_agreement.txt` 与 `res/raw/privacy_policy.txt`（与关于页一致），
 * 正文可长按选择复制。
 *
 * @param initialAgreed MainActivity 预取的协议同意状态真实值（避免首帧闪烁）
 */
@Composable
fun UserAgreementGate(
    initialAgreed: Boolean,
) {
    val context = LocalContext.current
    val settingsRepository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()

    // 本地即时值：初始用预取真实值；点击「同意」时立即置 true 关闭弹窗，不依赖磁盘写入回调。
    // 同时持续 collect 持久化值，兜底同步外部对 DataStore 的变更。
    var agreed by remember { mutableStateOf(initialAgreed) }
    LaunchedEffect(Unit) {
        settingsRepository.agreementAccepted.collect { agreed = it }
    }

    // 协议全文详情弹窗显隐状态（点击主弹窗中的链接打开，可关闭，不关闭主弹窗）
    var showAgreementDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // 协议全文（懒加载一次，复用 raw 资源）
    val userAgreementText = remember {
        context.resources.openRawResource(R.raw.user_agreement).bufferedReader().use { it.readText() }
    }
    val privacyPolicyText = remember {
        context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
    }

    // 主确认弹窗或任一详情弹窗打开时，声明遮罩激活，驱动系统栏/导航栏随遮罩同步变暗
    ScrimActive(!agreed || showAgreementDialog || showPrivacyDialog)

    // 未同意：渲染不可关闭的协议确认弹窗
    OverlayDialog(
        title = null,
        show = !agreed,
        onDismissRequest = null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle("用户协议与隐私政策")
            Text(
                text = "欢迎使用不赖闹钟！在开始使用之前，请阅读并同意以下条款：",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            AgreementLinkRow(
                title = "《用户协议》",
                onClick = { showAgreementDialog = true },
            )
            AgreementLinkRow(
                title = "《隐私政策》",
                onClick = { showPrivacyDialog = true },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    text = "不同意",
                    onClick = {
                        (context as? Activity)?.finishAndRemoveTask()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "同意并继续",
                    onClick = {
                        agreed = true
                        coroutineScope.launch {
                            settingsRepository.setAgreementAccepted(true)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    // 协议全文详情弹窗（可滚动、可复制、可关闭）
    AgreementDetailDialog(
        show = showAgreementDialog,
        title = "用户协议",
        text = userAgreementText,
        onDismiss = { showAgreementDialog = false },
    )
    AgreementDetailDialog(
        show = showPrivacyDialog,
        title = "隐私政策",
        text = privacyPolicyText,
        onDismiss = { showPrivacyDialog = false },
    )
}

/**
 * 协议链接行：主色标题 + 尾部箭头图标，整行可点击打开对应协议全文弹窗。
 */
@Composable
private fun AgreementLinkRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}

/**
 * 协议全文详情弹窗：可滚动阅读、正文可长按选择复制，底部固定「确定」按钮。
 * 结构与 AboutScreen 的隐私政策/用户协议弹窗保持一致。
 */
@Composable
private fun AgreementDetailDialog(
    show: Boolean,
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = null,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle(title)
            // 内容区：weight 占满剩余高度并滚动，保证底部按钮始终可见、不被裁剪
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                text = "确定",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
