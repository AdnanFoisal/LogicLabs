package com.logiclabs.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.logiclabs.core.data.progress.BadgeCatalog
import com.logiclabs.core.data.progress.BenchStats
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * The achievements drawer: every badge in the catalog with its earned state.
 *
 * Deliberately quiet — this is a record, not a game screen. Earned badges carry the
 * amber rosette and a phosphor check; unearned ones sit dimmed with their description,
 * so the sheet doubles as "what's left to do" without a single exclamation mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesSheet(
    stats: BenchStats,
    classicLabCount: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val badges = BadgeCatalog.derive(stats, classicLabCount)
    val earnedCount = badges.count { it.second }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        SheetScaffold(
            title = "BENCH ACHIEVEMENTS",
            subtitle = "$earnedCount of ${badges.size} earned · verified labs, builds and bench time",
            onDismiss = onDismiss
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                items(badges, key = { it.first.id }) { (badge, earned) ->
                    BadgeRow(title = badge.title, description = badge.description, earned = earned)
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(title: String, description: String, earned: Boolean) {
    val accent = if (earned) AmberCore else TextTertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(if (earned) SurfaceCard else ChassisBase)
            .border(
                width = Dimens.Hairline,
                color = if (earned) AmberCore.copy(alpha = 0.45f) else TextTertiary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(Dimens.RadiusLg)
            )
            .padding(Dimens.Space3)
            .semantics {
                contentDescription = if (earned) "Achievement earned: $title" else "Locked achievement: $title"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = LogicIcons.Rosette,
            contentDescription = null,
            tint = if (earned) accent else TextTertiary.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(Dimens.Space3))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LogicLabsType.TitleSm,
                color = if (earned) TextPrimary else TextTertiary,
                maxLines = 1
            )
            Text(
                text = description,
                style = LogicLabsType.BodySm,
                color = TextSecondary,
                maxLines = 2
            )
        }
        if (earned) {
            Spacer(Modifier.width(Dimens.Space2))
            Icon(
                imageVector = LogicIcons.Verify,
                contentDescription = null,
                tint = PhosphorCore,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
