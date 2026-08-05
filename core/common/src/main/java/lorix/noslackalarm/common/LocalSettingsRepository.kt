// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.common

import androidx.compose.runtime.staticCompositionLocalOf
import lorix.noslackalarm.domain.repository.SettingsRepository

val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("No SettingsRepository provided")
}