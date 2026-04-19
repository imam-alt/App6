package com.imamalt.quranfollow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuranFollowScreen()
                }
            }
        }
    }
}

@Composable
private fun QuranFollowScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { QuranRepository() }
    val listState = rememberLazyListState()

    var chapterCatalog by remember { mutableStateOf<List<QuranChapterIndex>>(emptyList()) }
    var selectedChapterId by rememberSaveable { mutableStateOf(1) }
    var currentChapter by remember { mutableStateOf<QuranChapter?>(null) }
    var isCatalogLoading by remember { mutableStateOf(true) }
    var isChapterLoading by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var showChapterPicker by remember { mutableStateOf(false) }

    var recognizedText by rememberSaveable(selectedChapterId) { mutableStateOf("") }
    var statusText by rememberSaveable(selectedChapterId) {
        mutableStateOf("Tekan mulai untuk mendengar bacaan")
    }
    var matchStatusText by rememberSaveable(selectedChapterId) {
        mutableStateOf("Belum ada kecocokan")
    }
    var activeGlobalIndex by rememberSaveable(selectedChapterId) { mutableStateOf(-1) }
    var activeVerseId by rememberSaveable(selectedChapterId) { mutableStateOf(-1) }
    var isListening by rememberSaveable { mutableStateOf(false) }

    val currentChapterMeta = chapterCatalog.firstOrNull { it.id == selectedChapterId }
    val matcher = remember(currentChapter) {
        currentChapter?.let { QuranMatcher(it) }
    }

    val speechController = remember(context) {
        QuranSpeechController(
            context = context,
            onRecognizedText = { partialText ->
                recognizedText = partialText
            },
            onStatusChanged = { newStatus ->
                statusText = newStatus
            },
            onListeningChanged = { listening ->
                isListening = listening
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            speechController.destroy()
        }
    }

    LaunchedEffect(Unit) {
        isCatalogLoading = true
        loadingError = null
        try {
            chapterCatalog = repository.loadChapterIndex()
            if (chapterCatalog.isNotEmpty() && chapterCatalog.none { it.id == selectedChapterId }) {
                selectedChapterId = chapterCatalog.first().id
            }
        } catch (exception: Exception) {
            loadingError = exception.message ?: "Gagal memuat daftar surat"
        } finally {
            isCatalogLoading = false
        }
    }

    LaunchedEffect(currentChapterMeta?.id) {
        val targetMeta = currentChapterMeta ?: return@LaunchedEffect
        isChapterLoading = true
        loadingError = null
        currentChapter = null
        recognizedText = ""
        statusText = "Surat dimuat. Siap mendengar bacaan."
        matchStatusText = "Menunggu bacaan untuk dikunci dengan konteks ayat."
        activeGlobalIndex = -1
        activeVerseId = -1
        try {
            currentChapter = repository.loadChapter(targetMeta)
        } catch (exception: Exception) {
            loadingError = exception.message ?: "Gagal memuat isi surat"
        } finally {
            isChapterLoading = false
        }
    }

    LaunchedEffect(recognizedText, matcher) {
        val localMatcher = matcher ?: return@LaunchedEffect
        if (recognizedText.isBlank()) return@LaunchedEffect

        val result = localMatcher.findBestMatch(
            recognizedText = recognizedText,
            currentAnchorGlobalIndex = activeGlobalIndex.takeIf { it >= 0 }
        )

        if (result == null) {
            matchStatusText = "Belum ada kandidat posisi yang cocok."
            return@LaunchedEffect
        }

        matchStatusText = result.statusMessage
        if (result.isConfident) {
            activeGlobalIndex = result.activeGlobalIndex
            activeVerseId = result.activeVerseId
        }
    }

    LaunchedEffect(activeVerseId) {
        if (activeVerseId > 0) {
            val targetIndex = (activeVerseId - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechController.startListening()
        } else {
            statusText = "Izin mikrofon ditolak"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Quran Follow Reader",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "114 surat + matcher berbasis kata, frasa, dan konteks minimal 2 ayat untuk menahan ambiguitas kata yang berulang.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (selectedChapterId > 1) selectedChapterId -= 1
                },
                enabled = selectedChapterId > 1 && chapterCatalog.isNotEmpty()
            ) {
                Text("Sebelumnya")
            }
            OutlinedButton(
                onClick = { showChapterPicker = true },
                enabled = chapterCatalog.isNotEmpty()
            ) {
                Text("Pilih Surat")
            }
            OutlinedButton(
                onClick = {
                    if (selectedChapterId < 114) selectedChapterId += 1
                },
                enabled = selectedChapterId < 114 && chapterCatalog.isNotEmpty()
            ) {
                Text("Berikutnya")
            }
        }

        Text(
            text = currentChapterMeta?.let {
                "Surat ${it.id}: ${it.transliteration} — ${it.name} (${it.totalVerses} ayat)"
            } ?: "Memuat daftar 114 surat...",
            fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val permissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        speechController.startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !isListening && currentChapter != null
            ) {
                Text("Mulai Dengarkan")
            }
            OutlinedButton(
                onClick = {
                    speechController.stopListening()
                    recognizedText = ""
                    activeGlobalIndex = -1
                    activeVerseId = -1
                    statusText = "Perekaman dihentikan"
                    matchStatusText = "Posisi direset. Menunggu konteks baru."
                }
            ) {
                Text("Stop / Reset")
            }
        }

        Text(text = "Status sistem: $statusText")
        Text(text = "Status pencocokan: $matchStatusText")
        Text(text = "Teks terdeteksi: $recognizedText")

        when {
            isCatalogLoading || isChapterLoading -> {
                Text("Sedang memuat data Qur'an...")
            }
            loadingError != null -> {
                Text(
                    text = "Error: $loadingError",
                    color = MaterialTheme.colorScheme.error
                )
            }
            currentChapter == null -> {
                Text("Data surat belum tersedia.")
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentChapter!!.verses, key = { verse -> verse.id }) { verse ->
                        val isActiveVerse = verse.id == activeVerseId
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isActiveVerse) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            tonalElevation = if (isActiveVerse) 4.dp else 0.dp,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Ayat ${verse.id}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = buildVerseAnnotatedString(
                                        verse = verse,
                                        activeGlobalIndex = activeGlobalIndex
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                    fontSize = 26.sp,
                                    lineHeight = 40.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChapterPicker) {
        ChapterPickerDialog(
            chapters = chapterCatalog,
            selectedChapterId = selectedChapterId,
            onDismiss = { showChapterPicker = false },
            onSelect = { chapterId ->
                selectedChapterId = chapterId
                showChapterPicker = false
            }
        )
    }
}

@Composable
private fun ChapterPickerDialog(
    chapters: List<QuranChapterIndex>,
    selectedChapterId: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
        title = {
            Text("Pilih surat")
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(chapters, key = { chapter -> chapter.id }) { chapter ->
                    TextButton(
                        onClick = { onSelect(chapter.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (chapter.id == selectedChapterId) {
                                "✓ ${chapter.id}. ${chapter.transliteration} — ${chapter.name}"
                            } else {
                                "${chapter.id}. ${chapter.transliteration} — ${chapter.name}"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
}

private fun buildVerseAnnotatedString(
    verse: QuranVerse,
    activeGlobalIndex: Int
): AnnotatedString {
    return buildAnnotatedString {
        verse.words.forEachIndexed { index, word ->
            val isActiveWord = word.globalIndex == activeGlobalIndex
            withStyle(
                SpanStyle(
                    background = if (isActiveWord) Color(0xFFFFF176) else Color.Transparent,
                    fontWeight = if (isActiveWord) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                append(word.displayText)
            }
            if (index < verse.words.lastIndex) append(" ")
        }
    }
}

private class QuranSpeechController(
    context: Context,
    private val onRecognizedText: (String) -> Unit,
    private val onStatusChanged: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit
) : RecognitionListener {

    private val speechRecognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(this)
            }
        } else {
            null
        }

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun startListening() {
        if (speechRecognizer == null) {
            onStatusChanged("Speech recognizer tidak tersedia di perangkat ini")
            return
        }
        onListeningChanged(true)
        onStatusChanged("Sedang mendengar bacaan...")
        speechRecognizer.startListening(recognizerIntent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        onListeningChanged(false)
        onStatusChanged("Perekaman dihentikan")
    }

    fun destroy() {
        speechRecognizer?.destroy()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onStatusChanged("Siap mendengar")
    }

    override fun onBeginningOfSpeech() {
        onStatusChanged("Bacaan mulai terdeteksi")
    }

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        onListeningChanged(false)
        onStatusChanged("Selesai mendengar")
    }

    override fun onError(error: Int) {
        onListeningChanged(false)
        onStatusChanged(
            when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error audio"
                SpeechRecognizer.ERROR_CLIENT -> "Error client"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon kurang"
                SpeechRecognizer.ERROR_NETWORK -> "Error jaringan"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Jaringan timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "Belum ada frasa yang bisa dikunci"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer sedang sibuk"
                SpeechRecognizer.ERROR_SERVER -> "Error server"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara masuk"
                else -> "Error pengenalan suara: $error"
            }
        )
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) {
            onRecognizedText(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) {
            onRecognizedText(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
