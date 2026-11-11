package io.github.isramorante.brava

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.isramorante.brava.ui.BravaApp
import io.github.isramorante.brava.ui.BravaTheme

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    // Fingerprint or face, with the phone's PIN or pattern as a fallback.
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BravaTheme {
                BravaApp(vm, unlock = { unlock() }, canLock = { canLock() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.lockIfEnabled()
    }

    private fun canLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun unlock() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.unlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Brava RCON")
            .setSubtitle(tr("Unlock to manage your servers", "Desbloquea para administrar tus servidores"))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
