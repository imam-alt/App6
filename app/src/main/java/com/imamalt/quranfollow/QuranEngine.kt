package com.imamalt.quranfollow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class QuranChapterIndex(
    val id: Int,
    val name: String,
    val transliteration: String,
    val type: String,
    val totalVerses: Int,
    val link: String
)

data class QuranWordPosition(
    val globalIndex: Int,
    val verseId: Int,
    val wordIndexInVerse: Int,
    val displayText: String,
    val normalized: String
)

data class QuranVerse(
    val id: Int,
    val text: String,
    val words: List<QuranWordPosition>
)

data class QuranChapter(
    val id: Int,
    val name: String,
    val transliteration: String,
    val type: String,
    val totalVerses: Int,
    val verses: List<QuranVerse>
) {
    val allWords: List<QuranWordPosition> = verses.flatMap { it.words }
}

object ArabicTextTools {
    fun tokenize(input: String): List<String> {
        return input
            .split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotBlank() }
    }

    fun normalize(input: String): String {
        return input
            .replace("\uFEFF", "")
            .lowercase(Locale("ar"))
            .replace(Regex("[ًٌٍَُِّْـٰۡۚۖۗۙۛۜ۝۞]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace(Regex("[^\\u0621-\\u064A ]"), "")
            .trim()
    }
}

class QuranRepository {
    private val chapterCache = ConcurrentHashMap<Int, QuranChapter>()
    private var chapterIndexCache: List<QuranChapterIndex>? = null

    suspend fun loadChapterIndex(): List<QuranChapterIndex> = withContext(Dispatchers.IO) {
        chapterIndexCache ?: parseChapterIndex(fetchJson(CHAPTER_INDEX_URL)).also {
            chapterIndexCache = it
        }
    }

    suspend fun loadChapter(chapterIndex: QuranChapterIndex): QuranChapter = withContext(Dispatchers.IO) {
        chapterCache[chapterIndex.id] ?: parseChapter(fetchJson(chapterIndex.link)).also {
            chapterCache[chapterIndex.id] = it
        }
    }

    private fun parseChapterIndex(json: String): List<QuranChapterIndex> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    QuranChapterIndex(
                        id = item.getInt("id"),
                        name = item.getString("name"),
                        transliteration = item.getString("transliteration"),
                        type = item.getString("type"),
                        totalVerses = item.getInt("total_verses"),
                        link = item.getString("link")
                    )
                )
            }
        }
    }

    private fun parseChapter(json: String): QuranChapter {
        val root = JSONObject(json)
        val versesArray = root.getJSONArray("verses")
        var globalWordIndex = 0

        val verses = buildList {
            for (index in 0 until versesArray.length()) {
                val verseJson = versesArray.getJSONObject(index)
                val verseId = verseJson.getInt("id")
                val verseText = verseJson.getString("text")
                val rawWords = verseText.split(Regex("\\s+")).filter { it.isNotBlank() }
                val words = rawWords.mapIndexed { wordIndex, rawWord ->
                    QuranWordPosition(
                        globalIndex = globalWordIndex++,
                        verseId = verseId,
                        wordIndexInVerse = wordIndex,
                        displayText = rawWord,
                        normalized = ArabicTextTools.normalize(rawWord)
                    )
                }

                add(
                    QuranVerse(
                        id = verseId,
                        text = verseText,
                        words = words
                    )
                )
            }
        }

        return QuranChapter(
            id = root.getInt("id"),
            name = root.getString("name"),
            transliteration = root.getString("transliteration"),
            type = root.getString("type"),
            totalVerses = root.getInt("total_verses"),
            verses = verses
        )
    }

    private fun fetchJson(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "QuranFollowReader/1.0")

        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CHAPTER_INDEX_URL =
            "https://cdn.jsdelivr.net/npm/quran-json@3.1.2/dist/chapters/index.json"
    }
}
