package com.splitwise.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.splitwise.app.data.BiometricVault
import javax.crypto.Cipher

enum class BiometricState { Ready, NotEnrolled, Unavailable }

/** Thin wrapper over BiometricPrompt: the Keystore key only works for the cipher handed to the prompt. */
object Biometric {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun state(context: Context): BiometricState = when (BiometricManager.from(context).canAuthenticate(AUTH)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricState.Ready
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricState.NotEnrolled
        else -> BiometricState.Unavailable
    }

    /** Encrypts [plain] after a fingerprint check; returns (ciphertext, iv). */
    fun encrypt(activity: FragmentActivity, plain: ByteArray, onDone: (ByteArray, ByteArray) -> Unit, onError: (String) -> Unit) {
        val cipher = runCatching { BiometricVault.encryptCipher() }.getOrElse { return onError("Fingerprint login isn't available on this phone.") }
        prompt(activity, "Turn on fingerprint login", "Confirm with your fingerprint", cipher, onError) { done ->
            onDone(done.doFinal(plain), done.iv)
        }
    }

    /** Decrypts a blob stored by [encrypt] after a fingerprint check. */
    fun decrypt(activity: FragmentActivity, blob: ByteArray, iv: ByteArray, onDone: (ByteArray) -> Unit, onError: (String) -> Unit, onInvalidated: () -> Unit) {
        val cipher = try {
            BiometricVault.decryptCipher(iv)
        } catch (e: Exception) {
            if (BiometricVault.isInvalidated(e)) onInvalidated() else onError("Fingerprint login isn't available. Use your password.")
            return
        }
        prompt(activity, "Log in to SplitEase", "Use your fingerprint", cipher, onError) { done -> onDone(done.doFinal(blob)) }
    }

    private fun prompt(activity: FragmentActivity, title: String, subtitle: String, cipher: Cipher, onError: (String) -> Unit, onSuccess: (Cipher) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val done = result.cryptoObject?.cipher ?: return onError("Fingerprint check failed.")
                runCatching { onSuccess(done) }.onFailure { onError("Fingerprint check failed.") }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // The user backing out is not an error worth showing.
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED) onError(errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title).setSubtitle(subtitle).setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(AUTH).build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
    return null
}
