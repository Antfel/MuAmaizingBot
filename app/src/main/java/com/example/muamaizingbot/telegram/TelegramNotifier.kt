package com.example.muamaizingbot.telegram

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class TelegramSendResult {
    data object Ok : TelegramSendResult()
    data class Failed(val message: String) : TelegramSendResult()
}

object TelegramNotifier {

    private const val TAG = "Telegram"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    fun sendTestMessage(): TelegramSendResult {
        val chatId = TelegramStore.chatId()
        if (chatId.isBlank()) {
            return TelegramSendResult.Failed("Configura tu Chat ID")
        }
        if (!TelegramEndpoint.isConfigured()) {
            return TelegramSendResult.Failed("Token del bot no embebido en este build")
        }
        return sendMessage(
            chatId = chatId,
            text = "MU Amaizing Bot — prueba OK.\nRecibirás avisos aquí si el juego se desconecta o entra en mantenimiento.",
        )
    }

    fun sendDisconnectAlert(reason: String): TelegramSendResult {
        if (!TelegramStore.isReadyForSend()) {
            return TelegramSendResult.Failed("Telegram no configurado")
        }
        return sendMessage(
            chatId = TelegramStore.chatId(),
            text = "⚠️ MU Bot — $reason",
        )
    }

    private fun sendMessage(chatId: String, text: String): TelegramSendResult {
        val token = TelegramEndpoint.botToken()
        if (token.isBlank()) {
            return TelegramSendResult.Failed("Token del bot no configurado")
        }

        var conn: HttpURLConnection? = null
        return try {
            val encodedChat = URLEncoder.encode(chatId, StandardCharsets.UTF_8.name())
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
            val url =
                "https://api.telegram.org/bot$token/sendMessage?chat_id=$encodedChat&text=$encodedText"

            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()

            val json = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (code in 200..299 && json.optBoolean("ok", false)) {
                TelegramSendResult.Ok
            } else {
                val desc = json.optString("description").ifBlank { "HTTP $code" }
                Log.w(TAG, "sendMessage failed: $desc")
                TelegramSendResult.Failed(desc)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "sendMessage error: ${t.message}")
            TelegramSendResult.Failed("No se pudo conectar con Telegram")
        } finally {
            conn?.disconnect()
        }
    }
}
