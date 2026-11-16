package com.tabletlauncher.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tabletlauncher.LauncherViewModel
import com.tabletlauncher.data.AppEntry
import com.tabletlauncher.data.Folder
import com.tabletlauncher.data.IconShape
import com.tabletlauncher.data.LauncherSettings

fun IconShape.toShape(): Shape = when (this) {
    IconShape.ORIGINAL -> RectangleShape
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> RoundedCornerShape(30)
    IconShape.SQUARE -> RoundedCornerShape(12)
}

fun Drawable.toBitmapScaled(maxSide: Int): Bitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxSide
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxSide
    val scale = maxSide.toFloat() / maxOf(w, h)
    return toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
}

@Composable
fun IconImage(
    bitmap: ImageBitmap?,
    settings: LauncherSettings,
    description: String,
    size: Dp = settings.iconSizeDp.dp,
) {
    val shape = settings.iconShape.toShape()
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = description,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(Modifier.size(size).clip(shape).background(Color.DarkGray))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    vm: LauncherViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = vm.settings.showLabels,
) {
    val s = vm.settings
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconImage(vm.icons[app.key], s, app.label)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = (s.iconSizeDp + 24).dp),
            )
        }
    }
}

@Composable
fun FolderTile(
    folder: Folder,
    vm: LauncherViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = vm.settings
    val size = s.iconSizeDp.dp
    val shape = if (s.iconShape == IconShape.ORIGINAL) RoundedCornerShape(24) else s.iconShape.toShape()
    val minis = folder.appKeys.mapNotNull { vm.icons[it] }.take(4)
    val mini = (size - 14.dp) / 2

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(size).clip(shape).background(Color(0x40FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (r in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in 0..1) {
                            val bmp = minis.getOrNull(r * 2 + c)
                            Box(Modifier.size(mini).clip(RoundedCornerShape(6.dp))) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (s.showLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = folder.name,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = (s.iconSizeDp + 24).dp),
            )
        }
    }
}
