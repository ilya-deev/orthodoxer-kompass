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

        val session = sessionMap.getOrPut(chatId) { SessionState() }
        val lang = langMap[chatId]

        // 1. Выбор языка
        if (lang == null) {
            return when (text) {
                "Русский" -> {
                    langMap[chatId] = "ru"
                    startModuleSelection(chatId, "ru")
                }
                "Deutsch" -> {
                    langMap[chatId] = "de"
                    startModuleSelection(chatId, "de")
                }
                else -> {
                    val msg = SendMessage(chatId.toString(), "Выберите язык / Sprache auswählen")
                    msg.replyMarkup = ReplyKeyboardMarkup(
                        listOf(
                            KeyboardRow(listOf(KeyboardButton("Русский"), KeyboardButton("Deutsch")))
                        )
                    ).apply {
                        resizeKeyboard = true
                        oneTimeKeyboard = true
                    }
                    msg
                }
            }
        }

        // 2. Выбор модуля
        if (session.module.isEmpty()) {
            return when (text) {
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
                else -> startModuleSelection(chatId, langMap[chatId]!!)
            }
        }

        // 3. Обработка ответа
        return handleAnswer(chatId, session, text)
    }

    private fun startModuleSelection(chatId: Long, lang: String): SendMessage {
        val text = when (lang) {
            "ru" -> "Выберите модуль:\n1. Хочу быть крестным\n2. Первый раз в церкви"
            "de" -> "Wähle ein Modul:\n1. Ich will Pate werden\n2. Zum ersten Mal in der Kirche"
            else -> "Выберите модуль"
        }
        return SendMessage(chatId.toString(), text)
    }

    private fun nextQuestion(chatId: Long, session: SessionState): SendMessage {
        val lang = langMap[chatId] ?: "ru"
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")

        if (session.currentQuestion >= questions.size) {
            val wrongQuestions = session.wrongAnswers.mapNotNull { wrongId ->
                questions.find { it.id == wrongId }?.text?.get(lang)
            }

            val resultText = buildString {
                append(
                    when (lang) {
                        "ru" -> "Вы прошли тест. Правильных ответов: ${session.correctAnswers} из ${questions.size}."
                        "de" -> "Sie haben den Test abgeschlossen. Richtige Antworten: ${session.correctAnswers} von ${questions.size}."
                        else -> ""
                    }
                )
                if (wrongQuestions.isNotEmpty()) {
                    append("\n\n")
                    append(
                        when (lang) {
                            "ru" -> "Вопросы с ошибками:\n"
                            "de" -> "Fragen mit Fehlern:\n"
                            else -> ""
                        }
                    )
                    wrongQuestions.forEach {
                        append("- $it\n")
                    }
                }
            }

            sessionMap.remove(chatId)

            val msg = SendMessage(chatId.toString(), resultText)
            msg.replyMarkup = ReplyKeyboardMarkup(
                listOf(
                    KeyboardRow(listOf(KeyboardButton("🌐 Перейти к модулю"), KeyboardButton("🔁 Завершить тест")))
                )
            ).apply {
                resizeKeyboard = true
                oneTimeKeyboard = true
            }

            return msg
        }

        val q = questions[session.currentQuestion]
        val optionsKeyboard = q.options.mapIndexed { index, _ ->
            KeyboardRow(listOf(KeyboardButton((index + 1).toString())))
        }

        val msg = SendMessage(chatId.toString(), q.text[lang] ?: "Вопрос")
        msg.replyMarkup = ReplyKeyboardMarkup().apply {
            keyboard = optionsKeyboard
            resizeKeyboard = true
            oneTimeKeyboard = true
        }

        return msg
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String): SendMessage {
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        val lang = langMap[chatId] ?: "ru"

        if (text == "🔁 Завершить тест") {
            sessionMap.remove(chatId)
            return SendMessage(chatId.toString(), if (lang == "ru") "Тест завершён. Нажмите /start." else "Test beendet. Tippen Sie /start.")
        }

        if (text == "🌐 Перейти к модулю") {
            val url = when (session.module) {
                "baptism" -> "https://rocor-ingolstadt.de/"
                "first_visit" -> "https://rocor-ingolstadt.de/"
                else -> "https://orthodoxer-kompass.de"
            }
            return SendMessage(chatId.toString(), url)
        }

        val index = text.toIntOrNull()?.minus(1)
        val currentQuestion = questions.getOrNull(session.currentQuestion)

        if (index == null || currentQuestion == null) {
            return SendMessage(chatId.toString(), if (lang == "ru") "Введите номер варианта." else "Bitte geben Sie die Nummer der Option ein.")
        }

        if (index == currentQuestion.correctIndex) {
            session.correctAnswers++
        } else {
            session.wrongAnswers.add(currentQuestion.id)
        }

        session.currentQuestion++
        return nextQuestion(chatId, session)
    }
}