// Copyright 2026 Lorix and Contributors
// SPDX-License-Identifier: Apache-2.0

package lorix.noslackalarm.ringing.domain

object MathProblemGenerator {

    fun generate(): MathProblem {
        val ops = listOf('+', '-', '*', '/')
        val op = ops.random()
        return when (op) {
            '+' -> {
                val a = (10..99).random()
                val b = (10..99).random()
                MathProblem("$a + $b = ?", (a + b).toString())
            }
            '-' -> {
                val a = (20..99).random()
                val b = (10 until a).random()
                MathProblem("$a - $b = ?", (a - b).toString())
            }
            '*' -> {
                val a = (2..12).random()
                val b = (2..12).random()
                MathProblem("$a × $b = ?", (a * b).toString())
            }
            '/' -> {
                val b = (2..9).random()
                val ans = (2..9).random()
                val a = b * ans
                MathProblem("$a ÷ $b = ?", ans.toString())
            }
            else -> throw IllegalStateException()
        }
    }
}
