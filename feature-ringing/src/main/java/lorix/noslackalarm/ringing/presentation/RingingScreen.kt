// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.presentation

// ============ 导入Android系统组件 ============
import android.content.Intent

// ============ 导入动画组件 ============
import androidx.compose.animation.core.animateFloatAsState

// ============ 导入布局组件 ============
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// ============ 导入 Miuix 组件 ============
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============ 导入状态管理和交互 ============
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// ============ 导入UI修饰符和工具 ============
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============ 导入依赖注入和主题 ============
import androidx.hilt.navigation.compose.hiltViewModel
import lorix.noslackalarm.common.theme.NoSlackAlarmTheme
import lorix.noslackalarm.common.LocalSettingsRepository
import lorix.noslackalarm.common.KeyboardLayout
import lorix.noslackalarm.common.util.isCompactHeight
import lorix.noslackalarm.ringing.domain.MathProblem
import lorix.noslackalarm.ringing.service.AlarmService

// ============ 导入协程和时间处理 ============
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 矮屏（横屏/小窗）下键盘的最大宽度：避免键盘按屏宽铺满导致高度超出窗口，保证键盘可达
private val COMPACT_KEYBOARD_MAX_WIDTH = 180.dp

/**
 * 闹钟响铃界面（主入口）
 * 
 * 闹钟响时显示的界面，用户需要回答数学题才能关闭闹钟
 * 
 * @param viewModel 响铃界面的视图模型，管理数学题逻辑和答案验证
 */
@Composable
fun RingingScreen(
    viewModel: RingingViewModel = hiltViewModel()
) {
    // 从ViewModel获取界面状态（包含题目、输入、错误提示等）
    val state by viewModel.state.collectAsState()

    // 读取答题键盘布局，传给键盘组件
    val keyboardLayout by LocalSettingsRepository.current.keyboardLayout.collectAsState(
        initial = KeyboardLayout.PHONE.value
    )
    
    // 获取当前上下文
    val context = LocalContext.current

    // 获取Activity实例，用于关闭界面
    val activity = context as? android.app.Activity
    
    // 从Intent中获取闹钟ID（启动这个Activity时传入的）
    val alarmId = activity?.intent?.getLongExtra("alarm_id", -1L) ?: -1L

    // 初始化闹钟ID
    LaunchedEffect(alarmId) {
        if (alarmId != -1L) {
            viewModel.initAlarmId(alarmId)
        }
    }

    // 实时时间更新
    var currentTime by remember { mutableStateOf(getCurrentTime()) }

    // 每秒更新一次时间显示
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)  // 延迟1秒
            currentTime = getCurrentTime()
            viewModel.updateTime(currentTime)
        }
    }

    // 监听是否完成，完成后停止服务并关闭界面
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)  // 停止闹钟服务（停止响铃）
            activity?.finish()          // 关闭Activity
        }
    }

    // 显示响铃界面内容
    RingingScreenContent(
        layout = KeyboardLayout.from(keyboardLayout),
        state = state.copy(currentTime = currentTime),  // 更新后的状态（包含最新时间）
        onNumberClick = viewModel::onNumberClick,       // 点击数字键盘
        onDelete = viewModel::onDelete,                 // 删除最后一位
        onSubmit = viewModel::onSubmit,                 // 提交答案
        onOpenSketchpad = viewModel::onOpenSketchpad,   // 打开白板
        onCloseSketchpad = viewModel::onCloseSketchpad  // 关闭白板
    )
}

/**
 * 响铃界面内容组件
 * 
 * 这是响铃界面的核心内容，包含：
 * - 当前时间显示
 * - 数学题目
 * - 答案输入区
 * - 数字键盘
 * - 白板按钮
 * - 贪睡提示
 * 
 * @param state 响铃界面状态
 * @param onNumberClick 点击数字的回调
 * @param onClear 清空输入的回调
 * @onDelete 删除最后一位的回调
 * @param onSubmit 提交答案的回调
 * @param onOpenSketchpad 打开白板的回调
 * @param onCloseSketchpad 关闭白板的回调
 */
@Composable
fun RingingScreenContent(
    state: RingingUiState,
    layout: KeyboardLayout = KeyboardLayout.PHONE,
    onNumberClick: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onOpenSketchpad: () -> Unit = {},
    onCloseSketchpad: () -> Unit = {}
) {
    // 答案错误时的抖动动画（改变透明度和缩放）
    val shakeAlpha by animateFloatAsState(
        targetValue = if (state.isWrong) 0.7f else 1f,
        label = "shakeAlpha"
    )
    
    val shakeScale by animateFloatAsState(
        targetValue = if (state.isWrong) 0.95f else 1f,
        label = "shakeScale"
    )

    // 矮屏（横屏/小窗）下收紧：主布局改为可滚动 + 等距排列，字号缩小、键盘限宽；竖屏保持原样
    val compactHeight = isCompactHeight()
    val timeFontSize = if (compactHeight) 36.sp else 64.sp
    val problemFontSize = if (compactHeight) 24.sp else 40.sp
    val answerFontSize = if (compactHeight) 32.sp else 56.sp

    // 主布局：垂直排列，各元素均匀分布；
    // 填充主题背景，亮色下文字（深色 onBackground）才与浅灰底正确分层，
    // 否则会透出深色窗口底而看不见。
    // 矮屏下 SpaceEvenly 与滚动不可共存，改用 spacedBy 保证内容可达。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp) // 左右24dp，上下16dp内边距
            .then(
                if (compactHeight) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally, // 水平居中
        verticalArrangement = if (compactHeight) {
            Arrangement.spacedBy(6.dp)
        } else {
            Arrangement.SpaceEvenly // 垂直均匀分布
        }
    ) {
        // 1.时间显示区
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 当前时间
            Text(
                text = state.currentTime,
                fontSize = timeFontSize,
                color = MiuixTheme.colorScheme.onBackground
            )
            // 标签"现在时间"
            Text(
                text = "现在时间",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Center
            )
        }

        // 间距
        Spacer(modifier = Modifier.height(8.dp))

        // 2.数学题目
        Text(
            text = state.problem.expression,
            fontSize = problemFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(shakeAlpha)
                .scale(shakeScale),
            color = MiuixTheme.colorScheme.onBackground
        )

        // 3.答案输入显示区
        Text(
            text = state.input.ifEmpty { " " },
            fontSize = answerFontSize,
            textAlign = TextAlign.Center,
            color = if (state.isWrong) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compactHeight) 0.dp else 12.dp)
                .alpha(shakeAlpha)
                .scale(shakeScale)
        )

        // 4.数学键盘（0-9数字、清除、提交）；矮屏下限宽，避免按屏宽铺满导致高度超出
        MathKeyboard(
            layout = layout,
            onNumberClick = onNumberClick,
            onDelete = onDelete,
            onSubmit = onSubmit,
            modifier = if (compactHeight) {
                Modifier.fillMaxWidth().widthIn(max = COMPACT_KEYBOARD_MAX_WIDTH)
            } else {
                Modifier.fillMaxWidth()
            }
        )

        // 5.白板按钮（可以在上面用画笔计算）
        TextButton(text = "打开白板辅助计算", onClick = onOpenSketchpad)

        // 6.贪睡提示
        Text(
            text = "按下电源键 = 贪睡10分钟",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center
        )
    }

    // 如果白板打开，显示白板底部弹窗
    if (state.showSketchpad) {
        SketchpadBottomSheet(
            problem = state.problem,
            onDismiss = onCloseSketchpad
        )
    }
}

/**
 * 获取当前时间
 * 
 * 格式化为 HH:mm（24小时制）
 * 
 * @return 当前时间的字符串
 */
private fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

/**
 * 界面预览
 * 
 * 在Android Studio中可以实时预览这个界面
 */
@Preview
@Composable
private fun RingingScreenPreview() {
    NoSlackAlarmTheme {
        RingingScreenContent(
            state = RingingUiState(
                currentTime = "08:00",
                problem = MathProblem("23 + 17 = ?", "40"),
                input = "40",
                isWrong = false
            )
        )
    }
}
