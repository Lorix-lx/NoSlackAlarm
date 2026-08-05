// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.domain

import javax.inject.Inject

class ValidateMathAnswerUseCase @Inject constructor() {

    operator fun invoke(problem: MathProblem, input: String): Boolean {
        return input == problem.answer
    }
}
