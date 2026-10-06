package com.colmenas.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private suspend fun importPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(dir, UUID.randomUUID().toString())
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir la imagen")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato de imagen no compatible" }
        file.name
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

@Composable
fun StoredPhoto(path: String, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            val file = File(File(context.filesDir, "photos"), path)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val options = BitmapFactory.Options()
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            options.inSampleSize = sample
            BitmapFactory.decodeFile(file.path, options)
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), description, modifier, contentScale = ContentScale.Crop) }
        ?: Text("Foto no disponible", modifier)
}

@Composable
fun PhotoEditor(paths: List<String>, maxPhotos: Int, onChange: (List<String>) -> Unit, onBusy: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            loading = true
            onBusy(true)
            error = null
            val imported = mutableListOf<String>()
            for (uri in uris.take(maxPhotos - paths.size)) {
                try { imported += importPhoto(context, uri) }
                catch (_: Exception) { error = "No se pudo agregar alguna imagen. Prueba con otra foto." }
            }
            onChange(paths + imported)
            loading = false
            onBusy(false)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (maxPhotos == 1) "Foto de la colmena" else "Fotos de la inspección (${paths.size}/$maxPhotos)")
        paths.forEachIndexed { index, path ->
            StoredPhoto(path, "Foto ${index + 1}", Modifier.fillMaxWidth().height(160.dp))
            TextButton(onClick = { onChange(paths - path) }, enabled = !loading) { Text("Quitar foto ${index + 1}") }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !loading && paths.size < maxPhotos) {
            Text(if (loading) "Guardando fotos…" else if (maxPhotos == 1) "Agregar foto" else "Agregar fotos")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Las fotos se guardan en este teléfono.", style = MaterialTheme.typography.bodySmall)
    }
}
