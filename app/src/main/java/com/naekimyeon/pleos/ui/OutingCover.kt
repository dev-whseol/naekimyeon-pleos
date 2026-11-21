package com.naekimyeon.pleos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naekimyeon.pleos.R
import com.naekimyeon.pleos.domain.MoodIntent

/** An editorial example on the parked home screen, visible before location is available. */
@Composable
internal fun OutingCover(modifier: Modifier, timeCode: String, mood: MoodIntent?) {
    val title = when {
        timeCode == "unlimited" -> "호숫가를 걷다가\n가족마당까지"
        (timeCode.toIntOrNull() ?: Int.MAX_VALUE) <= 45 -> "호숫가 벤치에서\n잠깐 쉬기"
        mood == MoodIntent.TOGETHER -> "서로 찍어 준 사진,\n한 장씩 골라 보기"
        mood == MoodIntent.DISCOVERY -> "물에 비친 나무를\n사진에 담기"
        mood == MoodIntent.REST -> "물가에 앉아\n쉬었다 가기"
        else -> "서울숲 호숫가\n한 바퀴"
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        val short = maxHeight < 460.dp
        Image(painterResource(R.drawable.seoul_forest_lake), "서울숲 호숫가 풍경", Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .32f to Color.Transparent, .8f to Color(0xCF11271E), 1f to Color(0xFA10241C),
        )))
        Column(Modifier.align(Alignment.BottomStart).padding(if (short) 20.dp else 32.dp)) {
            Text("나들이 예시  ·  서울숲", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = if (short) 27.sp else 40.sp, lineHeight = if (short) 33.sp else 48.sp,
                color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("사진 Enigma7seven  ·  CC BY-SA 3.0", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}
