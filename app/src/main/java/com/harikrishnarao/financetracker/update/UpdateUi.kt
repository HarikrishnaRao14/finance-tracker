package com.harikrishnarao.financetracker.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.ui.AppViewModelFactory
import com.harikrishnarao.financetracker.ui.Progress
import kotlinx.coroutines.launch

class UpdateViewModel(val updater: Updater) : ViewModel() {
    val state = updater.state
    fun checkIfDue() = viewModelScope.launch { updater.checkIfDue() }
    fun checkNow() = viewModelScope.launch { updater.check(manual = true) }
    fun install(info: UpdateInfo) = viewModelScope.launch { updater.downloadAndInstall(info) }
    fun dismiss() = updater.dismiss()
}

/** Shown at the top of every screen when a newer Arthix is on GitHub. */
@Composable
fun UpdateBanner() {
    val vm: UpdateViewModel = viewModel(factory = AppViewModelFactory)
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val (title, body) = when (val st = s) {
        is UpdateState.Available -> "Arthix ${st.info.versionName} is ready" to st.info.notes.lineSequence().firstOrNull().orEmpty()
        is UpdateState.Downloading -> "Downloading update… ${(st.progress * 100).toInt()}%" to ""
        is UpdateState.Installing -> "Opening the installer…" to "Tap Install (or Update) on the next screen."
        is UpdateState.NeedsPermission -> "One-time permission needed" to
            "Allow Arthix to install its own updates, then come back and tap Try again."
        is UpdateState.Failed -> if (st.info != null) "Update didn't finish" to st.message else return
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.primaryContainer,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            when (val st = s) {
                is UpdateState.Available -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.install(st.info) }) { Text("Update now") }
                    TextButton(onClick = vm::dismiss) { Text("Later") }
                }
                is UpdateState.Downloading -> Progress(st.progress, color = cs.primary)
                is UpdateState.NeedsPermission -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runCatching { context.startActivity(vm.updater.permissionIntent()) } }) { Text("Allow") }
                    TextButton(onClick = { vm.install(st.info) }) { Text("Try again") }
                }
                is UpdateState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    st.info?.let { info -> Button(onClick = { vm.install(info) }) { Text("Try again") } }
                    TextButton(onClick = vm::dismiss) { Text("Close") }
                }
                else -> Unit
            }
        }
    }
}
