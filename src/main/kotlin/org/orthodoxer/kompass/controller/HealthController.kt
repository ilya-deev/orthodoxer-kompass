package org.orthodoxer.kompass.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @author IDeev
 * Erstellt am 19.08.2025
 */
@RestController
class HealthController {

    @GetMapping("/")
    fun index(): String = "Orthodoxer Kompass läuft"
}