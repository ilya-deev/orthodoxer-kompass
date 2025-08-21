package org.orthodoxer.kompass.model

/**
 * @author IDeev
 * Erstellt am 18.08.2025
 */
data class SessionState(
    var module: String = "",
    var currentQuestion: Int = 0,
    var correctAnswers: Int = 0,
    val wrongAnswers: MutableList<Int> = mutableListOf()
)
