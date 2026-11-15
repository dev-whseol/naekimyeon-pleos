package com.naekimyeon.pleos.platform

import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against Android's real Intent/Bundle/JSON implementations, without launching an Activity,
 * connecting NaviHelper, or sending a broadcast to a live Gleo service.
 */
@RunWith(AndroidJUnit4::class)
class GleoBridgeInstrumentedTest {
    private fun action() = Intent(GleoBridge.ACTION_FIND_BREAK_PLACE)
    private fun json(raw: String, key: String = "ACTION_PARAM") = action().putExtra(key, raw)

    private fun accepted(intent: Intent): GleoBreakRequest {
        val result = GleoBridge.parse(intent)
        assertTrue("Expected a screen request, received $result", result is GleoParseResult.Accepted)
        return (result as GleoParseResult.Accepted).request
    }

    private fun assertRejected(intent: Intent) {
        val result = GleoBridge.parse(intent)
        assertTrue("Expected rejection, received $result", result is GleoParseResult.Rejected)
        assertTrue((result as GleoParseResult.Rejected).message.isNotBlank())
    }

    @Test fun acceptsAllThreePreferencesUsingTypedAndroidExtras() {
        for (need in listOf("breeze", "conversation", "shelter")) {
            assertEquals(GleoBreakRequest(need, 60, false), accepted(action().putExtra("need", need).putExtra("timeMinutes", 60)))
        }
    }

    @Test fun acceptsDocumentedJsonAndCompatibilityEnvelope() {
        val raw = """{"need":"shelter","timeMinutes":45,"noTimeLimit":false}"""
        for (key in listOf("ACTION_PARAM", "actionParameters")) {
            assertEquals(GleoBreakRequest("shelter", 45, false), accepted(json(raw, key)))
        }
    }

    @Test fun acceptsSingleStringWrappedJsonObjectFromOfficialExampleFormat() {
        val raw = """{"need":"conversation","noTimeLimit":true}"""
        assertEquals(GleoBreakRequest("conversation", null, true), accepted(json(JSONObject.quote(raw))))
    }

    @Test fun omittedTimeIsDifferentFromExplicitNoTimeLimit() {
        val unspecified = accepted(action().putExtra("need", "breeze"))
        val unlimited = accepted(action().putExtra("need", "breeze").putExtra("noTimeLimit", true))
        assertNull(unspecified.timeMinutes)
        assertFalse(unspecified.noTimeLimit)
        assertNull(unlimited.timeMinutes)
        assertTrue(unlimited.noTimeLimit)
    }

    @Test fun acceptsInclusiveMinuteBoundariesAndWholeNumericValues() {
        for (minutes in listOf(1, 240)) {
            assertEquals(minutes, accepted(action().putExtra("need", "breeze").putExtra("timeMinutes", minutes)).timeMinutes)
        }
        assertEquals(60, accepted(action().putExtra("need", "breeze").putExtra("timeMinutes", 60L)).timeMinutes)
        assertEquals(60, accepted(action().putExtra("need", "breeze").putExtra("timeMinutes", 60.0)).timeMinutes)
    }

    @Test fun missingPreferenceAndFeelingAsksForMoreInformation() {
        for (intent in listOf(action(), json("{}"), json("""{"need":null,"timeMinutes":30}"""))) {
            val result = GleoBridge.parse(intent)
            assertTrue("Expected clarification, received $result", result is GleoParseResult.Asking)
            assertTrue((result as GleoParseResult.Asking).message.isNotBlank())
        }
    }

    @Test fun unknownEmptyOrWrongTypePreferencesAreRejected() {
        for (need in listOf("", "BREEZE", "coffee", "navigate", "breeze;REQUEST_ROUTE")) {
            assertRejected(action().putExtra("need", need))
        }
        assertRejected(action().putExtra("need", 1))
        assertRejected(action().putExtra("need", true))
        assertRejected(json("""{"need":["breeze"]}"""))
    }

    @Test fun malformedIncompleteOrNonObjectJsonIsRejected() {
        for (raw in listOf("", "{", "{\"need\":", "[]", "true", "42", "\"breeze\"")) {
            assertRejected(json(raw))
        }
    }

    @Test fun trailingPayloadCannotHideBehindAValidJsonObject() {
        val valid = """{"need":"breeze"}"""
        assertRejected(json("$valid trailing"))
        assertRejected(json("$valid {}"))
        assertRejected(json(JSONObject.quote("$valid trailing")))
    }

    @Test fun wrongTypeEnvelopeCannotFallBackToOtherwiseValidDirectExtras() {
        assertRejected(action().putExtra("need", "breeze").putExtra("ACTION_PARAM", 123))
        assertRejected(action().putExtra("need", "breeze").putExtra("actionParameters", true))
    }

    @Test fun fractionalStringAndNonFiniteTimeBudgetsAreRejected() {
        assertRejected(action().putExtra("need", "breeze").putExtra("timeMinutes", 20.5))
        assertRejected(action().putExtra("need", "breeze").putExtra("timeMinutes", "60"))
        assertRejected(action().putExtra("need", "breeze").putExtra("timeMinutes", true))
        assertRejected(action().putExtra("need", "breeze").putExtra("timeMinutes", Double.NaN))
        assertRejected(action().putExtra("need", "breeze").putExtra("timeMinutes", Double.POSITIVE_INFINITY))
    }

    @Test fun outOfRangeTimeBudgetsAreRejected() {
        for (minutes in listOf(-1, 0, 241, Int.MAX_VALUE)) {
            assertRejected(action().putExtra("need", "shelter").putExtra("timeMinutes", minutes))
        }
        assertRejected(json("""{"need":"shelter","timeMinutes":1e100}"""))
    }

    @Test fun unlimitedFlagMustBeBoolean() {
        assertRejected(action().putExtra("need", "breeze").putExtra("noTimeLimit", "true"))
        assertRejected(action().putExtra("need", "breeze").putExtra("noTimeLimit", 1))
        assertRejected(json("""{"need":"breeze","noTimeLimit":[]}"""))
    }

    @Test fun conflictingBoundedAndUnlimitedTimeAsksForCorrection() {
        val result = GleoBridge.parse(action().putExtra("need", "breeze").putExtra("timeMinutes", 45).putExtra("noTimeLimit", true))
        assertTrue(result is GleoParseResult.Asking)
    }

    @Test fun falseUnlimitedFlagDoesNotConflictWithNumericBudget() {
        assertEquals(GleoBreakRequest("shelter", 45, false), accepted(action().putExtra("need", "shelter").putExtra("timeMinutes", 45).putExtra("noTimeLimit", false)))
    }

    @Test fun oversizedJsonIsRejectedBeforeRequestConstruction() {
        val oversized = """{"need":"breeze","padding":"${"x".repeat(4096)}"}"""
        assertRejected(json(oversized))
    }

    @Test fun whitespacePaddedJsonAtLimitRemainsValid() {
        val valid = """{"need":"breeze"}""".padEnd(4096, ' ')
        assertEquals(GleoBreakRequest("breeze"), accepted(json(valid)))
    }

    @Test fun nullLauncherAndNavigationActionsAreNotHandled() {
        assertSame(GleoParseResult.NotHandled, GleoBridge.parse(null))
        for (name in listOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW, "NAVIGATE", "REQUEST_ROUTE", "FIND_ALONG_ROUTE")) {
            assertSame(GleoParseResult.NotHandled, GleoBridge.parse(Intent(name).putExtra("need", "breeze").putExtra("ACTION_PARAM", "{")))
        }
    }

    @Test fun injectedRouteFieldsCannotCreateANavigationCommand() {
        val input = json("""{"need":"breeze","timeMinutes":30,"navigate":true,"action":"REQUEST_ROUTE","latitude":37.5,"longitude":127.0}""")
        // The accepted type carries only screen preferences; route commands are not in this protocol.
        assertEquals(GleoBreakRequest("breeze", 30, false), accepted(input))
        assertEquals(GleoBridge.ACTION_FIND_BREAK_PLACE, input.action)
    }

    @Test fun envelopeHasDeterministicPrecedenceAndInputIntentIsNotMutated() {
        val raw = """{"need":"shelter","timeMinutes":20}"""
        val input = json(raw).putExtra("need", "breeze").putExtra("timeMinutes", 60)
        assertEquals(GleoBreakRequest("shelter", 20, false), accepted(input))
        assertEquals(raw, input.getStringExtra("ACTION_PARAM"))
        assertEquals("breeze", input.getStringExtra("need"))
        assertEquals(60, input.getIntExtra("timeMinutes", -1))
    }

    @Test fun sampleCallbackStatesSampleSourceAndNoLiveNavigation() {
        val context = RecordingContext()
        assertTrue(GleoBridge.respondSampleOpened(context, GleoBreakRequest("breeze", 60)))
        val sent = context.broadcast
        assertNotNull(sent)
        assertEquals(GleoBridge.RESPONSE_ACTION, sent!!.action)
        assertEquals(context.packageName, sent.getStringExtra("packageName"))
        val response = JSONObject(sent.getStringExtra("response")!!)
        assertEquals(GleoBridge.ACTION_FIND_BREAK_PLACE, response.getString("functionName"))
        assertEquals("success", response.getString("status"))
        assertTrue(response.getString("message").contains("Live recommendations are not connected"))
        val data = JSONArray(response.getString("data")).getJSONObject(0)
        assertEquals("sample", data.getString("dataSource"))
        assertFalse(data.getBoolean("liveRecommendationConnected"))
        assertFalse(data.getBoolean("navigationStarted"))
    }

    @Test fun callbackReturnReportsSubmissionAndHandlesPermissionFailure() {
        val context = RecordingContext()
        // No real broadcast or Gleo receiver is involved: true cannot establish service receipt.
        assertTrue(GleoBridge.respond(context, GleoResponseStatus.ASKING, "A preference is required."))
        context.reject = true
        assertFalse(GleoBridge.respond(context, GleoResponseStatus.FAIL, "The screen is unavailable."))
    }

    @Test fun acceptsFeelingOnlyWithoutInventingAnActivityPreference() {
        val direct = accepted(action().putExtra("feeling", "  머리가 복잡해서 잠깐 쉬고 싶어  "))
        assertNull(direct.need)
        assertEquals("머리가 복잡해서 잠깐 쉬고 싶어", direct.feeling)
        val envelope = accepted(json("""{"feeling":"조용히 숨 돌리고 싶어","timeMinutes":30}"""))
        assertNull(envelope.need)
        assertEquals("조용히 숨 돌리고 싶어", envelope.feeling)
        assertEquals(30, envelope.timeMinutes)
    }

    @Test fun preservesExplicitPreferenceWhenAFeelingIsAlsoProvided() {
        val request = accepted(action().putExtra("need", "shelter").putExtra("feeling", "비를 보며 쉬고 싶어"))
        assertEquals("shelter", request.need)
        assertEquals("비를 보며 쉬고 싶어", request.feeling)
        assertRejected(action().putExtra("need", "unsupported").putExtra("feeling", "쉬고 싶어"))
    }

    @Test fun feelingMustBeANonblankStringAndMissingBothStillAsks() {
        assertRejected(action().putExtra("feeling", 123))
        assertRejected(action().putExtra("feeling", true))
        assertRejected(action().putExtra("feeling", "   "))
        assertRejected(json("""{"feeling":["쉬고 싶어"]}"""))
        assertTrue(GleoBridge.parse(json("""{"need":null,"feeling":null}""")) is GleoParseResult.Asking)
    }

    @Test fun feelingLengthLimitAccepts120CharactersAndRejects121() {
        val maximum = "가".repeat(120)
        assertEquals(maximum, accepted(action().putExtra("feeling", maximum)).feeling)
        assertRejected(action().putExtra("feeling", "가".repeat(121)))
        assertRejected(json(JSONObject().put("feeling", "a".repeat(121)).toString()))
    }

    @Test fun callbackAndDiagnosticDescriptionNeverEchoTheFeelingPhrase() {
        val phrase = "오늘은 말없이 마음을 가라앉히고 싶어"
        val request = accepted(action().putExtra("feeling", phrase))
        val context = RecordingContext()
        assertTrue(GleoBridge.respondSampleOpened(context, request))
        val rawResponse = context.broadcast!!.getStringExtra("response")!!
        assertFalse(rawResponse.contains(phrase))
        assertFalse(request.toString().contains(phrase))
        val response = JSONObject(rawResponse)
        val data = JSONArray(response.getString("data")).getJSONObject(0)
        assertTrue(data.getBoolean("hasFeeling"))
        assertFalse(data.has("feeling"))
        assertFalse(data.has("need"))
        assertFalse(data.getBoolean("navigationStarted"))
    }

    @Test fun drivingCallbackProvidesExactlyOneCandidateAndRequiresExplicitNavigation() {
        val context = RecordingContext()
        val phrase = "답답해서 잠깐 바람을 쐬고 싶어"
        assertTrue(GleoBridge.respondDrivingCandidate(context, GleoBreakRequest(null, feeling = phrase), "강변 산책길"))
        val raw = context.broadcast!!.getStringExtra("response")!!
        assertFalse(raw.contains(phrase))
        val response = JSONObject(raw)
        assertEquals("success", response.getString("status"))
        assertEquals("voice", response.getString("mode"))
        val candidates = JSONArray(response.getString("data"))
        assertEquals(1, candidates.length())
        val candidate = candidates.getJSONObject(0)
        assertEquals("강변 산책길", candidate.getString("candidateTitle"))
        assertEquals("sample", candidate.getString("dataSource"))
        assertTrue(candidate.getBoolean("confirmationRequired"))
        assertFalse(candidate.getBoolean("navigationStarted"))
        assertFalse(candidate.getBoolean("liveRecommendationConnected"))
    }

    @Test fun missingDrivingCandidateCannotBeAcknowledgedAsSuccess() {
        val context = RecordingContext()
        assertTrue(GleoBridge.respondDrivingCandidate(context, GleoBreakRequest("breeze"), "   "))
        val response = JSONObject(context.broadcast!!.getStringExtra("response")!!)
        assertEquals("fail", response.getString("status"))
        assertFalse(response.has("data"))
    }

    @Test fun nearbyCallbackDistinguishesCuratedEmulatorDataWithoutSendingOriginOrFeeling() {
        val context = RecordingContext()
        val phrase = "오늘은 조용한 곳에서 쉬고 싶어"
        assertTrue(GleoBridge.respondNearbyCandidate(context, GleoBreakRequest(null, feeling = phrase),
            "서울숲", mockOrigin = false, approximateOrigin = true, emulatedOrigin = true))
        val raw = context.broadcast!!.getStringExtra("response")!!
        assertFalse(raw.contains(phrase))
        val response = JSONObject(raw)
        val item = JSONArray(response.getString("data")).getJSONObject(0)
        assertEquals("curated_catalog", item.getString("dataSource"))
        assertEquals("emulator", item.getString("locationSource"))
        assertFalse(item.getBoolean("mockLocationFlag"))
        assertTrue(item.getBoolean("approximateOrigin"))
        assertFalse(item.getBoolean("navigationStarted"))
        assertFalse(item.has("latitude"))
        assertFalse(item.has("longitude"))
        assertFalse(item.has("accuracyMeters"))
        assertFalse(item.has("feeling"))
    }

    private class RecordingContext : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        var broadcast: Intent? = null
        var reject = false

        override fun sendBroadcast(intent: Intent) {
            if (reject) throw SecurityException("Test transport rejects submission")
            broadcast = Intent(intent)
        }
    }
}
