package com.colmenas.app

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity: ComponentActivity(){
    private var nfc:NfcAdapter?=null
    private var nfcCode by mutableStateOf<String?>(null)
    private val qrLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ r ->
        IntentIntegrator.parseActivityResult(r.resultCode,r.data)?.contents?.let{ nfcCode=it.substringAfterLast("/") }
    }
    override fun onCreate(b:Bundle?){super.onCreate(b); nfc=NfcAdapter.getDefaultAdapter(this); setContent{ BeeApp(nfcCode,{nfcCode=null},{scanQr()}) }}
    private fun scanQr(){ val i=IntentIntegrator(this).apply{setPrompt("Escanea el QR de la colmena");setBeepEnabled(true);setOrientationLocked(false)}.createScanIntent(); qrLauncher.launch(i) }
    override fun onResume(){super.onResume(); nfc?.enableForegroundDispatch(this,PendingIntent.getActivity(this,0,Intent(this,javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_MUTABLE),null,null)}
    override fun onPause(){nfc?.disableForegroundDispatch(this);super.onPause()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent); val tag=intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?:return; val n=Ndef.get(tag)?:return; runCatching{n.connect(); val text=n.ndefMessage?.records?.firstOrNull()?.payload?.let{String(it).drop(3)}; n.close(); if(!text.isNullOrBlank())nfcCode=text.substringAfterLast("/")}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BeeApp(incoming:String?, clear:()->Unit, scanQr:()->Unit){
    val ctx=androidx.compose.ui.platform.LocalContext.current; val dao=remember{BeeDb.get(ctx).dao()}; val scope=rememberCoroutineScope()
    val apiaries by dao.apiaries().collectAsState(initial=emptyList()); val hives by dao.hives().collectAsState(initial=emptyList())
    var screen by remember{mutableStateOf("home")}; var selected by remember{mutableStateOf<Hive?>(null)}; var addHive by remember{mutableStateOf(false)}; var addApiary by remember{mutableStateOf(false)}
    LaunchedEffect(incoming,hives){ if(incoming!=null){ selected=hives.firstOrNull{it.code==incoming}; if(selected!=null)screen="hive"; clear() } }
    MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFFF2A900),secondary=androidx.compose.ui.graphics.Color(0xFF5D6B32))){ Scaffold(topBar={TopAppBar(title={Text(if(screen=="home")"Colmenas" else selected?.name?:"Colmenas")},navigationIcon={if(screen!="home")IconButton(onClick={screen="home"}){Icon(Icons.Default.ArrowBack,"Volver")}})}){p->
        Box(Modifier.padding(p).padding(16.dp).fillMaxSize()){
            when(screen){
                "home"->LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    item{Text("Control de apiarios",style=MaterialTheme.typography.headlineSmall);Text("${apiaries.size} apiarios • ${hives.size} colmenas")}
                    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=scanQr,modifier=Modifier.weight(1f)){Icon(Icons.Default.QrCodeScanner,null);Spacer(Modifier.width(6.dp));Text("Escanear QR")};Button(onClick={},modifier=Modifier.weight(1f)){Icon(Icons.Default.Nfc,null);Spacer(Modifier.width(6.dp));Text("Acercar NFC")}}}
                    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={addApiary=true},modifier=Modifier.weight(1f)){Text("+ Apiario")};OutlinedButton(onClick={addHive=true},enabled=apiaries.isNotEmpty(),modifier=Modifier.weight(1f)){Text("+ Colmena")}}}
                    item{Text("Colmenas",style=MaterialTheme.typography.titleLarge)}
                    items(hives){h->Card(onClick={selected=h;screen="hive"},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("🐝 ${h.name}",style=MaterialTheme.typography.titleMedium);Text("${h.code} • ${h.status}");if(h.queenType.isNotBlank())Text("Reina: ${h.queenType} ${h.queenYear}")}}}
                }
                "hive"-> selected?.let{h->HiveDetail(h,dao,scope)}
            }
        }
    }}
    if(addApiary) SimpleInput("Nuevo apiario","Nombre del apiario",{addApiary=false}){name->scope.launch{dao.addApiary(Apiary(name=name))};addApiary=false}
    if(addHive){var name by remember{mutableStateOf("")};var queen by remember{mutableStateOf("")};AlertDialog(onDismissRequest={addHive=false},title={Text("Nueva colmena")},text={Column{OutlinedTextField(name,{name=it},label={Text("Nombre")});Spacer(Modifier.height(8.dp));OutlinedTextField(queen,{queen=it},label={Text("Tipo de reina")})}},confirmButton={Button(onClick={if(name.isNotBlank()){scope.launch{dao.addHive(Hive(apiaryId=apiaries.first().id,code="COL-"+UUID.randomUUID().toString().take(8).uppercase(),name=name,queenType=queen))};addHive=false}}){Text("Guardar")}},dismissButton={TextButton(onClick={addHive=false}){Text("Cancelar")}})}
}

@Composable fun HiveDetail(h:Hive,dao:BeeDao,scope:kotlinx.coroutines.CoroutineScope){ val inspections by dao.inspections(h.id).collectAsState(initial=emptyList());var add by remember{mutableStateOf(false)};Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(h.name,style=MaterialTheme.typography.headlineSmall);Text("Código: ${h.code}");Text("Estado: ${h.status}");Text("Reina: ${h.queenType.ifBlank{"Sin registrar"}} ${h.queenYear}")}};Button(onClick={add=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text(" Nueva inspección")};Text("Historial (${inspections.size})",style=MaterialTheme.typography.titleLarge);LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(inspections){i->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(i.timestamp)));Text("Salud: ${i.health} • Miel: ${i.honey} • Fuerza: ${i.strength}/5");if(i.notes.isNotBlank())Text(i.notes)}}}}};if(add){var notes by remember{mutableStateOf("")};AlertDialog(onDismissRequest={add=false},title={Text("Inspección")},text={OutlinedTextField(notes,{notes=it},label={Text("Observaciones")},minLines=4)},confirmButton={Button(onClick={scope.launch{dao.addInspection(Inspection(hiveId=h.id,notes=notes))};add=false}){Text("Guardar")}},dismissButton={TextButton(onClick={add=false}){Text("Cancelar")}})}}

@Composable fun SimpleInput(title:String,label:String,cancel:()->Unit,save:(String)->Unit){var v by remember{mutableStateOf("")};AlertDialog(onDismissRequest=cancel,title={Text(title)},text={OutlinedTextField(v,{v=it},label={Text(label)})},confirmButton={Button(onClick={if(v.isNotBlank())save(v)}){Text("Guardar")}},dismissButton={TextButton(onClick=cancel){Text("Cancelar")}})}
