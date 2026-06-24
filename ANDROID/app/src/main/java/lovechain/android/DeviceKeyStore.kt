package lovechain.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import lovechain.core.BlockSignature
import lovechain.core.LoveBlockSignatureVerifier
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class DeviceKeyStore {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun publicKeyBase64(): String {
        ensureKeyExists()
        val publicKeyBytes = keyStore.getCertificate(KeyAlias).publicKey.encoded
        return Base64.getEncoder().encodeToString(publicKeyBytes)
    }

    fun fingerprint(): String {
        return LoveBlockSignatureVerifier.fingerprint(publicKeyBase64())
    }

    fun signBlockHash(blockHash: String, timestamp: Long = System.currentTimeMillis()): BlockSignature {
        ensureKeyExists()
        val privateKey = keyStore.getKey(KeyAlias, null) as PrivateKey
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(blockHash.toByteArray(Charsets.UTF_8))

        return BlockSignature(
            signerFingerprint = fingerprint(),
            publicKeyBase64 = publicKeyBase64(),
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
            timestamp = timestamp
        )
    }

    private fun ensureKeyExists() {
        if (keyStore.containsAlias(KeyAlias)) {
            return
        }

        val parameterSpec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    private companion object {
        const val KeyAlias = "lovechain_device_signing_key"
    }
}
