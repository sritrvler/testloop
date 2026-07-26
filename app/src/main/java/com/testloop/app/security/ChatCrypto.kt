package com.testloop.app.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Tink owns the cryptographic implementation. This class only manages a local
 * HPKE keyset and serializes the public half for the Supabase key directory.
 */
class ChatCrypto(context: Context) {
    companion object {
        private const val PREF_FILE = "testloop_chat_keys"
        private const val KEYSET_NAME = "chat_hpke_keyset"
        private const val MASTER_KEY_URI = "android-keystore://testloop-chat-master-key"

        private val HPKE_TEMPLATE: KeyTemplate = KeyTemplate.createFrom(
            HpkeParameters.builder()
                .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                .setVariant(HpkeParameters.Variant.NO_PREFIX)
                .build()
        )
    }

    private val keysetHandle: KeysetHandle

    init {
        HybridConfig.register()
        keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, PREF_FILE, KEYSET_NAME)
            .withMasterKeyUri(MASTER_KEY_URI)
            .withKeyTemplate(HPKE_TEMPLATE)
            .build()
            .keysetHandle
    }

    val publicKeyBase64: String
        get() {
            val output = ByteArrayOutputStream()
            keysetHandle.getPublicKeysetHandle().writeNoSecret(BinaryKeysetWriter.withOutputStream(output))
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }

    fun encrypt(plaintext: String, recipientPublicKeyBase64: String, associatedData: String): String {
        val recipient = readPublicKeyset(recipientPublicKeyBase64)
        val encryptor = recipient.getPrimitive(HybridEncrypt::class.java)
        val ciphertext = encryptor.encrypt(
            plaintext.toByteArray(StandardCharsets.UTF_8),
            associatedData.toByteArray(StandardCharsets.UTF_8)
        )
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(ciphertextBase64: String, associatedData: String): String {
        val decryptor = keysetHandle.getPrimitive(HybridDecrypt::class.java)
        val plaintext = decryptor.decrypt(
            Base64.decode(ciphertextBase64, Base64.DEFAULT),
            associatedData.toByteArray(StandardCharsets.UTF_8)
        )
        return plaintext.toString(StandardCharsets.UTF_8)
    }

    private fun readPublicKeyset(encoded: String): KeysetHandle =
        KeysetHandle.readNoSecret(
            BinaryKeysetReader.withBytes(Base64.decode(encoded, Base64.DEFAULT))
        )
}
