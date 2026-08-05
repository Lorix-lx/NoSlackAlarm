// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AlarmMode(val label: String) {
    VIBRATE_ONLY("仅震动"),
    RINGTONE_ONLY("仅铃声"),
    BOTH("震动+铃声")
}