package org.orthodoxer.kompass.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.orthodoxer.kompass.model.Question
import org.orthodoxer.kompass.model.SessionState
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow

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

        val lang = langMap.getOrPut(chatId) {
            when (message.from.languageCode) {
                "de" -> "de"
                else -> "ru"
            }
        }

        val session = sessionMap.getOrPut(chatId) { SessionState() }

        val replyText: String = when (text.lowercase()) {
            "/start" -> {
                langMap[chatId] = lang
                sessionMap.remove(chatId)
                return sendWithButtons(
                    chatId,
                    when (lang) {
                        "de" -> "Wähle ein Modul:\n1. Ich will Pate werden\n2. Zum ersten Mal in der Kirche"
                        else -> "Выберите модуль:\n1. Хочу быть крестным\n2. Первый раз в церкви"
                    },
                    listOf("1", "2")
                )
            }

            "1" -> ({
                session.module = "baptism"
                session.currentQuestion = 0
                nextQuestion(chatId, session)
            }).toString()

            "2" -> ({
                session.module = "first_visit"
                session.currentQuestion = 0
                nextQuestion(chatId, session)
            }).toString()

            else -> handleAnswer(chatId, session, text).toString()
        }

        return SendMessage(chatId.toString(), replyText)
    }

    private fun nextQuestion(chatId: Long, session: SessionState): SendMessage {
        val lang = langMap[chatId] ?: "ru"
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")

        if (session.currentQuestion >= questions.size) {
            sessionMap.remove(chatId)
            val result = when (lang) {
                "de" -> "Du hast den Test abgeschlossen. Richtige Antworten: ${session.correctAnswers} von ${questions.size}."
                else -> "Вы прошли тест. Правильных ответов: ${session.correctAnswers} из ${questions.size}."
            }
            return SendMessage(chatId.toString(), result)
        }

        val q = questions[session.currentQuestion]
        val optionsButtons = q.options.withIndex().map { (i, _) -> (i + 1).toString() }
        val optionsText = q.options.withIndex().joinToString("\n") { (i, opt) -> "${i + 1}. $opt" }
        val questionText = q.text[lang] ?: q.text["ru"] ?: "Вопрос"

        return sendWithButtons(chatId, "$questionText\n$optionsText", optionsButtons)
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String): SendMessage {
        val index = text.toIntOrNull()?.minus(1)
            ?: return SendMessage(chatId.toString(), "Пожалуйста, введите номер варианта.")

        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        val q = questions.getOrNull(session.currentQuestion) ?: return SendMessage(chatId.toString(), "Вопрос не найден.")

        if (index == q.correctIndex) {
            session.correctAnswers++
        }

        session.currentQuestion++
        return nextQuestion(chatId, session)
    }

    private fun sendWithButtons(chatId: Long, text: String, buttons: List<String>): SendMessage {
        val keyboardMarkup = ReplyKeyboardMarkup()
        keyboardMarkup.resizeKeyboard = true
        keyboardMarkup.keyboard = buttons.map {
            val row = KeyboardRow()
            row.add(KeyboardButton(it))
            row
        }

        return SendMessage(chatId.toString(), text).apply {
            replyMarkup = keyboardMarkup
        }
    }
}