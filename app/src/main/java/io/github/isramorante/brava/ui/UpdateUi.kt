package io.github.isramorante.brava.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.isramorante.brava.AppViewModel
import io.github.isramorante.brava.tr

/** Version line plus one button that checks, downloads or installs depending on the state. */
@Composable
fun UpdateRow(vm: AppViewModel) {
    val release = vm.update
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = when (vm.updateState) {
            "checking" -> tr("Checking…", "Buscando…")
            "downloading" -> tr("Downloading ", "Descargando ") + (vm.updateProgress * 100).toInt() + "%"
            "available" -> tr("Update to build ", "Actualizar a la versión ") + (release?.build ?: "")
            "ready" -> tr("Install build ", "Instalar la versión ") + (release?.build ?: "")
            else -> tr("Check for updates", "Buscar actualizaciones")
        }
        val busy = vm.updateState == "checking" || vm.updateState == "downloading"
        BravaButton(
            label,
            { if (release != null && vm.updateState in listOf("available", "ready")) vm.installUpdate() else vm.checkUpdate(manual = true) },
            Modifier.fillMaxWidth(),
            if (vm.updateState == "available" || vm.updateState == "ready") Kind.Go else Kind.Neutral,
            enabled = !busy,
        )
        val error = vm.updateError
        Text(
            when {
                error != null -> tr("Update failed: ", "Falló la actualización: ") + error
                vm.updateState == "current" -> tr("Build ${vm.installedBuild} is the newest.", "La versión ${vm.installedBuild} es la más reciente.")
                else -> tr(
                    "Installed: build ${vm.installedBuild}. Updates keep your servers and settings.",
                    "Instalada: versión ${vm.installedBuild}. Al actualizar se conservan tus servidores y ajustes.",
                )
            },
            color = if (error != null) C.rustText else C.mute,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun UpdatePrompt(vm: AppViewModel) {
    val release = vm.update ?: return
    if (!vm.updatePrompt) return
    ConfirmDialog(
        title = tr("Update available", "Actualización disponible"),
        text = tr(
            "Build ${release.build} is out (you have ${vm.installedBuild}). Android will ask you to confirm the install. Your servers and settings stay.",
            "Ya salió la versión ${release.build} (tienes la ${vm.installedBuild}). Android te pedirá confirmar la instalación. Tus servidores y ajustes se conservan.",
        ),
        confirm = tr("Update", "Actualizar"),
        onConfirm = { vm.installUpdate() },
        onDismiss = { vm.closeUpdatePrompt() },
    )
}
