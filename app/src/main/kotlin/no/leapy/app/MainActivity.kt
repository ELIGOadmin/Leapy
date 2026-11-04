package no.leapy.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import no.leapy.app.ui.LeapyApp
import no.leapy.app.ui.LeapyTheme

class MainActivity : FragmentActivity() {
    private val vm: CarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeapyTheme { LeapyApp(vm, ::confirmWithBiometrics) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh(silent = true)
    }

    /**
     * Asks for fingerprint/face/screen lock before a sensitive command. When the phone has no
     * screen lock at all, returns false so the UI falls back to a plain confirmation dialog.
     */
    private fun confirmWithBiometrics(title: String, onOk: () -> Unit): Boolean {
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) return false
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Bekreft at det er deg")
                .setAllowedAuthenticators(auth)
                .build(),
        )
        return true
    }
}
