package com.naekimyeon.pleos.platform

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Schema-aligned incoming action. It only prepares a screen; it never starts navigation. */
data class GleoBreakRequest(
    val need: String?,
    val timeMinutes: Int? = null,
    val noTimeLimit: Boolean = false,
    val feeling: String? = null
) {
    // The phrase is transient UI input, not diagnostic or callback content.
    override fun toString() = "GleoBreakRequest(need=$need, timeMinutes=$timeMinutes, noTimeLimit=$noTimeLimit, hasFeeling=${feeling != null})"
}

sealed class GleoParseResult {
    data object NotHandled : GleoParseResult()
    data class Accepted(val request: GleoBreakRequest) : GleoParseResult()
    data class Asking(val message: String) : GleoParseResult()
    data class Rejected(val message: String) : GleoParseResult()
}

enum class GleoResponseStatus(val wireValue: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }

/**
 * Gleo's public Android intent contract, without any claim of schema approval or live voice access.
 * Parse in onCreate/onNewIntent, apply Accepted to UI, then call respondSampleOpened().
 * ACTION_PARAM is the documented code-example envelope; separate extras are also documented.
 */
object GleoBridge {
    const val ACTION_FIND_BREAK_PLACE = "FIND_BREAK_PLACE"
    const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    private val needs = setOf("breeze", "conversation", "shelter")

    fun parse(intent: Intent?): GleoParseResult {
        if (intent?.action != ACTION_FIND_BREAK_PLACE) return GleoParseResult.NotHandled
        return try {
            val parameters = readParameters(intent)
            val rawNeed = parameters.opt("need")
            val need = if (rawNeed == null || rawNeed === JSONObject.NULL) null else {
                if (rawNeed !is String || rawNeed !in needs) {
                    return GleoParseResult.Rejected("The requested break preference is not supported.")
                }
                rawNeed
            }
            val rawFeeling = parameters.opt("feeling")
            val feeling = if (rawFeeling == null || rawFeeling === JSONObject.NULL) null else {
                if (rawFeeling !is String || rawFeeling.length > 120 || rawFeeling.isBlank()) {
                    return GleoParseResult.Rejected("The feeling must be a nonblank phrase of at most 120 characters.")
                }
                rawFeeling.trim()
            }
            if (need == null && feeling == null) {
                return GleoParseResult.Asking("Please share a short feeling or choose a break preference.")
            }
            val rawMinutes = parameters.opt("timeMinutes")
            val minutes = if (rawMinutes == null || rawMinutes === JSONObject.NULL) null else {
                if (rawMinutes !is Number) return GleoParseResult.Rejected("The time budget must be a whole number of minutes.")
                val number = rawMinutes.toDouble()
                if (!number.isFinite() || number != number.toInt().toDouble() || number !in 1.0..240.0) {
                    return GleoParseResult.Rejected("The time budget must be between 1 and 240 whole minutes.")
                }
                number.toInt()
            }
            val rawUnlimited = parameters.opt("noTimeLimit")
            val noTimeLimit = if (rawUnlimited == null || rawUnlimited === JSONObject.NULL) false else {
                if (rawUnlimited !is Boolean) return GleoParseResult.Rejected("The no-time-limit preference must be true or false.")
                rawUnlimited
            }
            if (minutes != null && noTimeLimit) {
                return GleoParseResult.Asking("Please choose either a time budget or no time limit.")
            }
            GleoParseResult.Accepted(GleoBreakRequest(need, minutes, noTimeLimit, feeling))
        } catch (error: Exception) {
            GleoParseResult.Rejected("The break request could not be read. Please try again.")
        }
    }

    /** Only call after the requested sample screen has actually been opened. */
    fun respondSampleOpened(context: Context, request: GleoBreakRequest): Boolean {
        val data = JSONArray().put(JSONObject().apply {
            request.need?.let { put("need", it) }
            put("hasFeeling", request.feeling != null)
            request.timeMinutes?.let { put("timeMinutes", it) }
            put("noTimeLimit", request.noTimeLimit)
            put("dataSource", "sample")
            put("liveRecommendationConnected", false)
            put("navigationStarted", false)
        })
        return respond(
            context,
            GleoResponseStatus.SUCCESS,
            "Sample candidates have been opened on the screen. Live recommendations are not connected.",
            data
        )
    }

    /**
     * Call only after a known-safe driving panel has presented this single sample candidate.
     * This acknowledges selection/display only; a separate explicit user action owns navigation.
     */
    fun respondDrivingCandidate(context: Context, request: GleoBreakRequest, candidateTitle: String): Boolean {
        val title = candidateTitle.trim().take(80)
        if (title.isBlank()) {
            return respond(context, GleoResponseStatus.FAIL, "A sample destination could not be selected.")
        }
        val data = JSONArray().put(JSONObject().apply {
            put("candidateTitle", title)
            put("candidateCount", 1)
            put("dataSource", "sample")
            put("hasFeeling", request.feeling != null)
            put("liveRecommendationConnected", false)
            put("navigationStarted", false)
            put("confirmationRequired", true)
        })
        return respond(
            context,
            GleoResponseStatus.SUCCESS,
            "One sample outing option is ready. Navigation will start only after explicit confirmation.",
            data
        )
    }

    /** Public catalog title only. No origin coordinates, accuracy value, or raw feeling are sent. */
    fun respondNearbyCandidate(context: Context, request: GleoBreakRequest, candidateTitle: String,
        mockOrigin: Boolean, approximateOrigin: Boolean, emulatedOrigin: Boolean = false): Boolean {
        val title = candidateTitle.trim().take(80)
        if (title.isBlank()) return respond(context, GleoResponseStatus.FAIL, "A nearby destination could not be selected.")
        val data = JSONArray().put(JSONObject().apply {
            put("candidateTitle", title)
            put("dataSource", "curated_catalog")
            put("locationSource", if (emulatedOrigin) "emulator" else if (mockOrigin) "mock" else "device")
            put("mockLocationFlag", mockOrigin)
            put("approximateOrigin", approximateOrigin)
            put("proximityBasis", "straight_line")
            put("hasFeeling", request.feeling != null)
            put("navigationStarted", false)
            put("confirmationRequired", true)
            put("liveTrafficConnected", false)
            put("liveParkingAvailabilityConnected", false)
        })
        return respond(context, GleoResponseStatus.SUCCESS,
            if (emulatedOrigin) "A curated place was selected using an emulator location, not a real vehicle location. Navigation still requires explicit confirmation."
            else if (mockOrigin) "A curated place was selected using a mock location. Navigation still requires explicit confirmation."
            else "A nearby place from the curated catalog is ready. Road travel time and parking availability are not verified.", data)
    }

    /** A true return means broadcast submission only; the Gleo app does not acknowledge delivery. */
    fun respond(
        context: Context,
        status: GleoResponseStatus,
        message: String,
        data: JSONArray? = null
    ): Boolean = runCatching {
        val response = JSONObject().apply {
            put("functionName", ACTION_FIND_BREAK_PLACE)
            put("status", status.wireValue)
            put("message", message.take(500))
            put("mode", "voice")
            data?.let { put("data", it.toString()) }
        }
        // Official Gleo sample defines this action and these two extras, without a target package.
        // Do not include location, account identifiers, or other private information in the response.
        context.sendBroadcast(Intent(RESPONSE_ACTION).apply {
            putExtra("packageName", context.packageName)
            putExtra("response", response.toString())
        })
        true
    }.getOrDefault(false)

    private fun readParameters(intent: Intent): JSONObject {
        val envelopeKey = when {
            intent.hasExtra("ACTION_PARAM") -> "ACTION_PARAM"
            intent.hasExtra("actionParameters") -> "actionParameters"
            else -> null
        }
        if (envelopeKey != null) {
            val raw = intent.getStringExtra(envelopeKey) ?: error("Action parameters must be a JSON string")
            require(raw.length <= 4096) { "Oversized action parameters" }
            val parsed = readWholeJsonValue(raw)
            return when (parsed) {
                is JSONObject -> parsed
                // The official sending example also shows a JSON-string-wrapped object.
                is String -> readWholeJsonValue(parsed) as? JSONObject ?: error("Action parameters must wrap an object")
                else -> error("Action parameters must be an object")
            }
        }
        val extras = intent.extras
        return JSONObject().apply {
            for (key in listOf("need", "timeMinutes", "noTimeLimit", "feeling")) {
                if (extras?.containsKey(key) == true) {
                    @Suppress("DEPRECATION")
                    val value = extras.get(key)
                    require(value is String || value is Number || value is Boolean || value == null)
                    put(key, value)
                }
            }
        }
    }

    private fun readWholeJsonValue(raw: String): Any? {
        val tokens = JSONTokener(raw)
        val value = tokens.nextValue()
        require(tokens.nextClean() == '\u0000') { "Unexpected content after action parameters" }
        return value
    }
}
