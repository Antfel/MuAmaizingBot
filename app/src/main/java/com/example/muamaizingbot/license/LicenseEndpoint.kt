package com.example.muamaizingbot.license

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * License API base URL embedded encrypted in the APK (not shown in UI / prefs).
 * Obfuscation against casual reverse engineering — not a hard security boundary.
 */
internal object LicenseEndpoint {

    // AES-128-CBC ciphertext + IV (Base64). Plaintext is never stored as a string constant.
    private val WRAP_A = "viPK98N7+J5mgCW1qvpqDQ=="
    private val WRAP_B = "b6q6b71217cdw4kfDUJ0Hv3gG2HtZ4hPbRxEKMj4Vgs="

    @Volatile
    private var cached: String? = null

    fun serverUrl(): String {
        cached?.let { return it }
        val url = decrypt().trim().trimEnd('/')
        cached = url
        return url
    }

    private fun decrypt(): String {
        val key = SecretKeySpec(deriveKey(), "AES")
        val iv = IvParameterSpec(Base64.decode(WRAP_A, Base64.DEFAULT))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val plain = cipher.doFinal(Base64.decode(WRAP_B, Base64.DEFAULT))
        return String(plain, Charsets.UTF_8)
    }

    /** Key assembled at runtime so the URL key material is not one contiguous string. */
    private fun deriveKey(): ByteArray {
        val a = charArrayOf('m', 'u', 'a', 'm', 'a', 'i', 'z', 'i', 'n', 'g')
        val b = charArrayOf('|', 'l', 'i', 'c', 'e', 'n', 's', 'e', '|', 'v', '1', '|')
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
