package lovechain.core

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object LoveBlockSignatureVerifier {
    fun verify(blockHash: String, blockSignature: BlockSignature): Boolean {
        return try {
            val publicKeyBytes = Base64.getDecoder().decode(blockSignature.publicKeyBase64)
            val signatureBytes = Base64.getDecoder().decode(blockSignature.signatureBase64)
            val publicKey = KeyFactory
                .getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(blockHash.toByteArray(Charsets.UTF_8))
            verifier.verify(signatureBytes)
        } catch (_: RuntimeException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    fun fingerprint(publicKeyBase64: String): String {
        val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)

        return digestBytes
            .take(8)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
