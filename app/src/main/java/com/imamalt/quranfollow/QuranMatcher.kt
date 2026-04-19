package com.imamalt.quranfollow

import kotlin.math.abs

data class MatchResult(
    val activeGlobalIndex: Int,
    val activeVerseId: Int,
    val matchedTokenCount: Int,
    val verseSpan: Int,
    val isConfident: Boolean,
    val statusMessage: String
)

private data class MatchCandidate(
    val endPosition: QuranWordPosition,
    val matchedTokenCount: Int,
    val verseSpan: Int,
    val score: Int
)

class QuranMatcher(private val chapter: QuranChapter) {
    private val positions = chapter.allWords

    fun findBestMatch(
        recognizedText: String,
        currentAnchorGlobalIndex: Int?
    ): MatchResult? {
        val recognizedTokens = ArabicTextTools.tokenize(recognizedText)
        if (recognizedTokens.isEmpty()) return null

        val tailTokens = recognizedTokens.takeLast(MAX_TOKEN_WINDOW)
        val lastToken = tailTokens.last()
        val candidates = mutableListOf<MatchCandidate>()

        for (index in positions.indices) {
            val endPosition = positions[index]
            if (endPosition.normalized != lastToken) continue

            var matchedTokenCount = 1
            var wordCursor = index - 1
            var tokenCursor = tailTokens.lastIndex - 1
            val spannedVerses = linkedSetOf(endPosition.verseId)

            while (wordCursor >= 0 && tokenCursor >= 0) {
                if (positions[wordCursor].normalized == tailTokens[tokenCursor]) {
                    matchedTokenCount += 1
                    spannedVerses += positions[wordCursor].verseId
                    wordCursor -= 1
                    tokenCursor -= 1
                } else {
                    break
                }
            }

            var score = matchedTokenCount * 10
            if (spannedVerses.size >= 2) score += 40

            if (currentAnchorGlobalIndex != null) {
                val delta = endPosition.globalIndex - currentAnchorGlobalIndex
                if (delta in 0..40) score += 12
                if (delta < -5) score -= 25
                score -= abs(delta) / 30
            }

            candidates += MatchCandidate(
                endPosition = endPosition,
                matchedTokenCount = matchedTokenCount,
                verseSpan = spannedVerses.size,
                score = score
            )
        }

        if (candidates.isEmpty()) return null

        val sortedCandidates = candidates.sortedWith(
            compareByDescending<MatchCandidate> { it.score }
                .thenByDescending { it.matchedTokenCount }
                .thenByDescending { it.endPosition.globalIndex }
        )

        val best = sortedCandidates.first()
        val runnerUp = sortedCandidates.getOrNull(1)
        val scoreGap = if (runnerUp == null) 99 else best.score - runnerUp.score

        val isConfident = when {
            best.matchedTokenCount >= 7 -> true
            best.matchedTokenCount >= 4 && best.verseSpan >= 2 -> true
            best.matchedTokenCount >= 5 && scoreGap >= 10 -> true
            else -> false
        }

        val statusMessage = if (isConfident) {
            if (best.verseSpan >= 2) {
                "Presisi tinggi: konteks cocok melintasi ${best.verseSpan} ayat."
            } else {
                "Presisi cukup: frasa unik ${best.matchedTokenCount} kata."
            }
        } else {
            "Konteks belum cukup. Posisi ditahan sampai frasa lebih panjang atau melintasi minimal 2 ayat."
        }

        return MatchResult(
            activeGlobalIndex = best.endPosition.globalIndex,
            activeVerseId = best.endPosition.verseId,
            matchedTokenCount = best.matchedTokenCount,
            verseSpan = best.verseSpan,
            isConfident = isConfident,
            statusMessage = statusMessage
        )
    }

    private companion object {
        const val MAX_TOKEN_WINDOW = 12
    }
}
