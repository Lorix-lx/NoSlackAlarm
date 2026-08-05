// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateMathAnswerUseCaseTest {

    private val useCase = ValidateMathAnswerUseCase()

    @Test
    fun `correct answer returns true`() {
        val problem = MathProblem("23 + 17 = ?", "40")
        assertTrue(useCase(problem, "40"))
    }

    @Test
    fun `incorrect answer returns false`() {
        val problem = MathProblem("23 + 17 = ?", "40")
        assertFalse(useCase(problem, "41"))
    }

    @Test
    fun `empty answer returns false`() {
        val problem = MathProblem("23 + 17 = ?", "40")
        assertFalse(useCase(problem, ""))
    }
}
