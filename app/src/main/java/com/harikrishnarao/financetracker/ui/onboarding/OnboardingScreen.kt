package com.harikrishnarao.financetracker.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Card
import com.harikrishnarao.financetracker.ui.Notice
import com.harikrishnarao.financetracker.ui.OnboardingViewModel
import com.harikrishnarao.financetracker.ui.RowsDivider
import com.harikrishnarao.financetracker.ui.ScreenTitle

/** First launch: explain, ask for SMS access, let the user pick which banks to track. */
@Composable
fun OnboardingScreen() {
    val vm: OnboardingViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    val permissions = buildList {
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) vm.scan() else denied = true
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val senders = s.senders
            when {
                s.importing -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Reading the last 90 days of bank SMS...")
                    }
                }

                s.scanning -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Looking for bank messages...")
                    }
                }

                senders == null -> {
                    item { ScreenTitle("Welcome to Arthix", "Your money, tracked from your bank SMS") }
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(30); vm.setName(name) },
                            label = { Text("Your name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Card {
                            Text("How it works", fontWeight = FontWeight.Bold)
                            Text("• The app reads SMS only from the banks you choose next. Personal chats and OTPs are ignored.")
                            Text("• New bank SMS are checked every 3 hours and sorted into categories.")
                            Text("• Everything stays on this phone. The app has no internet access.")
                        }
                    }
                    if (denied) item { Notice("SMS access was not given. You can still add spends by hand, and turn on SMS later in Settings.", warn = true) }
                    item {
                        Button(onClick = { launcher.launch(permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Allow SMS access") }
                    }
                    item {
                        TextButton(onClick = { vm.skipSms() }, modifier = Modifier.fillMaxWidth()) { Text("Skip, I'll add spends manually") }
                    }
                }

                else -> {
                    item { ScreenTitle("Choose your banks", "Found in your SMS from the last 90 days") }
                    if (senders.isEmpty()) {
                        item { Notice("No bank messages found. You can add spends by hand, and scan again later from Settings.", warn = true) }
                    } else {
                        item {
                            Card {
                                senders.forEachIndexed { i, d ->
                                    if (i > 0) RowsDivider()
                                    Row(
                                        Modifier.fillMaxWidth().clickable { vm.toggle(d.code) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = d.code in s.chosen, onCheckedChange = { vm.toggle(d.code) })
                                        Column(Modifier.weight(1f)) {
                                            Text(d.bankName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${d.transactionCount} transaction SMS" + if (!d.known) " · not in our bank list, check it's yours" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(onClick = { vm.finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (s.chosen.isEmpty()) "Continue without SMS tracking" else "Start tracking ${s.chosen.size} bank(s)")
                        }
                    }
                }
            }
        }
    }
}
