// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.menu.OverlayDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import lorix.noslackalarm.BuildConfig
import lorix.noslackalarm.R
import lorix.noslackalarm.common.theme.ScrimActive
import lorix.noslackalarm.common.ui.DialogTitle

/**
 * 关于页面
 *
 * 展示应用的基础信息、功能简介、开源项目链接、隐私政策与用户协议、本项目许可证与第三方依赖库。
 * 顶部应用信息区展示图标、名称与版本号；下方依次为简介、开源（项目链接 + 许可证）、更新与支持（隐私政策 + 用户协议）、致谢（第三方依赖与开源库）四个区块。
 *
 * @param onNavigateBack 返回上一页的回调
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // 第三方依赖库弹窗显隐状态
    var showDependenciesDialog by remember { mutableStateOf(false) }
    // 本项目许可证弹窗显隐状态
    var showProjectLicenseDialog by remember { mutableStateOf(false) }
    // 隐私政策弹窗显隐状态
    var showPrivacyDialog by remember { mutableStateOf(false) }
    // 用户协议弹窗显隐状态
    var showUserAgreementDialog by remember { mutableStateOf(false) }
    // 「源码仓库」下拉菜单展开状态（OverlayDropdownMenu 同样渲染在根 Scaffold 的 popup host 内）
    var sourceRepoMenuExpanded by remember { mutableStateOf(false) }

    // 声明当前页面存在激活的 Overlay（任一弹窗或下拉菜单）；
    // 由 NoSlackAlarmTheme 通过 LocalScrimActive 集中驱动系统栏/导航栏随遮罩同步变暗。
    ScrimActive(
        showDependenciesDialog || showProjectLicenseDialog || showPrivacyDialog ||
            showUserAgreementDialog || sourceRepoMenuExpanded
    )

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ======= 应用信息 Header =======
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "不赖闹钟",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onBackground
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            // ======= 简介 / 功能说明 =======
            SmallTitle(text = "关于不赖闹钟")

            Text(
                text = AboutIntro,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 8.dp)
            )

            // ======= 分类分割线：简介 / 开源 =======
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.dividerLine
            )

            // ======= 开源 / 项目链接 =======
            SmallTitle(text = "开源")

            // 源码仓库：内联下拉菜单
            val repoEntry = remember {
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = "GitHub(主仓库)",
                            summary = "https://github.com/Lorix-lx/NoSlackAlarm",
                            onClick = { openUrl(context, "https://github.com/Lorix-lx/NoSlackAlarm") }
                        ),                    
                        DropdownItem(
                            text = "Gitee(镜像仓库)",
                            summary = "https://gitee.com/Lorix/NoSlackAlarm",
                            onClick = { openUrl(context, "https://gitee.com/Lorix/NoSlackAlarm") }
                        ),
                    )
                )
            }
            OverlayDropdownMenu(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                title = "源码仓库",
                summary = "GitHub / Gitee",
                entry = repoEntry,
                onExpandedChange = { sourceRepoMenuExpanded = it }
            )

            ProjectLinks.forEach { link ->
                ArrowPreference(
                    insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    title = link.title,
                    summary = link.url,
                    onClick = { openUrl(context, link.url) }
                )
            }

            ArrowPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                title = "许可证",
                summary = "Apache License 2.0",
                onClick = { showProjectLicenseDialog = true },
                holdDownState = showProjectLicenseDialog
            )

            // ======= 分类分割线：开源 / 更新与支持 =======
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.dividerLine
            )

            // ======= 更新与支持 =======
            SmallTitle(text = "更新与支持")

            ArrowPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                title = "隐私政策",
                summary = "我们如何收集与使用您的数据",
                onClick = { showPrivacyDialog = true },
                holdDownState = showPrivacyDialog
            )

            ArrowPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                title = "用户协议",
                summary = "使用条款与免责声明",
                onClick = { showUserAgreementDialog = true },
                holdDownState = showUserAgreementDialog
            )

            // ======= 分类分割线：更新与支持 / 致谢 =======
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.dividerLine
            )

            // ======= 致谢 / 第三方库 =======
            SmallTitle(text = "致谢")

            ArrowPreference(
                insideMargin = PaddingValues(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                title = "依赖与开源库",
                summary = "本项目使用以下开源库",
                onClick = { showDependenciesDialog = true },
                holdDownState = showDependenciesDialog
            )
        }

        // 第三方依赖与开源库弹窗（居中，置于 Scaffold 内部以使用 MiuixPopupHost）
        DependenciesDialog(
            show = showDependenciesDialog,
            onDismiss = { showDependenciesDialog = false }
        )
        // 本项目许可证弹窗
        ProjectLicenseDialog(
            show = showProjectLicenseDialog,
            onDismiss = { showProjectLicenseDialog = false }
        )
        // 隐私政策弹窗
        PrivacyPolicyDialog(
            show = showPrivacyDialog,
            onDismiss = { showPrivacyDialog = false }
        )
        // 用户协议弹窗
        UserAgreementDialog(
            show = showUserAgreementDialog,
            onDismiss = { showUserAgreementDialog = false }
        )
    }
}

/**
 * 第三方依赖与开源库弹窗：可滚动列表展示本项目依赖的开源库及其许可证。
 */
@Composable
private fun DependenciesDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        title = null,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle("依赖与开源库")
            // 内容区：weight 占满剩余高度并滚动，保证底部按钮始终可见、不被裁剪
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                val licenses = remember { OpenSourceLicenses }
                licenses.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onBackground
                        )
                        Text(
                            text = item.license,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    if (item != licenses.last()) {
                        HorizontalDivider(
                            color = MiuixTheme.colorScheme.dividerLine
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                text = "确定",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 本项目许可证弹窗：离线展示本项目自身采用的完整开源协议（Apache License 2.0）。
 * 协议全文来自 app/src/main/res/raw/license.txt，已随 APK 打包，无需联网。
 * 因全部第三方依赖同为 Apache 2.0，本文本亦覆盖其许可证说明。
 */
@Composable
private fun ProjectLicenseDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val licenseText = remember {
        context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
    }
    OverlayDialog(
        title = null,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle("许可证")
            // 内容区：weight 占满剩余高度并滚动，保证底部按钮始终可见、不被裁剪
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "本项目基于 Apache License 2.0 发布。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onBackground
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = licenseText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                text = "确定",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 隐私政策弹窗：离线展示本项目隐私政策全文。
 * 全文来自 app/src/main/res/raw/privacy_policy.txt，已随 APK 打包，无需联网。
 */
@Composable
private fun PrivacyPolicyDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val policyText = remember {
        context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
    }
    OverlayDialog(
        title = null,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle("隐私政策")
            // 内容区：weight 占满剩余高度并滚动，保证底部按钮始终可见、不被裁剪
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = policyText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                text = "确定",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 用户协议弹窗：离线展示本项目用户协议全文。
 * 全文来自 app/src/main/res/raw/user_agreement.txt，已随 APK 打包，无需联网。
 */
@Composable
private fun UserAgreementDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val agreementText = remember {
        context.resources.openRawResource(R.raw.user_agreement).bufferedReader().use { it.readText() }
    }
    OverlayDialog(
        title = null,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle("用户协议")
            // 内容区：weight 占满剩余高度并滚动，保证底部按钮始终可见、不被裁剪
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = agreementText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                text = "确定",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 通过系统浏览器打开 URL，无可用浏览器时静默忽略。
 */
private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 设备上无浏览器应用，忽略
    }
}

/**
 * 应用简介文案。
 */
private const val AboutIntro =
    "不赖闹钟是一款主打「防睡过头」的闹钟应用：通过数学答题解除闹钟等方式，帮你彻底告别赖床。界面简洁清爽，支持亮/暗色主题自适应。"

/**
 * 开源 / 项目链接
 */
private data class ProjectLink(val title: String, val url: String)

private val ProjectLinks = listOf(
    ProjectLink("项目官网", "暂无"),
)

/**
 * 本项目依赖的开源库及其许可证（静态数据）。
 */
private data class OpenSourceLibrary(val name: String, val license: String)

private val OpenSourceLicenses = listOf(
    OpenSourceLibrary("Miuix", "Apache License 2.0"),
    OpenSourceLibrary("Jetpack Compose", "Apache License 2.0"),
    OpenSourceLibrary("Miuix Nav", "Apache License 2.0"),
    OpenSourceLibrary("Hilt (Dagger)", "Apache License 2.0"),
    OpenSourceLibrary("Kotlin Coroutines", "Apache License 2.0"),
    OpenSourceLibrary("kotlinx-serialization", "Apache License 2.0"),
    OpenSourceLibrary("AndroidX Core / Lifecycle", "Apache License 2.0"),
)
