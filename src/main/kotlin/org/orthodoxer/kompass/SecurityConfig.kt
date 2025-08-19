package org.orthodoxer.kompass

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * @author IDeev
 * Erstellt am 19.08.2025
 */
@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/webhook").permitAll()
                    .anyRequest().permitAll()
            }
        return http.build()
    }
}