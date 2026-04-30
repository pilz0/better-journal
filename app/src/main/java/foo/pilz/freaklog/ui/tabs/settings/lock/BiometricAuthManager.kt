/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.settings.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.di.ApplicationScope
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Possible states of biometric availability on the device.
 *
 * Maps the integer codes returned by [BiometricManager.canAuthenticate] to a
 * Kotlin enum so callers don't need to depend on the AndroidX constants.
 */
enum class BiometricAvailability(val message: String) {
    AVAILABLE("Strong biometric authentication is available."),
    NO_HARDWARE("This device has no biometric hardware."),
    HARDWARE_UNAVAILABLE("Biometric hardware is currently unavailable."),
    NONE_ENROLLED("No strong biometrics are enrolled. Set one up in system settings to enable the lock."),
    UNSUPPORTED("Strong biometric authentication is not supported on this device."),
}

/**
 * Singleton that owns the journal's app-lock state.
 *
 * It exposes:
 *  - flows for the user-facing settings (`isLockEnabled`, `lockTimeOption`)
 *  - a [shouldLock] state-flow telling the UI whether the lock screen must be shown
 *  - lifecycle hooks ([onAppPaused] / [onAppResumed]) used by the activity to
 *    record the last active timestamp and re-evaluate the lock
 *  - [authenticate] which displays the system biometric prompt and unlocks on success
 *
 * The math behind "did enough time pass to lock again?" lives in the pure
 * [shouldLockNow] helper, which is unit-tested separately.
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPreferences: UserPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AUTH_KEY_ALIAS = "freaklog_app_lock_auth"
        const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS7Padding"
        const val AUTH_CHALLENGE = "freaklog-app-lock"
        const val STRONG_BIOMETRIC_AUTHENTICATOR = BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

    val isLockEnabledFlow: Flow<Boolean> = userPreferences.isLockEnabledFlow
    val lockTimeOptionFlow: Flow<LockTimeOption> = userPreferences.lockTimeOptionFlow

    private val _isUnlocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** True when the user has authenticated for the current foreground session. */
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    /**
     * True when the lock screen must be shown over the rest of the UI.
     *
     * Combines the persisted lock settings with the in-memory [isUnlocked] flag.
     */
    val shouldLock: StateFlow<Boolean> = combine(
        userPreferences.isLockEnabledFlow,
        _isUnlocked,
    ) { enabled, unlocked -> enabled && !unlocked }
        // Default to "locked" on cold start / process death so that we don't briefly
        // render the journal contents before DataStore emits the persisted setting.
        // The combine flips this to false within a frame once prefs report the lock
        // is disabled.
        .stateIn(applicationScope, SharingStarted.Eagerly, initialValue = true)

    fun setLockEnabled(enabled: Boolean) {
        applicationScope.launch {
            userPreferences.saveLockEnabled(enabled)
            // Newly enabling the lock should immediately require auth on next foreground.
            if (enabled) {
                _isUnlocked.value = false
            } else {
                _isUnlocked.value = true
            }
        }
    }

    fun setLockTimeOption(option: LockTimeOption) {
        applicationScope.launch { userPreferences.saveLockTimeOption(option) }
    }

    /** Should be called from the activity's `ON_PAUSE` lifecycle event. */
    fun onAppPaused() {
        applicationScope.launch {
            userPreferences.saveLastActiveEpochSeconds(Instant.now().epochSecond)
        }
    }

    /** Should be called from the activity's `ON_RESUME` lifecycle event. */
    fun onAppResumed() {
        applicationScope.launch {
            val enabled = userPreferences.isLockEnabledFlow.first()
            if (!enabled) {
                _isUnlocked.value = true
                return@launch
            }
            val option = userPreferences.lockTimeOptionFlow.first()
            val lastActive = userPreferences.lastActiveEpochSecondsFlow.first()
            val now = Instant.now().epochSecond
            if (shouldLockNow(true, option, lastActive, now)) {
                _isUnlocked.value = false
            }
        }
    }

    fun availability(): BiometricAvailability {
        return when (BiometricManager.from(appContext).canAuthenticate(STRONG_BIOMETRIC_AUTHENTICATOR)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNSUPPORTED
        }
    }

    /**
     * Display the system biometric prompt.
     *
     * On success [onUnlocked] is invoked and the in-memory unlock flag flips to true,
     * allowing the lock screen to be removed by the UI layer.
     */
    fun authenticate(
        activity: FragmentActivity,
        onUnlocked: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        val cryptoObject = try {
            createCryptoObject()
        } catch (e: GeneralSecurityException) {
            onError("Biometric authentication could not be prepared.")
            return
        } catch (e: IOException) {
            onError("Biometric authentication could not be prepared.")
            return
        }
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher == null) {
                    onError("Biometric authentication could not be verified.")
                    return
                }
                try {
                    cipher.doFinal(AUTH_CHALLENGE.encodeToByteArray())
                    _isUnlocked.value = true
                    onUnlocked()
                } catch (e: GeneralSecurityException) {
                    onError("Biometric authentication could not be verified.")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Journal")
            .setSubtitle("Authenticate with a strong biometric to view your journal")
            .setAllowedAuthenticators(STRONG_BIOMETRIC_AUTHENTICATOR)
            .build()
        prompt.authenticate(info, cryptoObject)
    }

    /** Forces a re-lock — used when toggling the setting on. */
    fun lockNow() {
        _isUnlocked.value = false
    }

    private fun createCryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        } catch (e: InvalidKeyException) {
            deleteSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        return BiometricPrompt.CryptoObject(cipher)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(AUTH_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keySpec = KeyGenParameterSpec.Builder(
            AUTH_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun deleteSecretKey() {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
            deleteEntry(AUTH_KEY_ALIAS)
        }
    }
}
