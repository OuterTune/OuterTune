package com.dd3boh.outertune.ui.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.constants.MenuCornerRadius

@Composable
fun IconLabelButton(
    text: String,
    icon: ImageVector,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier.Companion,
    onClick: () -> Unit
) = Row(
    verticalAlignment = Alignment.Companion.CenterVertically,
    modifier = modifier
        .background(background, RoundedCornerShape(MenuCornerRadius))
        .padding(horizontal = 8.dp)
        .clickable { onClick() }
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.Companion.padding(4.dp)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.Companion.padding(horizontal = 4.dp)
    )
}

@Composable
fun IconLabelButton(
    text: String,
    painter: Painter,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier.Companion,
    onClick: () -> Unit
) = Row(
    verticalAlignment = Alignment.Companion.CenterVertically,
    modifier = modifier
        .background(background, androidx.compose.foundation.shape.RoundedCornerShape(MenuCornerRadius))
        .padding(horizontal = 8.dp)
        .clickable { onClick() }
) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.Companion.padding(4.dp)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.Companion.padding(horizontal = 4.dp)
    )
}