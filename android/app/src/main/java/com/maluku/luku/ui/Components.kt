package com.maluku.luku.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maluku.luku.data.MetaPreview

/** Draait de app op een tv (Chromecast met Google TV)? */
@Composable
fun isTv(): Boolean {
    val ctx = LocalContext.current
    return remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
}

/** Klikbaar met de afstandsbediening: gele rand als hij geselecteerd is (goed zichtbaar vanaf de bank). */
@Composable
fun Modifier.tv(shape: RoundedCornerShape = RoundedCornerShape(10.dp), onClick: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .scale(if (focused) 1.05f else 1f)
        .border(if (focused) 3.dp else 0.dp, if (focused) Z.focus else Color.Transparent, shape)
        .clip(shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
fun LukuButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .tv(RoundedCornerShape(10.dp)) { if (enabled) onClick() }
            .background(if (primary) Z.accent.copy(alpha = if (enabled) 1f else 0.4f) else Z.panel2, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun Poster(item: MetaPreview, width: Int = 130, onClick: () -> Unit) {
    val libItems by com.maluku.luku.App.instance.library.items.collectAsState()
    val inLib = libItems.any { it.id == item.id && it.inLibrary }
    Column(Modifier.width(width.dp)) {
        Box(
            Modifier
                .tv(RoundedCornerShape(10.dp), onClick)
                .width(width.dp)
                .height((width * 1.5f).dp)
                .background(Z.panel2, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(item.name, color = Z.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
            }
            if (inLib) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).background(Z.accent, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
        Text(
            item.name,
            color = Z.text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (item.year != null) Text(item.year, color = Z.dim, fontSize = 11.sp)
    }
}

@Composable
fun Loading(text: String = "Laden…") {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Z.accent)
        Text(text, color = Z.dim, modifier = Modifier.padding(top = 14.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorBox(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Er ging iets mis", color = Z.bad, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text, color = Z.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        if (onRetry != null) LukuButton("Opnieuw proberen", onClick = onRetry)
    }
}

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(Z.accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
        Text(
            "  Teyfey Luku",
            color = Z.text,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .tv(RoundedCornerShape(20.dp), onClick)
            .background(if (selected) Z.accent else Z.panel2, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(text, color = Color.White, fontSize = 14.sp) }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Z.text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
    )
}
