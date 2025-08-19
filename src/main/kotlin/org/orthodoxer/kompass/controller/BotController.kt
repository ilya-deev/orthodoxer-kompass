package org.orthodoxer.kompass.controller

import org.orthodoxer.kompass.bot.OrthodoxerWebhookBot
import org.orthodoxer.kompass.service.BotService
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.meta.api.objects.Update

@RestController
class BotController(
    private val bot: OrthodoxerWebhookBot,
    private val botService: BotService
) {
    @PostMapping("/webhook")
    fun onWebhook(@RequestBody update: Update) {
        println("✅ Update получен: $update")
        val response = botService.processUpdate(update)
        if (response != null) {
            bot.execute(response)
        }
    }
}
