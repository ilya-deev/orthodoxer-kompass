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

    private val supportedLanguages = listOf("ru", "de")

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
        val text = message.text?.trim() ?: return null

        val session = sessionMap.getOrPut(chatId) { SessionState() }

        // 1. Выбор языка
        if (!langMap.containsKey(chatId)) {
            return when (text.lowercase()) {
                "русский" -> {
                    langMap[chatId] = "ru"
                    askModule(chatId, "ru")
                }
                "deutsch" -> {
                    langMap[chatId] = "de"
                    askModule(chatId, "de")
                }
                else -> chooseLanguage(chatId)
            }
        }

        val lang = langMap[chatId] ?: "ru"

        // 2. Выбор модуля
        if (session.module == null) {
            return when (text) {
                "1" -> {
                    session.module = "baptism"
                    session.currentQuestion = 0
                    nextQuestion(chatId, session, lang)
                }
                "2" -> {
                    session.module = "first_visit"
                    session.currentQuestion = 0
                    nextQuestion(chatId, session, lang)
                }
                else -> askModule(chatId, lang)
            }
        }

        // 3. Ответ на вопрос
        return handleAnswer(chatId, session, text, lang)
    }

    private fun chooseLanguage(chatId: Long): SendMessage {
        val message = SendMessage(chatId.toString(), "Выберите язык / Sprache wählen:")
        val keyboard = ReplyKeyboardMarkup().apply {
            keyboard = listOf(
                KeyboardRow(listOf(KeyboardButton("Русский"), KeyboardButton("Deutsch")))
            )
            resizeKeyboard = true
            oneTimeKeyboard = true
        }
        message.replyMarkup = keyboard
        return message
    }

    private fun askModule(chatId: Long, lang: String): SendMessage {
        val text = when (lang) {
            "de" -> "Wähle ein Modul:\n1. Ich will Pate werden\n2. Zum ersten Mal in der Kirche"
            else -> "Выберите модуль:\n1. Хочу быть крестным\n2. Первый раз в церкви"
        }
        return SendMessage(chatId.toString(), text)
    }

    private fun nextQuestion(chatId: Long, session: SessionState, lang: String): SendMessage {
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        if (session.currentQuestion >= questions.size) {
            val result = when (lang) {
                "de" -> "Du hast das Quiz abgeschlossen. Richtige Antworten: ${session.correctAnswers} von ${questions.size}."
                else -> "Вы прошли тест. Правильных ответов: ${session.correctAnswers} из ${questions.size}."
            }
            sessionMap.remove(chatId)
            return SendMessage(chatId.toString(), result)
        }

        val q = questions[session.currentQuestion]
        val options = q.options.withIndex().joinToString("\n") { (i, opt) -> "${i + 1}. $opt" }
        val questionText = q.text[lang] ?: q.text["ru"] ?: "Вопрос"

        return SendMessage(chatId.toString(), "$questionText\n$options")
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String, lang: String): SendMessage {
        val index = text.toIntOrNull()?.minus(1)
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        val q = questions.getOrNull(session.currentQuestion) ?: return SendMessage(chatId.toString(), "Вопрос не найден.")

        if (index == q.correctIndex) {
            session.correctAnswers++
        }

        session.currentQuestion++
        return nextQuestion(chatId, session, lang)
    }
}