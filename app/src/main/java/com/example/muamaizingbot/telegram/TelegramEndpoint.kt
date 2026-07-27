package com.example.muamaizingbot.telegram

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Telegram bot token embedded encrypted in the APK (not shown in UI / prefs).
 */
internal object TelegramEndpoint {

    // Run scripts/embed_telegram_token.py <token> after creating the bot.
    private val WRAP_A = "CRdY9uOcyEDKpAs9LtY0bg=="
    private val WRAP_B = "zohGSEXwZzAag0ACV+n7RyU50mMOAPcrHgg0iWSvGLNI7nTSZtTmQoBVNnCGDhJU"

    @Volatile
    private var cached: String? = null

    fun botToken(): String {
        cached?.let { return it }
        return runCatching {
            decrypt().trim()
        }.getOrDefault("").also { cached = it }
    }

    fun isConfigured(): Boolean = botToken().isNotBlank()

    private fun decrypt(): String {
        val key = SecretKeySpec(deriveKey(), "AES")
        val iv = IvParameterSpec(Base64.decode(WRAP_A, Base64.DEFAULT))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val plain = cipher.doFinal(Base64.decode(WRAP_B, Base64.DEFAULT))
        return String(plain, Charsets.UTF_8)
    }

    private fun deriveKey(): ByteArray {
        val a = charArrayOf('m', 'u', 'a', 'm', 'a', 'i', 'z', 'i', 'n', 'g')
        val b = charArrayOf('|', 't', 'e', 'l', 'e', 'g', 'r', 'a', 'm', '|', 'v', '1', '|')
        val c = charArrayOf(
            'c', 'o', 'm', '.', 'e', 'x', 'a', 'm', 'p', 'l', 'e', '.',
            'm', 'u', 'a', 'm', 'a', 'i', 'z', 'i', 'n', 'g', 'b', 'o', 't',
        )
        val md = MessageDigest.getInstance("SHA-256")
        md.update(String(a).toByteArray(Charsets.UTF_8))
        md.update(String(b).toByteArray(Charsets.UTF_8))
        md.update(String(c).toByteArray(Charsets.UTF_8))
        return md.digest().copyOf(16)
    }
}
