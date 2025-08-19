package org.orthodoxer.kompass.controller

import org.orthodoxer.kompass.service.BotService
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.meta.api.objects.Update

@RestController

class BotController(
    private val botService: BotService
) {
    @PostMapping("/webhook")
    fun onWebhook(@RequestBody update: Update) {
        println("✅ Получено обновление от Telegram: $update")
        botService.processUpdate(update)
    }
}