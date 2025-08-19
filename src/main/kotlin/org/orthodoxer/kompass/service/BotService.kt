package org.orthodoxer.kompass.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.orthodoxer.kompass.model.Question
import org.orthodoxer.kompass.model.SessionState
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class BotService {

    private val modules: MutableMap<String, List<Question>> = mutableMapOf()
    private val langMap: MutableMap<Long, String> = mutableMapOf()
    private val sessionMap: MutableMap<Long, SessionState> = mutableMapOf()

    @PostConstruct
    fun init() {
        val mapper = jacksonObjectMapper()
        modules["baptism"] = mapper.readValue(
            javaClass.getResource("/modules/baptism/questions.json")!!
        )
        modules["first_visit"] = mapper.readValue(
            javaClass.getResource("/modules/first_visit/questions.json")!!
        )
    }

    fun processUpdate(update: Update): SendMessage? {
        val message = update.message ?: return null
        val chatId = message.chatId
        val text = message.text ?: return null

        val lang = langMap.getOrPut(chatId) { "ru" }
        val session = sessionMap.getOrPut(chatId) { SessionState() }

        val replyText = when (text) {
            "/start" -> {
                langMap[chatId] = "ru"
                "Выберите модуль: \n1. Хочу быть крестным\n2. Первый раз в церкви"
            }
            "1" -> {
                session.module = "baptism"
                session.currentQuestion = 0
                nextQuestion(chatId, session)
            }
            "2" -> {
                session.module = "first_visit"
                session.currentQuestion = 0
                nextQuestion(chatId, session)
            }
            else -> handleAnswer(chatId, session, text)
        }

        return SendMessage(chatId.toString(), replyText)
    }

    private fun nextQuestion(chatId: Long, session: SessionState): String {
        val questions = modules[session.module] ?: return "Модуль не найден."
        return if (session.currentQuestion >= questions.size) {
            sessionMap.remove(chatId)
            "Вы прошли тест. Правильных ответов: ${session.correctAnswers} из ${questions.size}."
        } else {
            val q = questions[session.currentQuestion]
            val options = q.options.withIndex().joinToString("\n") { (i, opt) -> "${i + 1}. $opt" }
            "${q.text[langMap[chatId] ?: "ru"]}\n$options"
        }
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String): String {
        val index = text.toIntOrNull()?.minus(1) ?: return "Пожалуйста, введите номер варианта."
        val questions = modules[session.module] ?: return "Модуль не найден."
        val q = questions.getOrNull(session.currentQuestion) ?: return "Вопрос не найден."

        if (index == q.correctIndex) {
            session.correctAnswers++
        }
        session.currentQuestion++
        return nextQuestion(chatId, session)
    }
}
