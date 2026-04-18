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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import java.util.Locale

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
    val lines = remember { buildSampleLines() }
    val flatWords = remember(lines) { lines.flatten() }
    val listState = rememberLazyListState()

    var currentWordIndex by rememberSaveable { mutableStateOf(-1) }
    var recognizedText by rememberSaveable { mutableStateOf("") }
    var statusText by rememberSaveable { mutableStateOf("Tekan mulai untuk mendengar bacaan") }
    var isListening by rememberSaveable { mutableStateOf(false) }

    val currentLineIndex = if (currentWordIndex >= 0) flatWords[currentWordIndex].lineIndex else -1

    val speechController = remember(context) {
        QuranSpeechController(
            context = context,
            onRecognizedText = { rawText ->
                recognizedText = rawText
                val newIndex = findCurrentWordIndex(flatWords, rawText)
                if (newIndex >= 0) {
                    currentWordIndex = maxOf(currentWordIndex, newIndex)
                }
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

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            val targetIndex = (currentLineIndex - 1).coerceAtLeast(0)
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
            text = "Demo MVP: mendengar bacaan, menandai kata aktif, menandai baris aktif, lalu otomatis scroll saat bacaan turun ke bawah.",
            style = MaterialTheme.typography.bodyMedium
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
                enabled = !isListening
            ) {
                Text("Mulai Dengarkan")
            }
            OutlinedButton(
                onClick = {
                    speechController.stopListening()
                    currentWordIndex = -1
                    recognizedText = ""
                    statusText = "Status direset"
                }
            ) {
                Text("Stop / Reset")
            }
        }

        Text(text = "Status: $statusText")
        Text(text = "Teks terdeteksi: $recognizedText")

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(lines) { lineIndex, lineWords ->
                val isActiveLine = lineIndex == currentLineIndex
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isActiveLine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    tonalElevation = if (isActiveLine) 4.dp else 0.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = buildLineAnnotatedString(lineWords, currentWordIndex),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        textAlign = TextAlign.End,
                        fontSize = 28.sp,
                        lineHeight = 42.sp
                    )
                }
            }
        }
    }
}

private data class DisplayWord(
    val text: String,
    val normalized: String,
    val globalIndex: Int,
    val lineIndex: Int
)

private fun buildSampleLines(): List<List<DisplayWord>> {
    val rawLines = listOf(
        listOf("بِسْمِ", "اللَّهِ", "الرَّحْمَٰنِ", "الرَّحِيمِ"),
        listOf("الْحَمْدُ", "لِلَّهِ", "رَبِّ", "الْعَالَمِينَ"),
        listOf("الرَّحْمَٰنِ", "الرَّحِيمِ"),
        listOf("مَالِكِ", "يَوْمِ", "الدِّينِ"),
        listOf("إِيَّاكَ", "نَعْبُدُ", "وَإِيَّاكَ", "نَسْتَعِينُ"),
        listOf("اهْدِنَا", "الصِّرَاطَ", "الْمُسْتَقِيمَ"),
        listOf("صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ"),
        listOf("غَيْرِ", "الْمَغْضُوبِ", "عَلَيْهِمْ", "وَلَا", "الضَّالِّينَ")
    )

    var globalIndex = 0
    val result = mutableListOf<List<DisplayWord>>()

    rawLines.forEachIndexed { lineIndex, words ->
        val line = words.map { word ->
            DisplayWord(
                text = word,
                normalized = normalizeArabic(word),
                globalIndex = globalIndex++,
                lineIndex = lineIndex
            )
        }
        result += line
    }

    return result
}

private fun buildLineAnnotatedString(
    words: List<DisplayWord>,
    currentWordIndex: Int
): AnnotatedString {
    return buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val isCurrentWord = word.globalIndex == currentWordIndex
            withStyle(
                SpanStyle(
                    background = if (isCurrentWord) Color(0xFFFFF176) else Color.Transparent,
                    fontWeight = if (isCurrentWord) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                append(word.text)
            }
            if (index < words.lastIndex) append(" ")
        }
    }
}

private fun findCurrentWordIndex(
    words: List<DisplayWord>,
    recognizedText: String
): Int {
    val recognizedTokens = tokenizeArabic(recognizedText)
    if (recognizedTokens.isEmpty()) return -1

    var matchedIndex = -1
    val limit = minOf(words.size, recognizedTokens.size)

    for (index in 0 until limit) {
        if (words[index].normalized == recognizedTokens[index]) {
            matchedIndex = index
        } else {
            break
        }
    }

    return matchedIndex
}

private fun tokenizeArabic(text: String): List<String> {
    return text
        .split(" ", "\n", "\t")
        .map { normalizeArabic(it) }
        .filter { it.isNotBlank() }
}

private fun normalizeArabic(input: String): String {
    return input
        .lowercase(Locale("ar"))
        .replace(Regex("[ًٌٍَُِّْـٰ]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ٱ', 'ا')
        .replace('ى', 'ي')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .replace(Regex("[^\u0621-\u064A ]"), "")
        .trim()
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
                SpeechRecognizer.ERROR_NO_MATCH -> "Tidak ada kata yang cocok"
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
