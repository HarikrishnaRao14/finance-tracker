package com.harikrishnarao.financetracker.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.data.AccountEntity
import com.harikrishnarao.financetracker.data.AppTheme
import com.harikrishnarao.financetracker.data.label
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle
import com.harikrishnarao.financetracker.ui.SectionLabel
import com.harikrishnarao.financetracker.ui.SettingsViewModel
import com.harikrishnarao.financetracker.ui.theme.LocalFinanceColors
import com.harikrishnarao.financetracker.ui.theme.previewColors
import com.harikrishnarao.financetracker.update.UpdateState
import com.harikrishnarao.financetracker.update.UpdateViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSmsActivity: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updates: UpdateViewModel = viewModel(factory = AppViewModelFactory)
    val update by updates.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<AccountEntity?>(null) }
    var editingName by remember { mutableStateOf(false) }
    var confirmRebuild by remember { mutableStateOf(false) }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.rescanBanks()
    }
    val findBanks = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.rescanBanks() else smsPermission.launch(Manifest.permission.READ_SMS)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Settings")
            }
        }
        busy?.let { item { Notice(it) } }

        // ---- Profile ----
        item {
            Card(Modifier.clickable { editingName = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(settings.userName.ifBlank { "Tap to add" }, style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Filled.Edit, contentDescription = "Edit name")
                }
            }
        }

        // ---- Theme ----
        item { SectionLabel("Theme") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { t ->
                    val selected = settings.theme == t
                    val (bg, card, accent) = t.previewColors()
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(2.dp, if (selected) accent else LocalFinanceColors.current.cardBorder),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { vm.setTheme(t) },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.clip(RoundedCornerShape(10.dp))) {
                                listOf(bg, card, accent).forEach { c -> Box(Modifier.size(18.dp, 40.dp).background(c)) }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(t.label, style = MaterialTheme.typography.titleMedium)
                                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = accent)
                        }
                    }
                }
            }
        }

        // ---- Accounts ----
        item { SectionLabel("Your accounts and cards") }
        item {
            Card {
                if (accounts.isEmpty()) {
                    Text("Accounts appear here after the first bank SMS is read.", style = MaterialTheme.typography.bodyMedium)
                }
                accounts.forEachIndexed { i, a ->
                    if (i > 0) RowsDivider()
                    Row(Modifier.fillMaxWidth().clickable { renaming = a }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(a.label, style = MaterialTheme.typography.titleSmall)
                            Text("${a.bankName} · ${if (a.isCard) "Credit card" else "Bank account"} ending ${a.last4}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                }
                Text("Tip: name them like 'ICICI Salary', 'Union Savings', 'ICICI Credit Card'.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Banks ----
        item { SectionLabel("Banks read from SMS") }
        item {
            Card {
                if (senders.isEmpty()) Text("No banks yet. Tap 'Find my banks' to scan your SMS.", style = MaterialTheme.typography.bodyMedium)
                senders.forEachIndexed { i, s ->
                    if (i > 0) RowsDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.bankName, style = MaterialTheme.typography.titleSmall)
                            Text("Sender ${s.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = s.tracked, onCheckedChange = { vm.setSenderTracked(s, it) })
                    }
                }
                OutlinedButton(onClick = { findBanks() }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Find my banks again")
                }
            }
        }

        // ---- SMS tools ----
        item { SectionLabel("SMS tracking") }
        item {
            Card {
                OutlinedButton(onClick = onOpenSmsActivity, modifier = Modifier.fillMaxWidth()) { Text("SMS activity: what happened to each SMS") }
                OutlinedButton(onClick = { confirmRebuild = true }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Rebuild from SMS (last 90 days)")
                }
                Text("Rebuild re-reads your bank SMS with the latest rules. Cash entries, payee names and 'use for all' choices are kept.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Notice(
                "Samsung puts apps to sleep, which stops the 3-hourly SMS check. Set Battery to 'Unrestricted' " +
                    "and keep this app out of 'Sleeping apps' (Settings > Battery > Background usage limits).",
                warn = true,
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open app battery settings") }
        }

        item { SectionLabel("App updates") }
        item {
            Card {
                Text("Arthix ${updates.updater.installedVersionName}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (val u = update) {
                        is UpdateState.Checking -> "Checking GitHub..."
                        is UpdateState.UpToDate -> "You have the latest version."
                        is UpdateState.Available -> "Version ${u.info.versionName} is ready. Use the banner at the top to install it."
                        is UpdateState.Failed -> u.message
                        else -> "Arthix checks for a new version every time you open it."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { updates.checkNow() }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates now") }
            }
        }

        item { SectionLabel("Privacy") }
        item {
            Notice("Your SMS and money data stay on this phone. Arthix uses the internet only to check its own GitHub page for updates, and sends nothing about you.")
        }
    }

    renaming?.let { a ->
        var name by remember(a.key) { mutableStateOf(a.nickname.orEmpty()) }
        var isCard by remember(a.key) { mutableStateOf(a.isCard) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("${a.bankName} ··${a.last4}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name (e.g. ICICI Salary)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCard, onCheckedChange = { isCard = it })
                        Text("This is a credit card")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameAccount(a, name)
                    if (isCard != a.isCard) vm.setAccountIsCard(a, isCard)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    if (editingName) {
        var name by remember { mutableStateOf(settings.userName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Your name") },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.setName(name); editingName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } },
        )
    }
    if (confirmRebuild) {
        AlertDialog(
            onDismissRequest = { confirmRebuild = false },
            title = { Text("Rebuild from SMS?") },
            text = { Text("SMS transactions from the last 90 days are read again. Category changes you made to single transactions are reset.", fontWeight = FontWeight.Normal) },
            confirmButton = { TextButton(onClick = { vm.rebuild(); confirmRebuild = false }) { Text("Rebuild") } },
            dismissButton = { TextButton(onClick = { confirmRebuild = false }) { Text("Cancel") } },
        )
    }
}
