@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.cryptomacro.app.ui.settings

/**
 * BEGINNER: Settings is a scrollable column of cards. if (BuildConfig.DEBUG) wraps the Feed
 * status and API-key forms so Play Store / release APKs cannot show or save secrets.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cryptomacro.app.BuildConfig
import com.cryptomacro.app.data.local.ThemeMode
import com.cryptomacro.app.domain.model.FiatCurrency
import com.cryptomacro.app.ui.components.AppCard
import com.cryptomacro.app.ui.components.SectionLabel
import com.cryptomacro.app.ui.components.WrapRow
import com.cryptomacro.app.ui.components.readableWidth

@Composable
fun SettingsScreen(vm: SettingsViewModel, onLock: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val status by vm.exchangeStatus.collectAsStateWithLifecycle()
    val keys by vm.keyDrafts.collectAsStateWithLifecycle()
    val secrets by vm.secretDrafts.collectAsStateWithLifecycle()
    val live by vm.market.wsLive.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings / Security", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Portfolio data stays on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AppCard {
            SectionLabel("Appearance")
            WrapRow {
                ThemeMode.entries.forEach {
                    FilterChip(selected = s.theme == it, onClick = { vm.setTheme(it) }, label = { Text(it.name) })
                }
            }
            WrapRow {
                FiatCurrency.entries.forEach {
                    FilterChip(selected = s.fiat == it, onClick = { vm.setFiat(it) }, label = { Text(it.name) })
                }
            }
        }

        AppCard {
            SectionLabel("Privacy & biometrics")
            Toggle("Biometric app lock", s.biometricLock) { vm.setBiometric(it) }
            Toggle("Privacy shield (block screenshots)", s.privacyShield) { vm.setPrivacy(it) }
            Text(
                "Hardware tokens (YubiKey / FIDO2) can unlock on supported devices. This app never stores seeds or private keys.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock now") }
        }

        AppCard {
            SectionLabel("Chart overlays")
            Toggle("SMA 20", s.showSma) { vm.setSma(it) }
            Toggle("EMA 12", s.showEma) { vm.setEma(it) }
            Toggle("Volume", s.showVolume) { vm.setVolume(it) }
            Toggle("Shemitah bands", s.shemitahOverlay) { vm.setShemitah(it) }
        }

        if (BuildConfig.DEBUG) {
            AppCard {
                SectionLabel("Feed")
                Text(if (live) "Binance WebSocket connected" else "Polling REST with exponential backoff")
                Text(
                    "Sources: Binance, CoinLore, CoinGecko, Yahoo Finance, CryptoCompare, alternative.me, mempool.space, ECB Frankfurter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            listOf("binance", "coinbase", "kraken").forEach { ex ->
                AppCard {
                    SectionLabel("Read-only ${ex.replaceFirstChar { it.titlecase() }} API")
                    Text(
                        "Withdraw-disabled keys only. Encrypted with Android Keystore AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        keys[ex].orEmpty(),
                        { vm.keyDrafts.value = keys + (ex to it) },
                        label = { Text("API key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        secrets[ex].orEmpty(),
                        { vm.secretDrafts.value = secrets + (ex to it) },
                        label = { Text("Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    WrapRow {
                        Button(onClick = { vm.saveKey(ex) }) { Text("Save encrypted") }
                        OutlinedButton(onClick = { vm.deleteKey(ex) }) { Text("Delete") }
                    }
                    status[ex]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        AppCard {
            SectionLabel("Disclaimer")
            Text("Not financial advice. Market data may be delayed or unavailable. Shemitah metrics are educational historical-cycle overlays.")
        }
        Spacer(Modifier.height(28.dp))
    }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
