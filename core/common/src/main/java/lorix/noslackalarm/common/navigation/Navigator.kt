// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 返回栈操作与跨屏结果通道的封装。
 *
 * [backStack] 直接就是 miuix-nav 的 [NavBackStack]（即 `SnapshotStateList<NavKey>`），
 * 可直接交给 `NavDisplay` 渲染；本类的 push/pop/replace/popUntil 与 miuix-nav 的
 * `NavController` 语义一致，之所以保留自研版本是为了同时持有结果通道。
 *
 * 结果通道：miuix-nav v1 核心不提供 `navigateForResult` / `setResult`，官方建议在返回栈旁
 * 自建一个按 requestKey 寻址的总线。这里用 `MutableSharedFlow(replay = 1)` 实现，
 * 保证「在调用方屏幕尚未开始收集时发出的结果」不会丢失；
 * 收集方消费后应调用 [clearResult]，避免下次进入同一屏时误读上一次的陈旧结果。
 */
class Navigator(
    val backStack: NavBackStack,
) {
    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun navigateForResult(route: Screen, requestKey: String) {
        ensureChannel(requestKey)
        push(route)
    }

    fun <T : Any> setResult(requestKey: String, value: T) {
        ensureChannel(requestKey).tryEmit(value)
        pop()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> =
        ensureChannel(requestKey) as SharedFlow<T>

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearResult(requestKey: String) {
        ensureChannel(requestKey).resetReplayCache()
    }

    fun current() = backStack.lastOrNull()
    fun backStackSize() = backStack.size

    private fun ensureChannel(key: String): MutableSharedFlow<Any> =
        resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }
}
