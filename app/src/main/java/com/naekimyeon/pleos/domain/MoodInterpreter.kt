package com.naekimyeon.pleos.domain

import java.util.Locale

enum class MoodIntent(val phrase: String, val shortLabel: String) {
    RESET("머리 좀 비우고 싶어", "생각을 잠깐 내려놓기"),
    TOGETHER("둘이 가볍게 다녀오고 싶어", "함께 가기 좋은 곳"),
    DISCOVERY("색다른 곳에 가보고 싶어", "새로운 분위기 둘러보기"),
    REST("오늘은 편하게 쉬고 싶어", "아무것도 서두르지 않기"),
}

sealed class MoodInterpretation {
    data object Empty : MoodInterpretation()
    data object Unknown : MoodInterpretation()
    data class Matched(val mood: MoodIntent) : MoodInterpretation()
}

/** Explicit, local example-keyword matching. No diagnosis, personality inference, network or text storage. */
class MoodInterpreter {
    fun interpret(input: String): MoodInterpretation {
        if (input.length > MAX_INPUT_LENGTH) return MoodInterpretation.Unknown
        val text = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (text.isBlank()) return MoodInterpretation.Empty
        // Negated preferences require an explicit choice instead of guessing their opposite.
        if (Regex("(쉬|휴식|함께|둘이|새로|색다|비우).{0,10}(싫|않|말고)").containsMatchIn(text)) {
            return MoodInterpretation.Unknown
        }
        val scored = keywords.map { (mood, words) -> mood to words.count { text.contains(it) } }
        val best = scored.maxOf { it.second }
        if (best == 0) return MoodInterpretation.Unknown
        val winners = scored.filter { it.second == best }
        // Ambiguous mixed feelings are not silently resolved to a made-up intent.
        return if (winners.size == 1) MoodInterpretation.Matched(winners.single().first) else MoodInterpretation.Unknown
    }

    companion object {
        const val MAX_INPUT_LENGTH = 120
        private val keywords = linkedMapOf(
            MoodIntent.RESET to listOf("머리", "비우", "생각", "복잡", "답답", "리셋"),
            MoodIntent.TOGETHER to listOf("둘이", "함께", "추억", "데이트", "친구", "연인", "대화"),
            MoodIntent.DISCOVERY to listOf("색다", "새로", "낯선", "발견", "재밌", "재미", "특별", "구경", "모험"),
            MoodIntent.REST to listOf("쉬", "휴식", "편하", "편하게", "편안", "가만", "아무것도", "피곤", "지친"),
        )
    }
}
