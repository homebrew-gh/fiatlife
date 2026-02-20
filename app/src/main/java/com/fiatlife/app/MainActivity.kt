package com.fiatlife.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.nostr.*
import com.fiatlife.app.ui.navigation.FiatLifeNavGraph
import com.fiatlife.app.ui.screens.login.LoginScreen
import com.fiatlife.app.ui.screens.login.parseAmberResult
import com.fiatlife.app.ui.theme.FiatLifeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var nostrClient: NostrClient
    @Inject lateinit var blossomClient: BlossomClient

    val amberSignerRef = AtomicReference<AmberSigner?>(null)
    lateinit var decryptLauncher: ActivityResultLauncher<Intent>
    lateinit var encryptLauncher: ActivityResultLauncher<Intent>
    lateinit var signLauncher: ActivityResultLauncher<Intent>

    companion object {
        val KEY_AUTH_TYPE = stringPreferencesKey("auth_type")
        val KEY_PRIVATE_KEY = stringPreferencesKey("private_key")
        val KEY_AMBER_PUBKEY = stringPreferencesKey("amber_pubkey")
        val KEY_SIGNER_PACKAGE = stringPreferencesKey("signer_package")
        val KEY_RELAY_URL = stringPreferencesKey("relay_url")
        val KEY_BLOSSOM_URL = stringPreferencesKey("blossom_url")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mainHandler = Handler(Looper.getMainLooper())
        var resolvedSignerPackage: String? = null

        decryptLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> amberSignerRef.get()?.onDecryptResult(result) }

        encryptLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> amberSignerRef.get()?.onEncryptResult(result) }

        signLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> amberSignerRef.get()?.onSignResult(result) }

        var refresh by mutableIntStateOf(0)

        val amberLoginLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val (pk, pkg) = parseAmberResult(result)
            val effectivePkg = pkg ?: resolvedSignerPackage
            Log.d(TAG, "Amber login: pk=${pk?.take(8)}..., pkg=$effectivePkg")
            if (pk != null && effectivePkg != null) {
                val pubkeyHex = amberPubkeyToHex(pk)
                lifecycleScope.launch {
                    dataStore.edit { prefs ->
                        prefs[KEY_AUTH_TYPE] = "amber"
                        prefs[KEY_AMBER_PUBKEY] = pubkeyHex
                        prefs[KEY_SIGNER_PACKAGE] = effectivePkg
                        prefs.remove(KEY_PRIVATE_KEY)
                    }
                    setupAmberSigner(pubkeyHex, effectivePkg)
                    mainHandler.post { refresh++ }
                }
            }
        }

        setContent {
            @Suppress("UNUSED_EXPRESSION")
            refresh

            val isLoggedIn = remember(refresh) {
                runBlocking {
                    val prefs = dataStore.data.first()
                    val authType = prefs[KEY_AUTH_TYPE] ?: ""
                    when (authType) {
                        "local" -> prefs[KEY_PRIVATE_KEY]?.isNotEmpty() == true
                        "amber" -> prefs[KEY_AMBER_PUBKEY]?.isNotEmpty() == true
                                && prefs[KEY_SIGNER_PACKAGE]?.isNotEmpty() == true
                        else -> false
                    }
                }
            }

            if (isLoggedIn) {
                remember(refresh) { restoreSession() }
            }

            FiatLifeTheme {
                if (!isLoggedIn) {
                    LoginScreen(
                        onNsecSubmit = { nsecOrHex ->
                            val keyBytes = nsecToBytes(nsecOrHex)
                            if (keyBytes != null) {
                                val hex = keyBytes.toHex()
                                runBlocking {
                                    dataStore.edit { prefs ->
                                        prefs[KEY_AUTH_TYPE] = "local"
                                        prefs[KEY_PRIVATE_KEY] = hex
                                        prefs.remove(KEY_AMBER_PUBKEY)
                                        prefs.remove(KEY_SIGNER_PACKAGE)
                                    }
                                }
                                setupLocalSigner(keyBytes)
                                true
                            } else {
                                val hex = nsecOrHex.trim()
                                if (hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                    runBlocking {
                                        dataStore.edit { prefs ->
                                            prefs[KEY_AUTH_TYPE] = "local"
                                            prefs[KEY_PRIVATE_KEY] = hex
                                            prefs.remove(KEY_AMBER_PUBKEY)
                                            prefs.remove(KEY_SIGNER_PACKAGE)
                                        }
                                    }
                                    setupLocalSigner(hex.hexToByteArray())
                                    true
                                } else false
                            }
                        },
                        onLoginSuccess = { mainHandler.post { refresh++ } },
                        onLaunchAmber = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
                            intent.putExtra("type", "get_public_key")
                            intent.putExtra("permissions",
                                """[{"type":"nip44_decrypt"},{"type":"nip44_encrypt"},{"type":"sign_event","kind":22242},{"type":"sign_event","kind":24242},{"type":"sign_event","kind":30078}]"""
                            )
                            resolvedSignerPackage = packageManager
                                .queryIntentActivities(intent, 0)
                                .firstOrNull()?.activityInfo?.packageName
                            Log.d(TAG, "Launching Amber, resolved package: $resolvedSignerPackage")
                            amberLoginLauncher.launch(intent)
                        }
                    )
                } else {
                    FiatLifeNavGraph()
                }
            }
        }
    }

    private fun restoreSession() {
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val authType = prefs[KEY_AUTH_TYPE] ?: return@launch

            when (authType) {
                "local" -> {
                    val hex = prefs[KEY_PRIVATE_KEY] ?: return@launch
                    if (hex.isNotEmpty()) {
                        setupLocalSigner(hex.hexToByteArray())
                        autoConnect(prefs)
                    }
                }
                "amber" -> {
                    val pubkey = prefs[KEY_AMBER_PUBKEY] ?: return@launch
                    val signerPkg = prefs[KEY_SIGNER_PACKAGE] ?: return@launch
                    if (pubkey.isNotEmpty() && signerPkg.isNotEmpty()) {
                        setupAmberSigner(pubkey, signerPkg)
                        autoConnect(prefs)
                    }
                }
            }
        }
    }

    private fun setupLocalSigner(privateKey: ByteArray) {
        val signer = LocalSigner(privateKey)
        nostrClient.clearSigner()
        amberSignerRef.set(null)
        Log.d(TAG, "Local signer configured: ${signer.pubkeyHex.take(8)}...")
    }

    private fun setupAmberSigner(pubkeyHex: String, signerPackage: String) {
        val signer = AmberSigner(this, pubkeyHex, signerPackage).apply {
            setLaunchDecrypt { decryptLauncher.launch(it) }
            setLaunchEncrypt { encryptLauncher.launch(it) }
            setLaunchSign { signLauncher.launch(it) }
        }
        amberSignerRef.set(signer)
        Log.d(TAG, "Amber signer configured: ${pubkeyHex.take(8)}...")
    }

    private suspend fun autoConnect(prefs: Preferences) {
        val relayUrl = prefs[KEY_RELAY_URL] ?: return
        val blossomUrl = prefs[KEY_BLOSSOM_URL]
        val authType = prefs[KEY_AUTH_TYPE] ?: return

        val signer: NostrSigner = when (authType) {
            "local" -> {
                val hex = prefs[KEY_PRIVATE_KEY] ?: return
                LocalSigner(hex.hexToByteArray())
            }
            "amber" -> amberSignerRef.get() ?: return
            else -> return
        }

        if (relayUrl.isNotEmpty()) {
            nostrClient.connect(relayUrl, signer)
        }
        if (!blossomUrl.isNullOrEmpty()) {
            blossomClient.configure(blossomUrl, signer)
        }
    }
}
