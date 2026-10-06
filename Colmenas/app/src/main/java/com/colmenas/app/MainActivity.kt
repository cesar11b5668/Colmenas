package com.colmenas.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.math.roundToInt

// A new object per scan lets the same card be read again after an earlier scan.
data class Scan(val tagId: String? = null, val code: String? = null, val eventId: String = UUID.randomUUID().toString())

class MainActivity : ComponentActivity() {
    private var nfc: NfcAdapter? = null
    private var scan by mutableStateOf<Scan?>(null)
    private var nfcEnabled by mutableStateOf(false)
    private val qrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        IntentIntegrator.parseActivityResult(result.resultCode, result.data)?.contents?.let {
            acceptScan(Scan(code = normalizeHiveCode(it)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        setContent {
            val pendingScan = scan
            BeeApp(pendingScan, { if (scan?.eventId == pendingScan?.eventId) scan = null }, ::scanQr, nfc != null, nfcEnabled)
        }
    }

    internal fun acceptScan(event: Scan) { scan = event }

    private fun scanQr() {
        val intent = IntentIntegrator(this).apply {
            setPrompt("Escanea el QR de la colmena")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }.createScanIntent()
        qrLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcEnabled = nfc?.isEnabled == true
        if (nfcEnabled) nfc?.enableReaderMode(this, { tag -> readTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE, null)
    }

    override fun onPause() {
        nfc?.disableReaderMode(this)
        super.onPause()
    }

    private fun readTag(tag: Tag) {
        var code: String? = null
        // UID association also works for blank cards; NDEF remains supported for old cards.
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                code = ndef.ndefMessage?.records?.firstNotNullOfOrNull { record ->
                    when {
                        record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                            decodeNfcText(record.payload)?.let(::normalizeHiveCode)
                        else -> record.toUri()?.toString()?.let(::normalizeHiveCode)
                    }?.takeIf { it.startsWith("COL-") }
                }
            } catch (_: Exception) {
                // A card without readable NDEF can still be identified by its UID.
            } finally { runCatching { ndef.close() } }
        }
        val event = Scan(nfcTagId(tag.id), code)
        runOnUiThread { acceptScan(event) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeeApp(incoming: Scan?, clear: () -> Unit, scanQr: () -> Unit, hasNfc: Boolean, nfcEnabled: Boolean) {
    val context = LocalContext.current
    val dao = remember { BeeDb.get(context).dao() }
    val scope = rememberCoroutineScope()
    val apiaries by dao.apiaries().collectAsState(initial = emptyList())
    val hives by dao.hives().collectAsState(initial = emptyList())
    var selectedApiaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedApiary = apiaries.firstOrNull { it.id == selectedApiaryId }
    var creatingApiaryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = hives.firstOrNull { it.id == selectedId }
    var addHive by rememberSaveable { mutableStateOf(false) }
    var addApiary by rememberSaveable { mutableStateOf(false) }
    var capturing by rememberSaveable { mutableStateOf(false) }
    var newTagId by rememberSaveable { mutableStateOf<String?>(null) }
    var linkHiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun closeNewHive() { addHive = false; capturing = false; newTagId = null; creatingApiaryId = null }
    fun goBack() {
        if (selectedId != null) selectedId = null else selectedApiaryId = null
    }
    BackHandler(enabled = selectedId != null || selectedApiaryId != null) { goBack() }

    LaunchedEffect(incoming) {
        val event = incoming ?: return@LaunchedEffect
        try {
            if (capturing && event.tagId != null) {
                val existing = dao.hiveByNfcTagId(event.tagId)
                if (existing != null && existing.id != linkHiveId) {
                    snackbar.showSnackbar("Esta tarjeta ya está asociada a ${existing.name}. Usa otra tarjeta.")
                } else if (addHive) {
                    newTagId = event.tagId
                    capturing = false
                } else {
                    val hive = hives.firstOrNull { it.id == linkHiveId }
                    if (hive != null) {
                        dao.updateHive(hive.copy(nfcTagId = event.tagId))
                        capturing = false
                        linkHiveId = null
                        snackbar.showSnackbar("Tarjeta NFC asociada a ${hive.name}")
                    }
                }
            } else if (capturing) {
                snackbar.showSnackbar("Acerca una tarjeta NFC para asociarla.")
            } else {
                val hive = event.tagId?.let { dao.hiveByNfcTagId(it) }
                    ?: event.code?.let { dao.hiveByCode(it) }
                if (hive != null) {
                    selectedApiaryId = hive.apiaryId
                    selectedId = hive.id
                }
                else snackbar.showSnackbar("Tarjeta o código sin asociar. Agrégalo desde la ficha de una colmena.")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { snackbar.showSnackbar("No se pudo leer o guardar la colmena. Inténtalo de nuevo.") }
        finally { clear() }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFF2A900), secondary = androidx.compose.ui.graphics.Color(0xFF5D6B32))) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(title = { Text(selected?.name ?: selectedApiary?.name ?: "Colmenas") }, navigationIcon = {
                    if (selectedId != null || selectedApiaryId != null) IconButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                })
            }
        ) { padding ->
            Box(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                when {
                    selected != null -> HiveDetail(selected, apiaries.firstOrNull { it.id == selected.apiaryId }?.name.orEmpty(), dao,
                        onLinkNfc = { linkHiveId = selected.id; capturing = true }, canLinkNfc = hasNfc && nfcEnabled,
                        onMessage = ::message)
                    selectedApiary != null -> ApiaryDetail(selectedApiary, hives.filter { it.apiaryId == selectedApiary.id },
                        onAddHive = { creatingApiaryId = selectedApiary.id; addHive = true },
                        onOpenHive = { selectedId = it.id })
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Control de apiarios", style = MaterialTheme.typography.headlineSmall); Text("${apiaries.size} apiarios • ${hives.size} colmenas") }
                        item {
                            Button(onClick = scanQr, modifier = Modifier.fillMaxWidth()) { Text("Escanear QR") }
                            Text(when { !hasNfc -> "Este teléfono no tiene NFC."; !nfcEnabled -> "Activa NFC en los ajustes del teléfono."; else -> "Acerca una tarjeta NFC asociada para abrir su colmena." })
                        }
                        item { OutlinedButton(onClick = { addApiary = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Apiario") } }
                        item { Text("Apiarios", style = MaterialTheme.typography.titleLarge) }
                        if (apiaries.isEmpty()) item { Text("Agrega un apiario para registrar sus colmenas.") }
                        items(apiaries, key = { it.id }) { apiary ->
                            Card(onClick = { selectedApiaryId = apiary.id }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(apiary.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${hives.count { it.apiaryId == apiary.id }} colmenas")
                                    if (apiary.location.isNotBlank()) Text(apiary.location)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (addApiary) SimpleInput("Nuevo apiario", "Nombre del apiario", { addApiary = false }) { name ->
            scope.launch {
                try { dao.addApiary(Apiary(name = name)); addApiary = false }
                catch (_: Exception) { snackbar.showSnackbar("No se pudo guardar el apiario.") }
            }
        }
        val creatingApiary = apiaries.firstOrNull { it.id == creatingApiaryId }
        if (addHive && creatingApiary != null) NewHiveDialog(creatingApiary, newTagId, capturing, hasNfc && nfcEnabled,
            onCapture = { capturing = true }, onRemoveTag = { newTagId = null; capturing = false },
            onCancel = ::closeNewHive, dao = dao, onSaved = ::closeNewHive)
        if (capturing && linkHiveId != null) AlertDialog(
            onDismissRequest = { capturing = false; linkHiveId = null },
            title = { Text("Asociar tarjeta NFC") },
            text = { Text("Acerca la tarjeta a la parte trasera del teléfono. La tarjeta quedará asociada a esta colmena.") },
            confirmButton = {}, dismissButton = { TextButton(onClick = { capturing = false; linkHiveId = null }) { Text("Cancelar") } })
    }
}

@Composable
fun ApiaryDetail(apiary: Apiary, hives: List<Hive>, onAddHive: () -> Unit, onOpenHive: (Hive) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Apiario: ${apiary.name}", style = MaterialTheme.typography.headlineSmall)
            Text("${hives.size} colmenas")
        }
        item { Button(onClick = onAddHive, modifier = Modifier.fillMaxWidth()) { Text("+ Colmena") } }
        item { Text("Colmenas de este apiario", style = MaterialTheme.typography.titleLarge) }
        if (hives.isEmpty()) item { Text("Este apiario todavía no tiene colmenas.") }
        items(hives, key = { it.id }) { hive ->
            Card(onClick = { onOpenHive(hive) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hive.photoPath.isNotEmpty()) StoredPhoto(hive.photoPath, "Foto de ${hive.name}", Modifier.fillMaxWidth().height(150.dp))
                    Text(hive.name, style = MaterialTheme.typography.titleMedium)
                    Text("${hive.code} • ${hive.status}")
                    if (hive.nfcTagId != null) Text("Tarjeta NFC asociada")
                }
            }
        }
    }
}

@Composable
fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        options.forEach { option ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(selected == option, onClick = { onSelect(option) })
                TextButton(onClick = { onSelect(option) }) { Text(option) }
            }
        }
    }
}

@Composable
fun NewHiveDialog(apiary: Apiary, tagId: String?, capturing: Boolean, canUseNfc: Boolean,
    onCapture: () -> Unit, onRemoveTag: () -> Unit, onCancel: () -> Unit, dao: BeeDao, onSaved: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var queen by rememberSaveable { mutableStateOf("") }
    var photos by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving && !importing) onCancel() }, title = { Text("Nueva colmena") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, enabled = !saving)
            OutlinedTextField(queen, { queen = it }, label = { Text("Tipo de reina") }, enabled = !saving)
            Text("Apiario: ${apiary.name}", style = MaterialTheme.typography.titleSmall)
            Text(if (tagId == null) "Sin tarjeta NFC" else "Tarjeta NFC agregada")
            OutlinedButton(onClick = onCapture, enabled = canUseNfc && !saving) { Text(if (capturing) "Acerca la tarjeta al teléfono…" else "Asociar tarjeta NFC") }
            if (!canUseNfc) Text("Para asociar una tarjeta necesitas un teléfono con NFC activado.")
            if (tagId != null || capturing) TextButton(onClick = onRemoveTag) { Text("Quitar / cancelar tarjeta") }
            PhotoEditor(photos, 1, { photos = it }, onBusy = { importing = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank() && !saving && !importing && !capturing, onClick = {
            saving = true
            scope.launch {
                try {
                    if (tagId != null && dao.hiveByNfcTagId(tagId) != null) error = "Esta tarjeta ya está asociada. Usa otra tarjeta."
                    else {
                        dao.addHive(Hive(apiaryId = apiary.id, code = "COL-" + UUID.randomUUID().toString().take(8).uppercase(),
                            name = name.trim(), queenType = queen.trim(), nfcTagId = tagId, photoPath = photos.firstOrNull().orEmpty()))
                        onSaved()
                    }
                } catch (_: Exception) { error = "No se pudo guardar la colmena. Inténtalo de nuevo." }
                finally { saving = false }
            }
        }) { Text(if (saving) "Guardando…" else "Guardar") }
    }, dismissButton = { TextButton(onClick = onCancel, enabled = !saving && !importing) { Text("Cancelar") } })
}

@Composable
fun HiveDetail(hive: Hive, apiary: String, dao: BeeDao, onLinkNfc: () -> Unit, canLinkNfc: Boolean, onMessage: (String) -> Unit) {
    val inspections by remember(hive.id) { dao.inspections(hive.id) }.collectAsState(initial = emptyList())
    var add by rememberSaveable(hive.id) { mutableStateOf(false) }
    var editPhoto by rememberSaveable(hive.id) { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hive.photoPath.isNotEmpty()) StoredPhoto(hive.photoPath, "Foto de ${hive.name}", Modifier.fillMaxWidth().height(200.dp))
                    Text(hive.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Apiario: $apiary")
                    Text("Código: ${hive.code}")
                    Text("Estado: ${hive.status}")
                    Text("Reina: ${hive.queenType.ifBlank { "Sin registrar" }} ${hive.queenYear}")
                    Text(if (hive.nfcTagId == null) "Sin tarjeta NFC" else "Tarjeta NFC asociada")
                    OutlinedButton(onClick = onLinkNfc, enabled = canLinkNfc) { Text(if (hive.nfcTagId == null) "Asociar tarjeta NFC" else "Cambiar tarjeta NFC") }
                    OutlinedButton(onClick = { editPhoto = true }) { Text("Agregar / cambiar foto") }
                }
            }
        }
        item { Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) { Text("Nueva inspección") } }
        item { Text("Historial (${inspections.size})", style = MaterialTheme.typography.titleLarge) }
        items(inspections, key = { it.id }) { inspection ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(inspection.timestamp)))
                    Text("Salud: ${inspection.health}")
                    Text(if (inspection.honey in listOf("Clara", "Oscura")) "Color de miel: ${inspection.honey}" else "Miel (registro anterior): ${inspection.honey}")
                    Text("Fuerza: ${inspection.strength}/5 (1 = bajo, 5 = fuerte)")
                    if (inspection.notes.isNotBlank()) Text(inspection.notes)
                    inspection.photoPaths.forEachIndexed { index, path -> StoredPhoto(path, "Foto ${index + 1} de la inspección", Modifier.fillMaxWidth().height(180.dp)) }
                }
            }
        }
    }
    if (add) InspectionDialog(hive.id, dao, onCancel = { add = false }, onSaved = { add = false })
    if (editPhoto) EditHivePhotoDialog(hive, dao, onClose = { editPhoto = false }, onMessage)
}

@Composable
fun InspectionDialog(hiveId: Long, dao: BeeDao, onCancel: () -> Unit, onSaved: () -> Unit) {
    var health by rememberSaveable { mutableStateOf("Buena") }
    var honey by rememberSaveable { mutableStateOf("Clara") }
    var strength by rememberSaveable { mutableIntStateOf(3) }
    var notes by rememberSaveable { mutableStateOf("") }
    var photos by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving && !importing) onCancel() }, title = { Text("Nueva inspección") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Salud", listOf("Buena", "Regular", "Muerta"), health) { health = it }
            Choice("Color de miel", listOf("Clara", "Oscura"), honey) { honey = it }
            Text("Fuerza: $strength/5", style = MaterialTheme.typography.titleSmall)
            Slider(value = strength.toFloat(), onValueChange = { strength = it.roundToInt().coerceIn(1, 5) }, valueRange = 1f..5f, steps = 3)
            Text("1 = bajo • 5 = fuerte")
            OutlinedTextField(notes, { notes = it }, label = { Text("Descripción / observaciones") }, minLines = 3)
            PhotoEditor(photos, 6, { photos = it }, onBusy = { importing = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        Button(enabled = !saving && !importing, onClick = {
            saving = true
            scope.launch {
                try {
                    dao.addInspection(Inspection(hiveId = hiveId, health = health, honey = honey, strength = strength, notes = notes.trim(), photoPaths = photos))
                    onSaved()
                } catch (_: Exception) { error = "No se pudo guardar la inspección. Inténtalo de nuevo." }
                finally { saving = false }
            }
        }) { Text(if (saving) "Guardando…" else "Guardar") }
    }, dismissButton = { TextButton(onClick = onCancel, enabled = !saving && !importing) { Text("Cancelar") } })
}

@Composable
fun EditHivePhotoDialog(hive: Hive, dao: BeeDao, onClose: () -> Unit, onMessage: (String) -> Unit) {
    var photos by rememberSaveable { mutableStateOf(listOfNotNull(hive.photoPath.takeIf { it.isNotEmpty() })) }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving && !importing) onClose() }, title = { Text("Foto de la colmena") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { PhotoEditor(photos, 1, { photos = it }, onBusy = { importing = it }) } },
        confirmButton = { Button(enabled = !saving && !importing, onClick = {
            saving = true
            scope.launch {
                try { dao.updateHive(hive.copy(photoPath = photos.firstOrNull().orEmpty())); onClose() }
                catch (_: Exception) { onMessage("No se pudo guardar la foto.") }
                finally { saving = false }
            }
        }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose, enabled = !saving && !importing) { Text("Cancelar") } })
}

@Composable
fun SimpleInput(title: String, label: String, cancel: () -> Unit, save: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }) },
        confirmButton = { Button(onClick = { save(value.trim()) }, enabled = value.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancelar") } })
}
