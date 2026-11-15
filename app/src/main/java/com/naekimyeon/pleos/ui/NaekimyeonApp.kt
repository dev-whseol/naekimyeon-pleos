@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.naekimyeon.pleos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naekimyeon.pleos.R
import com.naekimyeon.pleos.domain.*
import kotlinx.coroutines.delay

/** Native Compose UI; all displayed place/travel data is deliberately an offline demo fixture. */
@Composable
fun NaekimyeonApp(
    navigationStatus: String,
    onNavigate: (Place) -> Unit,
    restricted: Boolean = true,
    incomingRequest: IncomingRequest? = null,
    onIncomingRequestApplied: (Long) -> Unit = {},
    restrictionMessage: String = "차량 이용 제한 중",
    onIncomingFeelingUnmatched: (Long) -> Unit = {},
    drivingAvailable: Boolean = false,
    onDrivingCandidateApplied: (Long, Place?) -> Unit = { _, _ -> },
    drivingMaxStringLength: Int = 120,
    drivingMaxCandidates: Int = 3,
    navigationPlaceId: String? = null,
) {
    NaekimyeonTheme {
        val keyboard = LocalSoftwareKeyboardController.current
        var screen by rememberSaveable { mutableStateOf("home") }
        var needName by rememberSaveable { mutableStateOf("") }
        var timeCode by rememberSaveable { mutableStateOf("unspecified") }
        var sameOrigin by rememberSaveable { mutableStateOf(false) }
        var selectedId by rememberSaveable { mutableStateOf("") }
        var showInfo by remember { mutableStateOf(false) }
        var showNavigationConfirmation by remember { mutableStateOf(false) }
        var navigationRequested by rememberSaveable { mutableStateOf(false) }
        var feedback by rememberSaveable { mutableStateOf("") }
        var lastIncomingId by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
        // Raw feeling text lives only in this composition, never in saved state, preferences or a request log.
        var feeling by remember { mutableStateOf("") }
        var moodName by rememberSaveable { mutableStateOf("") }
        var unknownFeeling by remember { mutableStateOf(false) }
        var conditionsExpanded by rememberSaveable { mutableStateOf(false) }
        var variation by rememberSaveable { mutableIntStateOf(0) }
        var showDraw by remember { mutableStateOf(false) }
        var drawnPlaceId by rememberSaveable { mutableStateOf("") }
        var pendingDriverIncomingId by remember { mutableStateOf<Long?>(null) }
        var pendingDriverPlaceId by remember { mutableStateOf<String?>(null) }
        var nextCoolingDown by remember { mutableStateOf(false) }
        LaunchedEffect(nextCoolingDown) {
            if (nextCoolingDown) {
                delay(2_000)
                nextCoolingDown = false
            }
        }
        val mood = MoodIntent.entries.firstOrNull { it.name == moodName }
        val need = Need.entries.firstOrNull { it.name == needName }
        val time = when (timeCode) {
            "unlimited" -> TimePreference.Unlimited
            else -> timeCode.toIntOrNull()?.takeIf { it in 1..240 }?.let { TimePreference.Bounded(it) } ?: TimePreference.Unspecified
        }
        val request = RecommendationRequest(
            needs = need?.let { setOf(it) } ?: emptySet(),
            time = time,
            returnPlan = if (sameOrigin) ReturnPlan.SAME_ORIGIN else ReturnPlan.UNSPECIFIED,
        )
        val result = remember(needName, timeCode, sameOrigin, moodName, variation) {
            SceneRecommender().recommend(DemoPlaces.places, request, mood, variation)
        }
        val selected = DemoPlaces.places.firstOrNull { it.id == selectedId }
        val selectedScene = (result as? SceneResult.Ready)?.scenes?.firstOrNull { it.place.id == selectedId }
        val eligibleScenes = (result as? SceneResult.Ready)?.scenes.orEmpty()
        val drawnScene = eligibleScenes.firstOrNull { it.place.id == drawnPlaceId }
        val driverScenes = eligibleScenes.take(drivingMaxCandidates.coerceIn(0, 3))
        val driverScene = driverScenes.firstOrNull { it.place.id == selectedId } ?: driverScenes.firstOrNull()
        val unappliedIncomingForFrame = incomingRequest != null && incomingRequest.id != lastIncomingId
        val pendingDriverIdForFrame = pendingDriverIncomingId
        val pendingDriverPlaceForFrame = pendingDriverPlaceId
        LaunchedEffect(incomingRequest?.id, restricted, drivingAvailable) {
            if (restricted) {
                showNavigationConfirmation = false
                showInfo = false
                showDraw = false
                keyboard?.hide()
                if (!drivingAvailable) pendingDriverIncomingId = null
            }
            incomingRequest?.takeIf { (!restricted || drivingAvailable) && it.id != lastIncomingId }?.let { incoming ->
                val incomingFeeling = incoming.feeling.orEmpty().take(MoodInterpreter.MAX_INPUT_LENGTH)
                val interpretation = MoodInterpreter().interpret(incomingFeeling)
                if (restricted && interpretation == MoodInterpretation.Unknown) {
                    unknownFeeling = true
                    lastIncomingId = incoming.id
                    onIncomingFeelingUnmatched(incoming.id)
                    return@let
                }
                val incomingMood = (interpretation as? MoodInterpretation.Matched)?.mood
                val incomingTime = when {
                    incoming.unlimited -> TimePreference.Unlimited
                    incoming.minutes != null && incoming.minutes in 1..240 -> TimePreference.Bounded(incoming.minutes)
                    else -> TimePreference.Unspecified
                }
                val incomingConditions = RecommendationRequest(
                    needs = incoming.need?.let { setOf(it) } ?: emptySet(),
                    time = incomingTime, returnPlan = ReturnPlan.UNSPECIFIED,
                )
                val incomingDriverCandidate = if (restricted && drivingAvailable) {
                    (SceneRecommender().recommend(DemoPlaces.places, incomingConditions, incomingMood) as? SceneResult.Ready)
                        ?.scenes?.take(drivingMaxCandidates.coerceIn(0, 3))?.firstOrNull()
                } else null
                needName = incoming.need?.name.orEmpty()
                conditionsExpanded = incoming.need != null
                feeling = incomingFeeling
                moodName = (interpretation as? MoodInterpretation.Matched)?.mood?.name.orEmpty()
                unknownFeeling = interpretation == MoodInterpretation.Unknown
                variation = 0
                timeCode = when {
                    incoming.unlimited -> "unlimited"
                    incoming.minutes != null && incoming.minutes in 1..240 -> incoming.minutes.toString()
                    else -> "unspecified"
                }
                sameOrigin = false
                navigationRequested = false
                showNavigationConfirmation = false
                showInfo = false
                showDraw = false
                drawnPlaceId = ""
                selectedId = incomingDriverCandidate?.place?.id.orEmpty()
                feedback = ""
                screen = if (unknownFeeling) "home" else if (incomingDriverCandidate != null) "detail" else "results"
                lastIncomingId = incoming.id
                when {
                    unknownFeeling -> onIncomingFeelingUnmatched(incoming.id)
                    restricted && drivingAvailable -> {
                        pendingDriverPlaceId = incomingDriverCandidate?.place?.id
                        pendingDriverIncomingId = incoming.id
                    }
                    else -> onIncomingRequestApplied(incoming.id)
                }
            }
        }
        LaunchedEffect(restricted, drivingAvailable, driverScene?.place?.id, incomingRequest?.id, pendingDriverIdForFrame) {
            if (restricted && drivingAvailable && driverScene != null && !unappliedIncomingForFrame && pendingDriverIncomingId == null) {
                selectedId = driverScene.place.id
                // Returning to P keeps the same destination and opens its existing detail view.
                screen = "detail"
            }
        }
        LaunchedEffect(pendingDriverIdForFrame, restricted, drivingAvailable, driverScene?.place?.id, pendingDriverPlaceForFrame) {
            // These immutable values belong to this composition, never a newer request plus an old candidate.
            val id = pendingDriverIdForFrame ?: return@LaunchedEffect
            if (restricted && !drivingAvailable) return@LaunchedEffect
            if (restricted && driverScene?.place?.id != pendingDriverPlaceForFrame) return@LaunchedEffect
            if (restricted && driverScene != null) {
                selectedId = driverScene.place.id
                screen = "detail"
            }
            withFrameNanos { }
            pendingDriverIncomingId = null
            if (restricted) onDrivingCandidateApplied(id, driverScene?.place) else onIncomingRequestApplied(id)
        }
        fun reset() {
            screen = "home"; needName = ""; timeCode = "unspecified"; sameOrigin = false
            selectedId = ""; feedback = ""; navigationRequested = false
            feeling = ""; moodName = ""; unknownFeeling = false; variation = 0; conditionsExpanded = false
            showDraw = false; drawnPlaceId = ""
            pendingDriverIncomingId = null
        }
        fun drawScene() {
            ScenePicker.pick(eligibleScenes, drawnPlaceId, kotlin.random.Random.nextInt())?.let {
                drawnPlaceId = it.place.id
                showDraw = true
            }
        }
        fun explore() {
            keyboard?.hide()
            val interpretation = MoodInterpreter().interpret(feeling)
            if (interpretation == MoodInterpretation.Unknown) {
                unknownFeeling = true
            } else {
                if (interpretation is MoodInterpretation.Matched) moodName = interpretation.mood.name
                unknownFeeling = false
                screen = "results"
            }
        }
        fun back() {
            screen = when (screen) { "detail" -> "results"; "feedback" -> "detail"; else -> "home" }
        }
        BackHandler(enabled = screen != "home" && !restricted) { back() }

        Surface(color = Ink.Background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val compact = maxWidth < 720.dp
                val wide = maxWidth >= 960.dp && maxHeight >= 460.dp
                if (restricted && drivingAvailable && drivingMaxCandidates > 0) {
                    DriverPanel(
                        scene = driverScene, canShowAnother = driverScenes.size > 1 && !nextCoolingDown,
                        maxStringLength = drivingMaxStringLength,
                        status = if (navigationRequested && navigationPlaceId != null && navigationPlaceId == driverScene?.place?.id) navigationStatus
                            else if (unknownFeeling) "마음은 정차 후 다시 골라 주세요." else "",
                        onNext = {
                            val current = driverScenes.indexOfFirst { it.place.id == driverScene?.place?.id }
                            if (driverScenes.size > 1 && !nextCoolingDown) {
                                nextCoolingDown = true
                                selectedId = driverScenes[(current + 1).mod(driverScenes.size)].place.id
                                screen = "detail"; navigationRequested = false; unknownFeeling = false
                            }
                        },
                        onNavigate = {
                            driverScene?.let {
                                selectedId = it.place.id; screen = "detail"; navigationRequested = true
                                onNavigate(it.place)
                            }
                        },
                    )
                } else if (restricted) RestrictedPanel(restrictionMessage) else Column(Modifier.fillMaxSize()) {
                    AppHeader(
                        screen = screen, compact = compact,
                        onBack = { back() }, onReset = { reset() }, onInfo = { showInfo = true },
                    )
                    HorizontalDivider(color = Ink.Line.copy(alpha = .5f))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            screen == "home" -> HomeScreen(
                                wide = wide, compact = compact, need = need, timeCode = timeCode, sameOrigin = sameOrigin,
                                feeling = feeling, mood = mood, unknownFeeling = unknownFeeling,
                                conditionsExpanded = conditionsExpanded, onToggleConditions = { conditionsExpanded = !conditionsExpanded },
                                onFeeling = { feeling = it.take(MoodInterpreter.MAX_INPUT_LENGTH); moodName = ""; unknownFeeling = false; variation = 0 },
                                onMood = { feeling = it.phrase; moodName = it.name; unknownFeeling = false; variation = 0; keyboard?.hide() },
                                onNeed = { needName = if (need == it) "" else it.name },
                                onTime = { timeCode = it }, onSameOrigin = { sameOrigin = it },
                                onExplore = { explore() },
                            )
                            screen == "results" -> ResultsScreen(
                                result = result, request = request, compact = compact,
                                feeling = feeling.ifBlank { mood?.phrase.orEmpty() }, mood = mood,
                                onDraw = { drawScene() },
                                onVariation = {
                                    variation = if (mood == MoodIntent.DISCOVERY) variation + 1 else 0
                                    moodName = MoodIntent.DISCOVERY.name; feeling = MoodIntent.DISCOVERY.phrase
                                },
                                onEdit = { screen = "home" },
                                onRelax = { timeCode = "unlimited"; sameOrigin = false },
                                onPlace = { selectedId = it.id; navigationRequested = false; screen = "detail" },
                            )
                            screen == "detail" && selected != null -> DetailScreen(
                                place = selected, scene = selectedScene, request = request, wide = wide,
                                navigationStatus = if (navigationRequested) navigationStatus else "",
                                onNavigate = { showNavigationConfirmation = true },
                                onFeedback = { screen = "feedback" },
                            )
                            screen == "feedback" && selected != null -> FeedbackScreen(
                                place = selected, feedback = feedback, onFeedback = { feedback = it }, onDone = { reset() },
                            )
                            else -> EmptyPanel("잠시 길을 잃었어요", "다시 시작하면 새로운 장소를 살펴볼 수 있어요.", "처음으로", { reset() })
                        }
                    }
                }
            }
        }

        if (showDraw && drawnScene != null && !restricted) {
            SceneDrawDialog(
                scene = drawnScene, request = request, eligibleCount = eligibleScenes.size,
                onDismiss = { showDraw = false }, onReroll = { drawScene() },
                onExplore = {
                    showDraw = false; selectedId = drawnScene.place.id
                    navigationRequested = false; screen = "detail"
                },
            )
        }

        if (showInfo && !restricted) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                icon = { Symbol(Glyph.INFO, Ink.Primary, 30.dp) },
                title = { Text("가볍게 둘러보는 데모") },
                text = { Text("입력한 말의 일부 키워드를 기기 안에서 준비된 장면과 연결하는 데모예요. 마음을 진단하거나 AI가 자유롭게 해석하지 않아요. 입력 문장은 저장하거나 전송하지 않아요.\n\n장소·이동 시간·주차장 설명은 샘플이며 혼잡도, 날씨와 빈자리는 조회하지 않아요. 이미지는 실제 장소 사진이 아닌 AI 예시예요.") },
                confirmButton = { TextButton(onClick = { showInfo = false }, modifier = Modifier.heightIn(min = 52.dp)) { Text("알겠어요") } },
            )
        }
        if (showNavigationConfirmation && selected != null && !restricted) {
            AlertDialog(
                onDismissRequest = { showNavigationConfirmation = false },
                icon = { Symbol(Glyph.PIN, Ink.Primary, 30.dp) },
                title = { Text("샘플 위치로 연결할까요?") },
                text = { Text("‘${selected.title}’은 체험용 장소예요. 내비 앱에 서울 지역의 샘플 좌표를 전달해요. 실제 방문할 장소와 경로는 내비 앱에서 확인해 주세요.") },
                confirmButton = {
                    TextButton(onClick = {
                        showNavigationConfirmation = false; navigationRequested = true; onNavigate(selected)
                    }, modifier = Modifier.heightIn(min = 52.dp)) { Text("샘플 위치 보내기") }
                },
                dismissButton = { TextButton(onClick = { showNavigationConfirmation = false }, modifier = Modifier.heightIn(min = 52.dp)) { Text("둘러보기 계속") } },
            )
        }
    }
}

@Composable
private fun AppHeader(screen: String, compact: Boolean, onBack: () -> Unit, onReset: () -> Unit, onInfo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = if (compact) 18.dp else 32.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (screen != "home") {
            IconButton(onClick = onBack, modifier = Modifier.size(if (compact) 48.dp else 52.dp).semantics { contentDescription = "이전 화면" }) {
                Symbol(Glyph.BACK, Ink.White)
            }
            Spacer(Modifier.width(if (compact) 4.dp else 12.dp))
        }
        Symbol(Glyph.MARK, Ink.Primary, if (compact) 25.dp else 29.dp)
        Spacer(Modifier.width(10.dp))
        Text("내키면", fontSize = if (compact) 22.sp else 25.sp, fontWeight = FontWeight.Bold, color = Ink.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = if (compact) Modifier.weight(1f) else Modifier)
        if (!compact) {
            Spacer(Modifier.width(18.dp))
            Text("계획 없어도, 마음 가는 곳으로", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
        }
        if (!compact) Spacer(Modifier.weight(1f))
        Surface(
            onClick = onInfo, color = Ink.Raised, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Row(Modifier.padding(horizontal = if (compact) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(6.dp).background(Ink.Apricot, CircleShape))
                Text("데모", color = Ink.Apricot, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (screen != "home") {
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onReset, modifier = Modifier.size(if (compact) 48.dp else 52.dp).semantics { contentDescription = "선택 초기화하고 처음으로" }) { Symbol(Glyph.RESET) }
        }
    }
}

@Composable
private fun HomeScreen(
    wide: Boolean, compact: Boolean, need: Need?, timeCode: String, sameOrigin: Boolean,
    feeling: String, mood: MoodIntent?, unknownFeeling: Boolean, conditionsExpanded: Boolean,
    onToggleConditions: () -> Unit, onFeeling: (String) -> Unit, onMood: (MoodIntent) -> Unit,
    onNeed: (Need) -> Unit, onTime: (String) -> Unit, onSameOrigin: (Boolean) -> Unit, onExplore: () -> Unit,
) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Box(Modifier.weight(1.05f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    HomeControls(need, timeCode, sameOrigin, false, feeling, mood, unknownFeeling, conditionsExpanded,
                        onToggleConditions, onFeeling, onMood, onNeed, onTime, onSameOrigin, onExplore)
                }
            }
            HomeHero(Modifier.weight(.95f).fillMaxHeight(), tall = true, mood = mood)
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 22.dp else 30.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            HomeControls(need, timeCode, sameOrigin, compact, feeling, mood, unknownFeeling, conditionsExpanded,
                onToggleConditions, onFeeling, onMood, onNeed, onTime, onSameOrigin, onExplore)
            HomeHero(Modifier.fillMaxWidth().height(if (compact) 240.dp else 300.dp), tall = false, mood = mood)
        }
    }
}

@Composable
private fun HomeControls(
    need: Need?, timeCode: String, sameOrigin: Boolean, compact: Boolean,
    feeling: String, mood: MoodIntent?, unknownFeeling: Boolean, conditionsExpanded: Boolean,
    onToggleConditions: () -> Unit, onFeeling: (String) -> Unit, onMood: (MoodIntent) -> Unit,
    onNeed: (Need) -> Unit, onTime: (String) -> Unit, onSameOrigin: (Boolean) -> Unit, onExplore: () -> Unit,
) {
    Text("계획 없어도, 마음 가는 곳으로", color = Ink.Primary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(12.dp))
    Text("어디 가지?", fontSize = if (compact) 42.sp else 52.sp, lineHeight = if (compact) 52.sp else 62.sp,
        fontWeight = FontWeight.Bold, color = Ink.White, modifier = Modifier.semantics { heading() })
    Text("지금 기분과 시간에 맞춰 가볼 만한 곳을 골라드려요.", fontSize = if (compact) 22.sp else 26.sp, lineHeight = 36.sp, color = Ink.Muted)
    Spacer(Modifier.height(24.dp))
    SectionLabel("지금 기분이나 하고 싶은 일", "선택")
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = feeling, onValueChange = onFeeling, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("예: 머리 좀 비우고 싶어", color = Ink.Muted) },
        shape = RoundedCornerShape(17.dp), minLines = 1, maxLines = 2,
        isError = unknownFeeling,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onExplore() }),
        supportingText = { Text(if (unknownFeeling) "입력한 내용은 아직 찾기 어려워요. 아래에서 가까운 항목을 골라 주세요." else "입력은 이 화면에서만 사용해요 · ${feeling.length}/120", color = if (unknownFeeling) Ink.Apricot else Ink.Muted) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ink.Primary, unfocusedBorderColor = Ink.Line,
            focusedContainerColor = Ink.Panel, unfocusedContainerColor = Ink.Panel, errorBorderColor = Ink.Apricot),
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MoodIntent.entries.forEach { item ->
            ChoiceChip(item.phrase, mood == item, glyph = moodGlyph(item), onClick = { onMood(item) })
        }
    }
    Spacer(Modifier.height(20.dp))
    TextButton(onClick = onToggleConditions, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Symbol(Glyph.PIN, Ink.Muted, 19.dp); Spacer(Modifier.width(8.dp))
        Text(if (conditionsExpanded) "이동 조건 접기" else "이동 조건 더하기", color = Ink.Muted)
        if (need != null) Text(" · ${needLabel(need)}", color = Ink.Primary)
    }
    if (conditionsExpanded) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Need.entries.forEach { item -> ChoiceChip(needLabel(item), need == item, glyph = needGlyph(item), onClick = { onNeed(item) }) }
        }
        Spacer(Modifier.height(16.dp))
    }
    Spacer(Modifier.height(8.dp))
    SectionLabel("시간은 얼마나 여유로워요?", "선택")
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ChoiceChip("아직 몰라요", timeCode == "unspecified", onClick = { onTime("unspecified") })
        ChoiceChip("시간 제한 없이", timeCode == "unlimited", onClick = { onTime("unlimited") })
        listOf("30", "45", "75").forEach { value -> ChoiceChip("${value}분", timeCode == value, onClick = { onTime(value) }) }
        if (timeCode.toIntOrNull() != null && timeCode !in listOf("30", "45", "75")) {
            ChoiceChip("${timeCode}분", selected = true, onClick = { onTime(timeCode) })
        }
    }
    if (timeCode.toIntOrNull() != null) {
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = { onSameOrigin(!sameOrigin) }, color = if (sameOrigin) Ink.Deep else Ink.Panel,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sameOrigin, onCheckedChange = onSameOrigin)
                Column(Modifier.weight(1f)) {
                    Text("출발한 곳으로 돌아오기", style = MaterialTheme.typography.bodyMedium, color = Ink.White)
                    Text(if (sameOrigin) "왕복 이동과 여유 5분을 함께 계산해요" else "선택하면 왕복 기준으로 머물 시간을 계산해요", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    PrimaryAction("추천 장소 보기", onExplore, Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Text("고르지 않아도 괜찮아요. 서울 지역 샘플을 보여드려요.", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Text("샘플 장소 · 예상 정보 · 실시간 위치와 교통 미반영", color = Ink.Apricot, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HomeHero(modifier: Modifier, tall: Boolean, mood: MoodIntent?) {
    Box(modifier.clip(RoundedCornerShape(28.dp))) {
        Image(painterResource(if (mood == MoodIntent.REST) R.drawable.cafe_concept else R.drawable.riverside_concept), "지금 마음에 어울리는 장면의 AI 예시 이미지", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .04f), Color.Black.copy(alpha = .08f), Color(0xFF0B211A).copy(alpha = .94f)))))
        Row(Modifier.align(Alignment.TopStart).padding(22.dp)) { ImageLabel() }
        Column(Modifier.align(Alignment.BottomStart).padding(if (tall) 30.dp else 24.dp)) {
            Text("기분에 맞춘 장소와 활동 제안", style = MaterialTheme.typography.labelMedium, color = Ink.Primary)
            Spacer(Modifier.height(10.dp))
            Text(when (mood) {
                MoodIntent.REST -> "창가에서\n아무것도 안 하기."
                MoodIntent.TOGETHER -> "같은 풍경,\n서로 다른 한 컷."
                MoodIntent.DISCOVERY -> "낯선 풍경\n한 장면 찾기."
                else -> "강변 벤치에 앉아\n하늘 보기."
            }, fontSize = if (tall) 34.sp else 27.sp, lineHeight = if (tall) 45.sp else 36.sp, fontWeight = FontWeight.SemiBold, color = Ink.White)
            if (tall) {
                Spacer(Modifier.height(15.dp))
                Text("어디로 갈지, 거기서 무엇을 할지.\n도착해서 해볼 만한 가벼운 활동을 함께 제안해요.", color = Ink.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ResultsScreen(
    result: SceneResult, request: RecommendationRequest, compact: Boolean,
    feeling: String, mood: MoodIntent?, onVariation: () -> Unit, onDraw: () -> Unit,
    onEdit: () -> Unit, onRelax: () -> Unit, onPlace: (Place) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 22.dp else 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("지금 가볼 만한 곳", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text(requestSummary(request), color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 52.dp)) { Text("조건 바꾸기") }
        }
        if (feeling.isNotBlank()) {
            Spacer(Modifier.height(17.dp))
            Text("“$feeling”", color = Ink.Primary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(mood?.shortLabel.orEmpty(), color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        SampleNote()
        Spacer(Modifier.height(22.dp))
        when (result) {
            is SceneResult.Ready -> {
                Surface(
                    onClick = onDraw, color = Ink.Deep, shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Ink.Primary.copy(alpha = .4f)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp),
                ) {
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Symbol(Glyph.MARK, Ink.Apricot, 31.dp)
                        Column(Modifier.weight(1f)) {
                            Text("하나만 바로 추천받기", color = Ink.Primary, style = MaterialTheme.typography.titleLarge)
                            Text("조건에 맞는 ${result.scenes.size}곳 중 하나를 바로 골라드려요", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Symbol(Glyph.ARROW, Ink.Primary)
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (request.time is TimePreference.Bounded && request.returnPlan == ReturnPlan.UNSPECIFIED) {
                    InfoStrip("돌아올 기준을 정하면, 머물 수 있는 시간도 살펴볼 수 있어요.", Ink.Apricot)
                    Spacer(Modifier.height(18.dp))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val horizontalCards = maxWidth >= 540.dp
                    if (maxWidth >= 840.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            result.scenes.forEachIndexed { index, item ->
                                PlaceCard(item, index, Modifier.weight(1f), onClick = { onPlace(item.place) })
                            }
                            if (result.scenes.size == 1) Spacer(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            result.scenes.forEachIndexed { index, item ->
                                PlaceCard(item, index, Modifier.fillMaxWidth(), horizontal = horizontalCards, onClick = { onPlace(item.place) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                OutlinedButton(onClick = onVariation, modifier = Modifier.heightIn(min = 54.dp), border = BorderStroke(1.dp, Ink.Line)) {
                    Symbol(Glyph.MARK, Ink.Primary, 20.dp); Spacer(Modifier.width(10.dp)); Text("추천 순서 바꾸기")
                }
                Spacer(Modifier.height(9.dp))
                Text("선택한 시간과 이동 조건은 유지하고 추천 순서를 바꿔요.", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
            }
            is SceneResult.Empty -> {
                val title = when (result.reason) {
                    EmptyReason.NOT_ENOUGH_TIME -> "조금 더 여유를 가져볼까요?"
                    EmptyReason.MISSING_TRAVEL_ESTIMATES -> "이동 시간을 확인하기 어려워요"
                    EmptyReason.NO_MATCHING_NEED -> "지금 조건에 맞는 곳이 없어요"
                    EmptyReason.NO_PLACES -> "아직 보여드릴 장소가 없어요"
                }
                val body = when (result.reason) {
                    EmptyReason.NOT_ENOUGH_TIME -> "왕복 이동·접근 시간과 여유 5분을 빼면, 편히 머물 시간이 부족해요. 시간을 늘리거나 조건을 바꿔 보세요."
                    EmptyReason.MISSING_TRAVEL_ESTIMATES -> "왕복 시간을 모르는 장소는 시간 안에 다녀올 수 있다고 안내하지 않아요. 시간 조건을 풀고 살펴볼 수 있어요."
                    else -> "선택을 조금 바꾸면 다른 제안을 만날 수 있어요."
                }
                EmptyPanel(title, body, "시간 제한 없이 살펴보기", onRelax)
            }
        }
    }
}

@Composable
private fun SceneDrawDialog(
    scene: SceneSuggestion, request: RecommendationRequest, eligibleCount: Int,
    onDismiss: () -> Unit, onReroll: () -> Unit, onExplore: () -> Unit,
) {
    val place = scene.place
    val recommendation = scene.recommendation
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Panel,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Glyph.MARK, Ink.Apricot, 22.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("하나만 바로 추천", color = Ink.Apricot, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).semantics { contentDescription = "추천 창 닫기" }) {
                        Symbol(Glyph.CLOSE, Ink.Muted, 21.dp)
                    }
                }
                Text(scene.title, color = Ink.White, style = MaterialTheme.typography.headlineMedium)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                PlacePhoto(place, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(14.dp))
                Text(place.title, color = Ink.Primary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(scene.activity, color = Ink.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(7.dp))
                Text(scene.rationale, color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(15.dp))
                HorizontalDivider(color = Ink.Line)
                Spacer(Modifier.height(13.dp))
                Text("가는 길 약 ${place.outboundMinutes ?: "?"}분", color = Ink.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(7.dp))
                Text(if (place.parking.facility == FacilityState.UNKNOWN) "주차 정보 미확인 · 빈자리 미확인" else "주차장 연결 예시 · 빈자리 미확인",
                    color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                val budgetText = when (recommendation.budgetStatus) {
                    BudgetStatus.ESTIMATED -> "왕복 기준 · 약 ${recommendation.stayMinutes}분 머무르기\n돌아오는 길 ${place.returnMinutes}분 · 접근 ${place.accessMinutes}분 · 여유 ${request.safetyMinutes}분 포함"
                    BudgetStatus.NO_DEADLINE -> "시간 제한 없이 · 체류 시간은 정하지 않았어요"
                    BudgetStatus.NOT_SET -> "시간 미정 · 체류 시간은 계산하지 않았어요"
                    BudgetStatus.RETURN_PLAN_NEEDED -> "복귀 기준 미정 · 체류 시간은 계산하지 않았어요"
                }
                Text(budgetText, color = Ink.Primary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                SampleNote()
                Spacer(Modifier.height(7.dp))
                Text("활동은 안전하게 도착한 뒤, 원할 때만 해 보세요.", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onExplore, modifier = Modifier.heightIn(min = 54.dp)) { Text("${place.title} 살펴보기", color = Ink.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onReroll, enabled = eligibleCount > 1, modifier = Modifier.heightIn(min = 54.dp)) {
                Text(if (eligibleCount > 1) "다른 곳 보기" else "선택 가능한 1곳")
            }
        },
    )
}

@Composable
private fun PlaceCard(item: SceneSuggestion, index: Int, modifier: Modifier, horizontal: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = Ink.Panel, shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, Ink.Line.copy(alpha = .65f))) {
        if (horizontal) {
            Row(Modifier.heightIn(min = 265.dp)) {
                PlacePhoto(item.place, Modifier.width(210.dp).height(280.dp), index)
                Column(Modifier.weight(1f).padding(22.dp)) { PlaceCardContent(item) }
            }
        } else {
            Column {
                PlacePhoto(item.place, Modifier.fillMaxWidth().height(185.dp), index)
                Column(Modifier.padding(22.dp)) { PlaceCardContent(item) }
            }
        }
    }
}

@Composable
private fun PlaceCardContent(item: SceneSuggestion) {
    val place = item.place
    val recommendation = item.recommendation
    Text(item.mood?.shortLabel ?: "가볍게 가볼 만한 곳", color = Ink.Primary, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    Text(item.title, style = MaterialTheme.typography.titleLarge, color = Ink.White)
    Spacer(Modifier.height(5.dp))
    Text(place.title + if (place.indoor) " · 실내" else " · 야외", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(14.dp))
    Text(item.activity, color = Ink.White, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(9.dp))
    Text(item.rationale, color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Symbol(Glyph.CLOCK, Ink.Primary, 21.dp)
        Text("가는 길 약 ${place.outboundMinutes ?: "?"}분", color = Ink.White, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(9.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Symbol(Glyph.PARKING, Ink.Muted, 21.dp)
        Text(if (place.parking.facility == FacilityState.UNKNOWN) "주차 정보 미확인" else "주차장 정보 예시 · 빈자리 미확인", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (recommendation.stayMinutes != null) {
        Spacer(Modifier.height(14.dp))
        Surface(color = Ink.Deep, shape = RoundedCornerShape(10.dp)) {
            Text("약 ${recommendation.stayMinutes}분 머물 수 있어요", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Ink.Primary, style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(19.dp))
    HorizontalDivider(color = Ink.Line)
    Row(Modifier.fillMaxWidth().heightIn(min = 53.dp).padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("자세히 보기", color = Ink.Primary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f)); Symbol(Glyph.ARROW, Ink.Primary)
    }
}

@Composable
private fun PlacePhoto(place: Place, modifier: Modifier, index: Int? = null) {
    Box(modifier.clip(RoundedCornerShape(18.dp))) {
        Image(painterResource(imageResource(place)), "${place.title} 분위기의 AI 예시 이미지", Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alignment = if (place.image == PlaceImage.GARDEN) Alignment.CenterEnd else Alignment.Center)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .14f), Color.Transparent, Color.Black.copy(alpha = .2f)))))
        if (index != null) {
            Box(Modifier.padding(14.dp).size(36.dp).background(Ink.Background.copy(alpha = .84f), CircleShape), contentAlignment = Alignment.Center) {
                Text("0${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.White)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) { ImageLabel() }
    }
}

@Composable
private fun DetailScreen(
    place: Place, scene: SceneSuggestion?, request: RecommendationRequest, wide: Boolean,
    navigationStatus: String, onNavigate: () -> Unit, onFeedback: () -> Unit,
) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(.95f).fillMaxHeight()) {
                PlacePhoto(place, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(14.dp))
                Text("이미지는 장소 분위기를 표현한 AI 예시예요.", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
            }
            Column(Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                DetailContent(place, scene, request, navigationStatus, onNavigate, onFeedback)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
            PlacePhoto(place, Modifier.fillMaxWidth().height(230.dp))
            Spacer(Modifier.height(22.dp))
            DetailContent(place, scene, request, navigationStatus, onNavigate, onFeedback)
        }
    }
}

@Composable
private fun DetailContent(
    place: Place, scene: SceneSuggestion?, request: RecommendationRequest,
    navigationStatus: String, onNavigate: () -> Unit, onFeedback: () -> Unit,
) {
    val recommendation = scene?.recommendation
    Text(scene?.mood?.shortLabel ?: "추천 장소 상세", color = Ink.Primary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(9.dp))
    Text(scene?.title ?: place.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(place.title, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(14.dp))
    Text(scene?.rationale ?: place.description, color = Ink.Muted, style = MaterialTheme.typography.bodyLarge)
    if (scene != null) {
        Spacer(Modifier.height(18.dp))
        Surface(color = Ink.Deep, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("도착해서, 원한다면", color = Ink.Primary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(7.dp))
                Text(scene.activity, color = Ink.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("안전하게 정차한 뒤에만 · 혼잡도와 이용 가능 여부는 미확인", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Surface(color = Ink.Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailRow(Glyph.CLOCK, "가는 길", "약 ${place.outboundMinutes ?: "?"}분", "샘플 예상 · 현재 교통 미반영")
            HorizontalDivider(color = Ink.Line)
            DetailRow(Glyph.PARKING, "주차", if (place.parking.facility == FacilityState.UNKNOWN) "정보 미확인" else "주차장 연결 예시", "${place.parking.note}\n빈자리 · 요금 · 운영시간 미확인")
        }
    }
    Spacer(Modifier.height(16.dp))
    if (recommendation?.budgetStatus == BudgetStatus.ESTIMATED) {
        Surface(color = Ink.Deep, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Symbol(Glyph.CLOCK, Ink.Primary)
                    Text("머무는 시간 약 ${recommendation.stayMinutes}분", color = Ink.Primary, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(9.dp))
                Text("${(request.time as TimePreference.Bounded).minutes}분 − 가는 길 ${place.outboundMinutes}분 − 돌아오는 길 ${place.returnMinutes}분 − 접근 ${place.accessMinutes}분 − 여유 ${request.safetyMinutes}분", color = Ink.Primary.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(5.dp))
                Text("출발한 곳으로 돌아오는 기준 · 예상치", color = Ink.Primary.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
    } else if (request.time is TimePreference.Bounded) {
        InfoStrip("돌아올 기준이 정해지지 않아 체류 시간은 계산하지 않았어요.", Ink.Apricot)
        Spacer(Modifier.height(16.dp))
    }
    SampleNote()
    Spacer(Modifier.height(23.dp))
    PrimaryAction("내비로 연결", onNavigate, Modifier.fillMaxWidth(), Glyph.PIN)
    Spacer(Modifier.height(8.dp))
    Text("내키면 여기까지. 길 안내는 내비 앱에서 이어져요.", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
    if (navigationStatus.isNotBlank()) {
        Spacer(Modifier.height(14.dp)); InfoStrip(navigationStatus, Ink.Apricot)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Symbol(Glyph.HEART, Ink.Muted, 20.dp); Spacer(Modifier.width(9.dp))
        Text("이 선택은 어땠나요?", color = Ink.Muted)
    }
}

@Composable
private fun DetailRow(glyph: Glyph, label: String, value: String, explanation: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Symbol(glyph, Ink.Primary, 25.dp)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(15.dp))
                Text(value, color = Ink.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(5.dp))
            Text(explanation, color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeedbackScreen(place: Place, feedback: String, onFeedback: (String) -> Unit, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 650.dp).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(82.dp).background(Ink.Deep, CircleShape), contentAlignment = Alignment.Center) {
                Symbol(if (feedback.isBlank()) Glyph.HEART else Glyph.CHECK, Ink.Primary, 35.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(if (feedback.isBlank()) "이번 추천은 어떠셨나요?" else "의견을 남겨주셨네요", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(11.dp))
            Text(if (feedback.isBlank()) "‘${place.title}’ 제안에 대한 느낌을 알려 주세요." else "이번 화면에만 기록했어요. 다음에도 가볍게 떠나 보세요.", color = Ink.Muted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("마음에 들어요", "다른 곳이 좋아요").forEach { value ->
                    ChoiceChip(value, feedback == value, glyph = if (value == "마음에 들어요") Glyph.HEART else Glyph.RESET, onClick = { onFeedback(value) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("선택 사항이에요 · 외부로 전송하지 않아요", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
            PrimaryAction(if (feedback.isBlank()) "건너뛰고 처음으로" else "처음으로 돌아가기", onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DriverPanel(
    scene: SceneSuggestion?, canShowAnother: Boolean, maxStringLength: Int,
    status: String, onNext: () -> Unit, onNavigate: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        val short = maxHeight < 560.dp
        val stackedButtons = maxWidth < 600.dp && !short
        val buttonTextSize = if (maxWidth < 460.dp) 18.sp else 22.sp
        fun limited(text: String) = text.take(maxStringLength.coerceAtLeast(0))
        Column(
            Modifier.widthIn(max = 920.dp).fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(if (short) 10.dp else 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Symbol(Glyph.MARK, Ink.Primary, if (short) 24.dp else 30.dp)
                Text(limited("지금 가볼 만한 곳"), color = Ink.Primary, fontSize = if (short) 23.sp else 28.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(limited(scene?.place?.title ?: "조건에 맞는 곳이 없어요"), color = Ink.White,
                    fontSize = if (short) 29.sp else 38.sp, lineHeight = if (short) 36.sp else 48.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(limited(scene?.activity ?: "정차 후 조건을 바꿔 보세요."), color = Ink.Muted,
                    fontSize = if (short) 18.sp else 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (scene != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(limited(scene.place.outboundMinutes?.let { "가는 길 약 ${it}분" } ?: "이동 시간 미확인"),
                        color = Ink.White, fontSize = if (short) 20.sp else 25.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(limited("주차 빈자리 미확인"), color = Ink.Muted, fontSize = if (short) 17.sp else 20.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            Text(limited("서울 지역 샘플 · 실시간 정보 아님"), color = Ink.Apricot, fontSize = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val navigateLabel = limited(scene?.place?.let { "${it.title} 안내" } ?: "안내할 곳 없음")
            if (stackedButtons) {
                OutlinedButton(onClick = onNext, enabled = canShowAnother,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, Ink.Line)) {
                    Text(limited("다른 곳 보기"), fontSize = buttonTextSize)
                }
                Button(onClick = onNavigate, enabled = scene != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(17.dp)) {
                    Text(navigateLabel, fontSize = buttonTextSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(onClick = onNext, enabled = canShowAnother,
                        modifier = Modifier.weight(.4f).heightIn(min = 64.dp), shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, Ink.Line)) {
                        Text(limited("다른 곳 보기"), fontSize = buttonTextSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = onNavigate, enabled = scene != null,
                        modifier = Modifier.weight(.6f).heightIn(min = 64.dp), shape = RoundedCornerShape(17.dp)) {
                        Text(navigateLabel, fontSize = buttonTextSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (status.isNotBlank()) {
                Text(limited(status), color = Ink.Muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RestrictedPanel(message: String) {
    Surface(color = Ink.Background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 590.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Symbol(Glyph.MARK, Ink.Primary, 50.dp)
                Spacer(Modifier.height(24.dp))
                Text("지금은 상세 조작을 쉬어갈게요", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Text(message, color = Ink.Muted, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun EmptyPanel(title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp), horizontalAlignment = Alignment.Start) {
            Box(Modifier.size(70.dp).background(Ink.Deep, CircleShape), contentAlignment = Alignment.Center) { Symbol(Glyph.BREEZE, Ink.Primary, 34.dp) }
            Spacer(Modifier.height(22.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(body, color = Ink.Muted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(25.dp))
            PrimaryAction(action, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionLabel(text: String, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink.White)
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, glyph: Glyph? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = if (selected) Ink.Primary else Ink.Panel,
        contentColor = if (selected) Ink.Deep else Ink.Muted,
        shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, if (selected) Ink.Primary else Ink.Line),
        modifier = Modifier.heightIn(min = 54.dp).semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (glyph != null) Symbol(glyph, if (selected) Ink.Deep else Ink.Muted, 22.dp)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Glyph = Glyph.ARROW) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 62.dp),
        shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink.Primary, contentColor = Ink.Deep),
    ) {
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Symbol(glyph, Ink.Deep, 25.dp)
    }
}

@Composable
private fun SampleNote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol(Glyph.INFO, Ink.Apricot, 18.dp)
        Text("샘플 장소 · 예상 정보 · 실시간 정보 아님", color = Ink.Apricot, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ImageLabel() {
    Surface(color = Ink.Background.copy(alpha = .75f), shape = RoundedCornerShape(8.dp)) {
        Text("AI 예시 이미지", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Ink.White.copy(alpha = .9f), fontSize = 11.sp)
    }
}

@Composable
private fun InfoStrip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Symbol(Glyph.INFO, color, 20.dp)
            Text(text, color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

private fun needLabel(need: Need) = when (need) {
    Need.BREEZE -> "바람 쐬기"; Need.CONVERSATION -> "둘이 이야기"; Need.SHELTER -> "비 피하기"
}
private fun needGlyph(need: Need) = when (need) {
    Need.BREEZE -> Glyph.BREEZE; Need.CONVERSATION -> Glyph.TALK; Need.SHELTER -> Glyph.RAIN
}
private fun moodGlyph(mood: MoodIntent) = when (mood) {
    MoodIntent.RESET -> Glyph.BREEZE; MoodIntent.TOGETHER -> Glyph.HEART
    MoodIntent.DISCOVERY -> Glyph.MARK; MoodIntent.REST -> Glyph.CLOCK
}
private fun imageResource(place: Place) = if (place.image == PlaceImage.CAFE) R.drawable.cafe_concept else R.drawable.riverside_concept
private fun requestSummary(request: RecommendationRequest): String {
    val need = request.needs.firstOrNull()?.let { needLabel(it) } ?: "마음은 열어 두고"
    val time = when (val time = request.time) {
        TimePreference.Unspecified -> "시간은 아직 미정"
        TimePreference.Unlimited -> "시간 제한 없이"
        is TimePreference.Bounded -> if (request.returnPlan == ReturnPlan.SAME_ORIGIN) "${time.minutes}분 안에 돌아오기" else "여유 ${time.minutes}분 · 복귀 기준 미정"
    }
    return "$need  ·  $time"
}
