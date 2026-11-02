package com.nova.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF173B70)
private val Blue = Color(0xFF246BCE)
private val Page = Color(0xFFF4F6FA)
private val Ink = Color(0xFF17243A)

data class Doc(val uri: String, val name: String, val mime: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovaApp() }
    }

    @Composable
    private fun NovaApp() {
        val prefs = remember { getSharedPreferences("nova", MODE_PRIVATE) }
        var docs by remember { mutableStateOf(readDocs(prefs)) }
        var favorites by remember { mutableStateOf(readFavorites(prefs)) }
        var tab by remember { mutableStateOf("Главная") }
        var query by remember { mutableStateOf("") }
        var opened by remember { mutableStateOf<Doc?>(null) }
        var message by remember { mutableStateOf<String?>(null) }

        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                val file = DocumentFile.fromSingleUri(this@MainActivity, uri)
                val doc = Doc(
                    uri.toString(),
                    file?.name ?: "Документ",
                    contentResolver.getType(uri) ?: "application/octet-stream"
                )
                docs = (listOf(doc) + docs.filterNot { it.uri == doc.uri }).take(300)
            }
            writeDocs(prefs, docs)
        }

        MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Blue, background = Page)) {
            Surface(Modifier.fillMaxSize(), color = Page) {
                if (opened != null) {
                    PdfViewer(opened!!, onBack = { opened = null }, onError = { message = it })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Header()
                        when (tab) {
                            "Файлы" -> {
                                OutlinedTextField(
                                    value = query, onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    placeholder = { Text("Поиск документов") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    singleLine = true, shape = RoundedCornerShape(14.dp)
                                )
                                DocList(docs.filter { it.name.contains(query, true) }, favorites,
                                    onOpen = { open(it, { opened = it }, { message = it }) },
                                    onFavorite = { toggleFavorite(it, prefs, favorites) { favorites = it } })
                            }
                            "Избранное" -> DocList(docs.filter { it.uri in favorites }, favorites,
                                onOpen = { open(it, { opened = it }, { message = it }) },
                                onFavorite = { toggleFavorite(it, prefs, favorites) { favorites = it } })
                            else -> {
                                Welcome { picker.launch(arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "application/vnd.ms-powerpoint",
                                    "text/plain"
                                )) }
                                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("Недавние документы", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    Spacer(Modifier.weight(1f))
                                    Text(docs.size.toString(), color = Color.Gray)
                                }
                                DocList(docs.take(15), favorites,
                                    onOpen = { open(it, { opened = it }, { message = it }) },
                                    onFavorite = { toggleFavorite(it, prefs, favorites) { favorites = it } })
                            }
                        }
                        NavigationBar(containerColor = Color.White) {
                            listOf("Главная", "Файлы", "Избранное").forEachIndexed { i, label ->
                                val icon = when (i) {
                                    0 -> Icons.Default.Home
                                    1 -> Icons.Default.Folder
                                    else -> Icons.Default.Star
                                }
                                NavigationBarItem(
                                    selected = tab == label, onClick = { tab = label },
                                    icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
                if (message != null) {
                    AlertDialog(
                        onDismissRequest = { message = null },
                        confirmButton = { TextButton(onClick = { message = null }) { Text("Понятно") } },
                        title = { Text("Не удалось открыть файл") },
                        text = { Text(message!!) }
                    )
                }
            }
        }
    }

    private fun open(doc: Doc, onPdf: (Doc) -> Unit, onError: (String) -> Unit) {
        if (doc.mime == "application/pdf" || doc.name.endsWith(".pdf", true)) onPdf(doc)
        else onError("В этой первой версии встроенный просмотрщик работает с PDF. Просмотр DOCX, XLSX и PPTX добавим следующим этапом.")
    }

    private fun toggleFavorite(doc: Doc, prefs: android.content.SharedPreferences,
                               current: Set<String>, update: (Set<String>) -> Unit) {
        val next = if (doc.uri in current) current - doc.uri else current + doc.uri
        update(next)
        prefs.edit().putString("favorites", JSONArray(next.toList()).toString()).apply()
    }

    private fun readDocs(p: android.content.SharedPreferences): List<Doc> = try {
        val a = JSONArray(p.getString("docs", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Doc(o.getString("uri"), o.getString("name"), o.getString("mime"))
        }
    } catch (_: Exception) { emptyList() }

    private fun writeDocs(p: android.content.SharedPreferences, docs: List<Doc>) {
        val a = JSONArray()
        docs.forEach { a.put(JSONObject().put("uri", it.uri).put("name", it.name).put("mime", it.mime)) }
        p.edit().putString("docs", a.toString()).apply()
    }

    private fun readFavorites(p: android.content.SharedPreferences): Set<String> = try {
        val a = JSONArray(p.getString("favorites", "[]"))
        (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (_: Exception) { emptySet() }

    @Composable
    private fun Header() {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = Navy, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("NOVA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = 1.sp)
                Text("DOCUMENTS", fontSize = 9.sp, color = Color.Gray, letterSpacing = 2.sp)
            }
        }
    }

    @Composable
    private fun Welcome(onAdd: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(16.dp)
            .background(Navy, RoundedCornerShape(22.dp)).padding(20.dp)) {
            Text("Все документы\nв одном месте", color = Color.White, fontSize = 23.sp,
                fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("Читайте файлы без рекламы и лишних действий.", color = Color(0xFFD7E4F7), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(
                containerColor = Color.White, contentColor = Navy)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить документы")
            }
        }
    }

    @Composable
    private fun DocList(list: List<Doc>, favorites: Set<String>,
                        onOpen: (Doc) -> Unit, onFavorite: (Doc) -> Unit) {
        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Здесь пока пусто", color = Color.Gray)
                    Text("Добавьте документы, чтобы увидеть их здесь", color = Color.Gray, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(list, key = { it.uri }) { doc ->
                    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp))
                        .clickable { onOpen(doc) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val pdf = doc.name.endsWith(".pdf", true)
                        Icon(if (pdf) Icons.Default.PictureAsPdf else Icons.Default.Description, null,
                            tint = if (pdf) Color(0xFFD84B4B) else Blue, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.name, fontWeight = FontWeight.SemiBold, color = Ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (pdf) "PDF-документ" else doc.mime.substringAfterLast('/').uppercase(),
                                color = Color.Gray, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onFavorite(doc) }) {
                            Icon(if (doc.uri in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                null, tint = if (doc.uri in favorites) Color(0xFFE2A52B) else Color.LightGray)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PdfViewer(doc: Doc, onBack: () -> Unit, onError: (String) -> Unit) {
        var page by remember(doc.uri) { mutableIntStateOf(0) }
        var count by remember(doc.uri) { mutableIntStateOf(0) }
        var bitmap by remember(doc.uri) { mutableStateOf<Bitmap?>(null) }
        var loading by remember(doc.uri) { mutableStateOf(true) }

        LaunchedEffect(doc.uri, page) {
            loading = true
            try {
                val pfd = contentResolver.openFileDescriptor(Uri.parse(doc.uri), "r")
                    ?: error("Не удалось открыть файл")
                val renderer = PdfRenderer(pfd)
                count = renderer.pageCount
                if (count > 0) {
                    val index = page.coerceIn(0, count - 1)
                    val pdfPage = renderer.openPage(index)
                    val w = 1200
                    val h = (w.toFloat() * pdfPage.height / pdfPage.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pdfPage.close()
                    bitmap = bmp
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                onError(e.message ?: "Ошибка чтения PDF")
            }
            loading = false
        }

        Column(Modifier.fillMaxSize().background(Page)) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Navy) }
                Text(doc.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text(if (count > 0) "${page + 1}/$count" else "", color = Color.Gray, fontSize = 12.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(color = Navy)
                    bitmap != null -> Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxWidth())
                    else -> Text("Не удалось отобразить страницу", color = Color.Gray)
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) {
                    Icon(Icons.Default.ChevronLeft, null); Text("Назад")
                }
                Text(if (count > 0) "Страница ${page + 1} из $count" else "PDF",
                    color = Color.Gray, fontSize = 12.sp)
                Button(onClick = { if (page < count - 1) page++ }, enabled = page < count - 1) {
                    Text("Далее"); Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}
