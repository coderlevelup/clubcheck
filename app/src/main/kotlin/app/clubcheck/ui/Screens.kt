package app.clubcheck.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File

@Composable
fun ClubCheckNav(vm: CheckInViewModel = viewModel()) {
    val nav = rememberNavController()
    val ui by vm.ui.collectAsState()
    NavHost(nav, startDestination = "capture") {
        composable("capture") { CaptureScreen(ui, onPhoto = vm::onPhoto, onDone = { nav.navigate("review") }) }
        composable("review") {
            ReviewScreen(ui, vm, onBack = { nav.popBackStack() }, onReady = { nav.navigate("share") })
        }
        composable("share") { ShareScreen(ui, onNewCheckIn = { vm.newCheckIn(); nav.popBackStack("capture", false) }) }
    }
}

@Composable
fun CaptureScreen(ui: CheckInUi, onPhoto: (ByteArray) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Take a photo of the group", style = MaterialTheme.typography.headlineSmall)
        Text("The photo stays on this phone. Only a blurred copy is ever shared.", style = MaterialTheme.typography.bodyMedium)
        if (!granted) {
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        } else {
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }, ContextCompat.getMainExecutor(ctx))
                }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !ui.busy, onClick = { takePhoto(context, capture, onPhoto) }) { Text("Take photo") }
                Button(enabled = !ui.busy && ui.photos > 0, onClick = onDone) { Text("Done (${ui.photos})") }
                if (ui.busy) CircularProgressIndicator(Modifier.padding(4.dp))
            }
            Text("Camera count: ${ui.cameraText}")
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun takePhoto(context: Context, capture: ImageCapture, onPhoto: (ByteArray) -> Unit) {
    capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val buf = image.planes[0].buffer
            val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
            image.close(); onPhoto(bytes)
        }
        override fun onError(e: ImageCaptureException) { /* the next tap tries again */ }
    })
}

@Composable
fun ReviewScreen(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit, onReady: () -> Unit) {
    val context = LocalContext.current
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> vm.setLocationEnabled(ok) }
    LaunchedEffect(ui.ready) { if (ui.ready != null) onReady() }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Check-in details", style = MaterialTheme.typography.headlineSmall)
        Text("Camera count: ${ui.cameraText} from ${ui.photos} photo(s)")
        OutlinedTextField(ui.hand, vm::setHand, label = { Text("People you counted (a number or a range, e.g. 55-65)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ui.club, vm::setClub, label = { Text("Club web address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ui.clubTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = ui.locationOn, onCheckedChange = { on ->
                if (on && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                else vm.setLocationEnabled(on)
            })
            Text("Add the area (rounded to about 5 km, never exact)")
        }
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Button(enabled = !ui.busy, onClick = vm::export) { Text("Make shareable image") }
            if (ui.busy) CircularProgressIndicator(Modifier.padding(4.dp))
        }
    }
}

@Composable
fun ShareScreen(ui: CheckInUi, onNewCheckIn: () -> Unit) {
    val context = LocalContext.current
    val ready = ui.ready ?: return
    val bitmap = remember(ready) { BitmapFactory.decodeByteArray(ready.jpeg, 0, ready.jpeg.size).asImageBitmap() }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ready to share", style = MaterialTheme.typography.headlineSmall)
        Image(bitmap, contentDescription = "The blurred shareable image", modifier = Modifier.fillMaxWidth())
        if (ready.flagged) Text("The camera and your count are far apart. It is shared as is and marked for review.", color = MaterialTheme.colorScheme.error)
        if (ready.weakWatermark) Text("This photo is very bright, so the hidden copy of the data is weak. The data strip still carries everything.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { share(context, ready.jpeg) }) { Text("Share") }
            Button(onClick = onNewCheckIn) { Text("New check-in") }
        }
    }
}

private fun share(context: Context, jpeg: ByteArray) {
    val file = File(context.cacheDir, "exports").apply { mkdirs() }.let { File(it, "checkin.jpg") }
    file.writeBytes(jpeg)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(send, "Share check-in"))
}
