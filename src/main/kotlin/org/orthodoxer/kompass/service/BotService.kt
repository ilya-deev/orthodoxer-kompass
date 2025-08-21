package org.orthodoxer.kompass.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.orthodoxer.kompass.model.Question
import org.orthodoxer.kompass.model.SessionState
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
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
        val callback = update.callbackQuery
        if (callback != null) {
            val chatId = callback.message.chatId
            val data = callback.data

            if (data == "finish") {
                sessionMap.remove(chatId)
                val msg = SendMessage(chatId.toString(), "Нажмите /start, чтобы начать заново.")
                msg.replyMarkup = ReplyKeyboardMarkup().apply {
                    keyboard = listOf(KeyboardRow(listOf(KeyboardButton("/start"))))
                    resizeKeyboard = true
                    oneTimeKeyboard = true
                }
                return msg
            }
        }

        val message = update.message ?: return null
        val chatId = message.chatId
        val text = message.text ?: return null

        val lang = langMap.getOrPut(chatId) { "" }
        val session = sessionMap.getOrPut(chatId) { SessionState() }

        return when {
            text == "/start" -> {
                sessionMap.remove(chatId)
                langMap[chatId] = ""
                val msg = SendMessage(chatId.toString(), "Выберите язык / Sprache wählen:")
                msg.replyMarkup = ReplyKeyboardMarkup().apply {
                    keyboard = listOf(
                        KeyboardRow(listOf(KeyboardButton("Русский"), KeyboardButton("Deutsch")))
                    )
                    resizeKeyboard = true
                    oneTimeKeyboard = true
                }
                msg
            }

            lang.isEmpty() && (text == "Русский" || text == "Deutsch") -> {
                val selected = if (text == "Русский") "ru" else "de"
                langMap[chatId] = selected
                val msg = SendMessage(
                    chatId.toString(),
                    if (selected == "ru") "Выберите модуль:" else "Modul wählen:"
                )
                msg.replyMarkup = ReplyKeyboardMarkup().apply {
                    keyboard = listOf(
                        KeyboardRow(
                            listOf(
                                KeyboardButton(if (selected == "ru") "Хочу быть крестным" else "Ich will Pate werden"),
                                KeyboardButton(if (selected == "ru") "Первый раз в церкви" else "Zum ersten Mal in der Kirche")
                            )
                        )
                    )
                    resizeKeyboard = true
                    oneTimeKeyboard = true
                }
                msg
            }

            langMap[chatId] != null && session.module.isEmpty() -> {
                when (text) {
                    "Хочу быть крестным", "Ich will Pate werden" -> {
                        session.module = "baptism"
                        session.currentQuestion = 0
                        nextQuestion(chatId, session)
                    }

                    "Первый раз в церкви", "Zum ersten Mal in der Kirche" -> {
                        session.module = "first_visit"
                        session.currentQuestion = 0
                        nextQuestion(chatId, session)
                    }

                    else -> SendMessage(chatId.toString(), "Пожалуйста, выберите модуль.")
                }
            }

            else -> handleAnswer(chatId, session, text)
        }
    }

    private fun nextQuestion(chatId: Long, session: SessionState): SendMessage {
        val lang = langMap[chatId] ?: "ru"
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")

        if (session.currentQuestion >= questions.size) {
            val msg = SendMessage(chatId.toString(),
                if (lang == "ru")
                    "Вы прошли тест. Правильных ответов: ${session.correctAnswers} из ${questions.size}."
                else
                    "Du hast den Test abgeschlossen. Richtige Antworten: ${session.correctAnswers} von ${questions.size}."
            )

            val wrongList = session.wrongAnswers.joinToString("\n") {
                val q = questions[it]
                "❌ ${q.text[lang]}"
            }

            if (session.wrongAnswers.isNotEmpty()) {
                msg.text += "\n\n" + (if (lang == "ru") "Неправильные ответы:" else "Falsche Antworten:") +
                        "\n$wrongList"
            }

            //val siteLink = "https://orthodoxer-kompass.de/module/${session.module}"
            val siteLink = "https://rocor-ingolstadt.de/"
            msg.replyMarkup = InlineKeyboardMarkup().apply {
                keyboard = listOf(
                    listOf(
                        InlineKeyboardButton.builder()
                            .text(if (lang == "ru") "Перейти к модулю" else "Zum Modul")
                            .url(siteLink)
                            .build()
                    ),
                    listOf(
                        InlineKeyboardButton.builder()
                            .text(if (lang == "ru") "Завершить тест" else "Test beenden")
                            .callbackData("finish")
                            .build()
                    )
                )
            }

            return msg
        }

        val q = questions[session.currentQuestion]
        val options = q.options.withIndex().joinToString("\n") { (i, opt) -> "${i + 1}. $opt" }

        val msg = SendMessage(chatId.toString(), "${q.text[lang]}\n\n$options")
        val keyboard = q.options.mapIndexed { index, _ ->
            KeyboardRow(listOf(KeyboardButton((index + 1).toString())))
        }

        msg.replyMarkup = ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            resizeKeyboard = true
            oneTimeKeyboard = true
        }

        return msg
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String): SendMessage {
        val index = text.toIntOrNull()?.minus(1) ?: return SendMessage(chatId.toString(), "Введите номер ответа.")
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        val q = questions.getOrNull(session.currentQuestion) ?: return SendMessage(chatId.toString(), "Вопрос не найден.")
        val lang = langMap[chatId] ?: "ru"

        if (index == q.correctIndex) {
            session.correctAnswers++
        } else {
            session.wrongAnswers.add(session.currentQuestion)
        }

        session.currentQuestion++
        return nextQuestion(chatId, session)
    }
}