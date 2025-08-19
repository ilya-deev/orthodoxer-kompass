package org.orthodoxer.kompass.bot

import org.orthodoxer.kompass.service.BotService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.starter.SpringWebhookBot

@Component
class OrthodoxerWebhookBot(
    private val botService: BotService,
    @Value("\${telegram.bot-token}") private val botToken: String,
    @Value("\${telegram.bot-username}") private val botUsername: String,
    @Value("\${telegram.webhook-path}") private val webhookPath: String
) : SpringWebhookBot(SetWebhook().apply { url = webhookPath }) {

    override fun getBotToken(): String = botToken
    override fun getBotUsername(): String = botUsername
    override fun getBotPath(): String = webhookPath

    override fun onWebhookUpdateReceived(update: Update): SendMessage? {
        println("✅ Получено обновление от Telegram: $update")
        return botService.processUpdate(update)
    }
}
