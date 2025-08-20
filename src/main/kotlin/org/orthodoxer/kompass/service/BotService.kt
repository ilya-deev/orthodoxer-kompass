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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton


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

        // язык еще не выбран — предложим
        val lang = langMap[chatId]
        if (lang == null) {
            return when (text.lowercase()) {
                "русский" -> {
                    langMap[chatId] = "ru"
                    askModuleSelection(chatId, "ru")
                }
                "deutsch" -> {
                    langMap[chatId] = "de"
                    askModuleSelection(chatId, "de")
                }
                else -> {
                    val keyboard = ReplyKeyboardMarkup().apply {
                        keyboard = listOf(
                            KeyboardRow(listOf(KeyboardButton("Русский"))),
                            KeyboardRow(listOf(KeyboardButton("Deutsch")))
                        )
                        resizeKeyboard = true
                        oneTimeKeyboard = true
                    }
                    SendMessage(chatId.toString(), "Выберите язык / Wähle eine Sprache:").apply {
                        replyMarkup = keyboard
                    }
                }
            }
        }

        val session = sessionMap.getOrPut(chatId) { SessionState() }

        return when {
            text == "/start" -> askModuleSelection(chatId, lang)
            session.module?.isEmpty() == true -> when (text) {
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
                else -> SendMessage(chatId.toString(), when (lang) {
                    "ru" -> "Пожалуйста, выберите модуль: 1 или 2"
                    "de" -> "Bitte wähle ein Modul: 1 oder 2"
                    else -> "Please select module: 1 or 2"
                })
            }

            else -> handleAnswer(chatId, session, text, lang)
        }
    }

    private fun askModuleSelection(chatId: Long, lang: String): SendMessage {
        val text = when (lang) {
            "ru" -> "Выберите модуль:\n1. Хочу быть крестным\n2. Первый раз в церкви"
            "de" -> "Wähle ein Modul:\n1. Ich möchte Taufpate werden\n2. Zum ersten Mal in der Kirche"
            else -> "Choose a module:\n1. Baptism\n2. First time in church"
        }

        val keyboard = ReplyKeyboardMarkup().apply {
            keyboard = listOf(
                KeyboardRow(listOf(KeyboardButton("1"))),
                KeyboardRow(listOf(KeyboardButton("2")))
            )
            resizeKeyboard = true
            oneTimeKeyboard = true
        }

        return SendMessage(chatId.toString(), text).apply {
            replyMarkup = keyboard
        }
    }

    private fun nextQuestion(chatId: Long, session: SessionState, lang: String): SendMessage {
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        if (session.currentQuestion >= questions.size) {
            sessionMap.remove(chatId)
            return SendMessage(
                chatId.toString(),
                when (lang) {
                    "ru" -> "Вы прошли тест. Правильных ответов: ${session.correctAnswers} из ${questions.size}."
                    "de" -> "Du hast den Test abgeschlossen. Richtige Antworten: ${session.correctAnswers} von ${questions.size}."
                    else -> "Test completed. Correct answers: ${session.correctAnswers} of ${questions.size}."
                }
            )
        }

        val q = questions[session.currentQuestion]
        val questionText = q.text[lang] ?: q.text["ru"] ?: "Вопрос"
        val optionsText = q.options.withIndex().joinToString("\n") { (i, opt) -> "${i + 1}. $opt" }

        val keyboard = ReplyKeyboardMarkup().apply {
            keyboard = q.options.indices.map { i -> KeyboardRow(listOf(KeyboardButton((i + 1).toString()))) }
            resizeKeyboard = true
            oneTimeKeyboard = true
        }

        return SendMessage(chatId.toString(), "$questionText\n$optionsText").apply {
            replyMarkup = keyboard
        }
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String, lang: String): SendMessage {
        val index = text.toIntOrNull()?.minus(1)
            ?: return SendMessage(chatId.toString(), if (lang == "ru") "Введите номер варианта." else "Bitte gib die Nummer ein.")

        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        val q = questions.getOrNull(session.currentQuestion) ?: return SendMessage(chatId.toString(), "Вопрос не найден.")

        if (index == q.correctIndex) {
            session.correctAnswers++
        }

        session.currentQuestion++
        return nextQuestion(chatId, session, lang)
    }
}
