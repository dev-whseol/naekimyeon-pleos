@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.naekimyeon.pleos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naekimyeon.pleos.platform.LocationPrecision
import com.naekimyeon.pleos.platform.LocationState
import com.naekimyeon.pleos.platform.LocationStatus
import kotlin.math.ceil

@Composable
internal fun DiscoverySourcePanel(
    sampleMode: Boolean, state: LocationState, nearbyCount: Int,
    onChooseSource: (Boolean) -> Unit, onRequestLocation: () -> Unit,
    useLocationOnLaunch: Boolean = false, onUseLocationOnLaunchChanged: (Boolean) -> Unit = {},
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(false to "내 위치로 공원 보기", true to "샘플 둘러보기").forEach { (sample, label) ->
            Surface(onClick = { onChooseSource(sample) }, color = if (sampleMode == sample) Ink.Primary else Ink.Panel,
                contentColor = if (sampleMode == sample) Ink.Deep else Ink.Muted,
                shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, if (sampleMode == sample) Ink.Primary else Ink.Line),
                modifier = Modifier.heightIn(min = 54.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Symbol(if (sample) Glyph.INFO else Glyph.PIN, if (sampleMode == sample) Ink.Deep else Ink.Muted, 21.dp)
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (sampleMode) return
    Spacer(Modifier.height(14.dp))
    val busy = state.status == LocationStatus.LOCATING || state.status == LocationStatus.REQUESTING_PERMISSION
    val outside = state.isUsable && nearbyCount == 0
    Surface(color = Ink.Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("서울·과천 등록 공원 3곳 · 직선 50km 범위", color = Ink.Primary, style = MaterialTheme.typography.labelMedium)
            Text(when {
                outside -> "이 근처는 아직 준비 중이에요"
                state.isUsable -> "현재 위치 기준으로 찾았어요"
                state.status == LocationStatus.IDLE -> "지금 위치에서 찾아볼까요?"
                busy -> "위치를 확인하고 있어요"
                else -> "위치를 다시 확인해 주세요"
            }, color = Ink.White, style = MaterialTheme.typography.titleMedium)
            Text(when {
                outside -> "현재 위치에서 직선 50km 안에 등록된 서울·과천 공원이 없어요."
                state.isUsable -> buildString {
                    append(if (state.precision == LocationPrecision.APPROXIMATE) "대략적인 위치 기준" else "기기 위치 기준")
                    append(" · 직선 거리만 안내해요")
                    state.fix?.accuracyMeters?.let { accuracy ->
                        append(if (accuracy >= 1_000) " · 기기 위치 오차 약 ${ceil(accuracy / 1_000.0).toInt()}km"
                            else " · 기기 위치 오차 약 ${(ceil(accuracy / 100.0) * 100).toInt()}m")
                    }
                    if (state.fix?.isEmulator == true) append(" · 에뮬레이터 위치 · 실차 위치 아님")
                    else if (state.fix?.isMock == true) append(" · 테스트 위치")
                    append("\n직선 거리는 위치 오차로 달라질 수 있어요.")
                }
                state.status == LocationStatus.IDLE -> "현재 위치는 주변 장소를 찾을 때만 사용해요."
                else -> state.parkedLocationMessage()
            }, color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink.Primary, trackColor = Ink.Line)
            } else {
                TextButton(onClick = onRequestLocation, modifier = Modifier.heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(when (state.status) {
                        LocationStatus.PERMISSION_BLOCKED -> "설정에서 권한 켜기"
                        LocationStatus.PERMISSION_DENIED -> "권한 다시 요청하기"
                        LocationStatus.PROVIDERS_DISABLED -> "위치 설정 열기"
                        LocationStatus.IDLE -> "현재 위치 사용"
                        else -> "위치 다시 확인하기"
                    })
                }
            }
            HorizontalDivider(color = Ink.Line)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(
                    value = useLocationOnLaunch, enabled = !busy || useLocationOnLaunch,
                    role = Role.Switch, onValueChange = onUseLocationOnLaunchChanged,
                ).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("앱을 열 때 내 위치 사용", color = Ink.White, style = MaterialTheme.typography.bodyLarge)
                    Text("앱이 화면에 있을 때만 사용하며 위치 기록은 저장하지 않아요.", color = Ink.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = useLocationOnLaunch, onCheckedChange = null, enabled = !busy || useLocationOnLaunch)
            }
        }
    }
}

internal fun LocationState.parkedLocationMessage(): String = when (status) {
    LocationStatus.PERMISSION_DENIED -> "위치 권한 요청이 거절되어 현재 위치를 확인할 수 없어요."
    LocationStatus.PERMISSION_BLOCKED -> "위치 권한이 차단되어 있으니 설정에서 권한을 허용해 주세요."
    LocationStatus.PROVIDERS_DISABLED -> "기기의 위치 기능이 꺼져 있어 현재 위치를 확인할 수 없어요."
    LocationStatus.STALE -> "확인된 위치 정보가 오래되어 지금 위치를 다시 불러와야 해요."
    LocationStatus.LOW_ACCURACY -> "현재 위치 오차가 커서 거리를 계산하지 않았어요."
    LocationStatus.TIMED_OUT -> "제시간에 현재 위치를 불러오지 못했어요."
    else -> message
}

internal fun LocationState.drivingLocationMessage(): String = when (status) {
    LocationStatus.PERMISSION_DENIED, LocationStatus.REQUESTING_PERMISSION -> "위치 권한이 없어 안내할 수 없으니 정차 후 확인해 주세요."
    LocationStatus.IDLE, LocationStatus.CANCELLED, LocationStatus.SAFETY_RESTRICTED -> "정차 후 현재 위치를 확인해 주세요."
    LocationStatus.LOCATING -> "현재 위치를 확인하고 있어요."
    LocationStatus.PERMISSION_BLOCKED -> "위치 권한이 차단되어 있으니 정차 후 설정을 확인해 주세요."
    LocationStatus.PROVIDERS_DISABLED -> "기기 위치 기능이 꺼져 있으니 정차 후 위치를 켜 주세요."
    LocationStatus.STALE -> "위치 정보가 오래되었으니 정차 후 위치를 다시 확인해 주세요."
    LocationStatus.LOW_ACCURACY -> "위치 오차가 크니 정차 후 위치를 다시 확인해 주세요."
    else -> "위치를 불러오지 못했으니 정차 후 다시 확인해 주세요."
}

@Composable
internal fun NearbyHero(modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = Ink.Deep) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.Raised, Ink.Deep)))) {
            Box(Modifier.align(Alignment.Center).size(240.dp).border(1.dp, Ink.Primary.copy(alpha = .15f), CircleShape))
            Box(Modifier.align(Alignment.Center).size(150.dp).border(1.dp, Ink.Primary.copy(alpha = .26f), CircleShape))
            Symbol(Glyph.PIN, Ink.Primary, 64.dp, Modifier.align(Alignment.Center))
            Text("내 주변에서, 마음 가는 곳으로", Modifier.align(Alignment.TopStart).padding(26.dp), color = Ink.Primary,
                style = MaterialTheme.typography.labelLarge)
            Column(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
                Text("지금 있는 곳에서\n시작해 볼까요?", color = Ink.White, fontSize = 31.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                Text("준비된 실제 장소를 현재 위치와 연결해요.\n이동 시간과 이용 정보는 내비에서 확인해 주세요.", color = Ink.Muted,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
