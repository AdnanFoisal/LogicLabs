package com.logiclabs.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary

/**
 * Reusable industrial instant search bar component for lab catalogs, IC catalogs, coursework,
 * and verifier sheets.
 *
 * Authored in Logic Labs industrial hardware chassis styling: dark card surface, subtle hairline border
 * that illuminates with [AccentCyan] when focused, leading [LogicIcons.Search] vector icon, and
 * interactive trailing [LogicIcons.Close] clear icon when a query is non-empty.
 */
@Composable
fun InstantSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "SEARCH...",
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(Dimens.RadiusMd)
    val borderColor = if (isFocused) AccentCyan else SurfaceCardBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(Dimens.Hairline, borderColor), shape)
            .padding(horizontal = Dimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = LogicIcons.Search,
            contentDescription = "Search",
            tint = if (isFocused) AccentCyan else TextSecondary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(Dimens.Space2))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = LogicLabsType.SwitchPlate,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                textStyle = LogicLabsType.BodySm.copy(color = TextPrimary),
                cursorBrush = SolidColor(AccentCyan),
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions.Default,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (query.isNotEmpty()) {
            Spacer(Modifier.width(Dimens.Space1))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .clickable {
                        onQueryChange("")
                        onClear?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LogicIcons.Close,
                    contentDescription = "Clear search",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
