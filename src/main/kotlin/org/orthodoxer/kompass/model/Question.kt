package org.orthodoxer.kompass.model

/**
 * @author IDeev
 * Erstellt am 18.08.2025
 */
data class Question(
    val id: Int,
    val text: Map<String, String>,
    val options: List<String>,
    val correctIndex: Int
)