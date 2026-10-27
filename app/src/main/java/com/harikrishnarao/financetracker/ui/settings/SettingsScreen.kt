package com.harikrishnarao.financetracker.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.data.AppTheme
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.SectionLabel
import com.harikrishnarao.financetracker.ui.SettingsViewModel
import com.harikrishnarao.financetracker.ui.theme.previewColors

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.rescanBanks()
    }
    val findBanks = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.rescanBanks() else smsPermission.launch(Manifest.permission.READ_SMS)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Settings") }

        // ---- Theme ----
        item { SectionLabel("Theme") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { t ->
                    val selected = settings.theme == t
                    val (bg, card, accent) = t.previewColors()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.setTheme(t) },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.clip(RoundedCornerShape(10.dp))) {
                                listOf(bg, card, accent).forEach { c -> Box(Modifier.size(18.dp, 40.dp).background(c)) }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(t.label, fontWeight = FontWeight.Bold)
                                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Follow phone dark mode", fontWeight = FontWeight.SemiBold)
                            Text("Uses Midnight when your phone is in dark mode", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.followSystemDark, onCheckedChange = vm::setFollowSystem)
                    }
                }
            }
        }

        // ---- Banks ----
        item { SectionLabel("Banks tracked from SMS") }
        item {
            Card {
                if (senders.isEmpty()) {
                    Text("No banks yet. Tap 'Find my banks' to scan your SMS.", style = MaterialTheme.typography.bodyMedium)
                }
                senders.forEachIndexed { i, s ->
                    if (i > 0) RowsDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.bankName, fontWeight = FontWeight.SemiBold)
                            Text("Sender ${s.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = s.tracked, onCheckedChange = { vm.setSenderTracked(s, it) })
                    }
                }
                OutlinedButton(onClick = { findBanks() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Scanning..." else "Find my banks and re-read last 90 days")
                }
            }
        }

        // ---- Background sync on Samsung and others ----
        item { SectionLabel("Reliable background sync") }
        item {
            Notice(
                "Samsung and some other phones put apps to sleep, which can stop the 3-hourly SMS check. " +
                    "Set this app's battery use to 'Unrestricted' and keep it out of 'Sleeping apps'.",
                warn = true,
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    val i = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    runCatching { context.startActivity(i) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open app battery settings") }
        }

        item { SectionLabel("Privacy") }
        item {
            Notice("Everything stays on this phone. The app has no internet permission, so your SMS and money data cannot be uploaded anywhere.")
        }
    }
}
