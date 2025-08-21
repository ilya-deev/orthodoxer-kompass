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

        if (!langMap.containsKey(chatId)) {
            if (text.lowercase() == "ru" || text.lowercase() == "de") {
                langMap[chatId] = text.lowercase()
                return moduleSelectionMessage(chatId)
            }
            return languageSelectionMessage(chatId)
        }

        val lang = langMap[chatId]!!

        if (session.module?.isBlank() == true) {
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
                else -> moduleSelectionMessage(chatId)
            }
        }

        return handleAnswer(chatId, session, text, lang)
    }

    private fun languageSelectionMessage(chatId: Long): SendMessage {
        val msg = SendMessage(chatId.toString(), "Выберите язык / Sprache wählen:")
        val row = KeyboardRow()
        row.add(KeyboardButton("ru"))
        row.add(KeyboardButton("de"))

        val markup = ReplyKeyboardMarkup()
        markup.keyboard = listOf(row)
        markup.resizeKeyboard = true
        markup.oneTimeKeyboard = true
        msg.replyMarkup = markup

        return msg
    }

    private fun moduleSelectionMessage(chatId: Long): SendMessage {
        val lang = langMap[chatId] ?: "ru"
        val text = when (lang) {
            "de" -> "Wähle ein Modul:\n1. Ich will Pate werden\n2. Zum ersten Mal in der Kirche"
            else -> "Выберите модуль:\n1. Хочу быть крестным\n2. Первый раз в церкви"
        }
        val msg = SendMessage(chatId.toString(), text)
        val row = KeyboardRow()
        row.add(KeyboardButton("1"))
        row.add(KeyboardButton("2"))
        val markup = ReplyKeyboardMarkup()
        markup.keyboard = listOf(row)
        markup.resizeKeyboard = true
        markup.oneTimeKeyboard = true
        msg.replyMarkup = markup
        return msg
    }

    private fun nextQuestion(chatId: Long, session: SessionState, lang: String): SendMessage {
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")

        if (session.currentQuestion >= questions.size) {
            val resultText = buildString {
                append(
                    when (lang) {
                        "de" -> "Du hast den Test abgeschlossen.\n"
                        else -> "Вы прошли тест.\n"
                    }
                )
                append("Правильных ответов: ${session.correctAnswers} из ${questions.size}.\n")
                if (session.wrongAnswers.isNotEmpty()) {
                    append(
                        when (lang) {
                            "de" -> "\nFragen mit falschen Antworten:\n"
                            else -> "\nВопросы с неправильными ответами:\n"
                        }
                    )
                    session.wrongAnswers.forEach {
                        append("- ${questions[it].text[lang]}\n")
                    }
                }
            }
            sessionMap.remove(chatId)
            return SendMessage(chatId.toString(), resultText)
        }

        val q = questions[session.currentQuestion]
        val options = q.options.withIndex().map { "${it.index + 1}. ${it.value}" }

        val text = "${q.text[lang]}\n" + options.joinToString("\n")

        val msg = SendMessage(chatId.toString(), text)
        val markup = ReplyKeyboardMarkup()
        markup.keyboard = q.options.chunked(2).map { rowOpts ->
            val row = KeyboardRow()
            rowOpts.forEachIndexed { i, opt ->
                val index = q.options.indexOf(opt) + 1
                row.add(KeyboardButton(index.toString()))
            }
            row
        }
        markup.resizeKeyboard = true
        markup.oneTimeKeyboard = true
        msg.replyMarkup = markup

        return msg
    }

    private fun handleAnswer(chatId: Long, session: SessionState, text: String, lang: String): SendMessage {
        val index = text.toIntOrNull()?.minus(1)
        val questions = modules[session.module] ?: return SendMessage(chatId.toString(), "Модуль не найден.")
        val q = questions.getOrNull(session.currentQuestion) ?: return SendMessage(chatId.toString(), "Вопрос не найден.")

        if (index == null || index !in q.options.indices) {
            return SendMessage(chatId.toString(), when (lang) {
                "de" -> "Bitte gib eine gültige Nummer ein."
                else -> "Пожалуйста, введите номер варианта."
            })
        }

        if (index == q.correctIndex) {
            session.correctAnswers++
        } else {
            session.wrongAnswers.add(session.currentQuestion)
        }

        session.currentQuestion++
        return nextQuestion(chatId, session, lang)
    }
}