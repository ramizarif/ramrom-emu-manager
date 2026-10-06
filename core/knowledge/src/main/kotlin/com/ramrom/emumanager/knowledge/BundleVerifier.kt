package com.ramrom.emumanager.knowledge

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Signature verification for knowledge bundles.
 * Ed25519 implementation uses standard java.security APIs (available on API 33+).
 */
interface BundleVerifier {
    fun verify(bytes: ByteArray, signature: ByteArray, publicKey: String): Boolean
}

/**
 * No-op implementation for bundled assets (no signature).
 */
class NoOpBundleVerifier : BundleVerifier {
    override fun verify(bytes: ByteArray, signature: ByteArray, publicKey: String): Boolean = true
}

/**
 * Ed25519 signature verification. Public key is a Base64-encoded X.509 SubjectPublicKeyInfo.
 */
class Ed25519BundleVerifier : BundleVerifier {
    override fun verify(bytes: ByteArray, signature: ByteArray, publicKey: String): Boolean = try {
        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))
        val keyFactory = KeyFactory.getInstance("Ed25519")
        val key = keyFactory.generatePublic(keySpec)
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(key)
        sig.update(bytes)
        sig.verify(signature)
    } catch (e: Exception) {
        false
    }
}
