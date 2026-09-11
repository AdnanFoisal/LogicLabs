package com.logiclabs.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.feature.tools.feedback.Haptics

enum class HomeTab(
    val title: String,
    val icon: ImageVector
) {
    PROJECTS("Projects", LogicIcons.Breadboard),
    COURSEWORK("Coursework", LogicIcons.Labs),
    REFERENCE("74xx ICs", LogicIcons.Chip),
    SETTINGS("Settings", LogicIcons.Gear)
}

/**
 * Modern 4-Tab Bottom Navigation Bar inspired by professional EDA simulation apps.
 */
@Composable
fun HomeBottomNavBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Theme token, not a literal: the hardcoded 0xFF14161F stayed near-black on
            // the daylight CLEANROOM theme while the rest of the home rack went light.
            .background(ChassisBase)
            // navigationBars ∪ displayCutout: the gesture bar at the bottom, and a
            // landscape side cutout — neither may sit over the tab targets.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
    ) {
        // Subtle top divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SurfaceCardBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = Dimens.Space2),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                val contentColor = if (isSelected) AmberCore else TextSecondary

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            // Indication-free like the rest of the app's chrome.
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!isSelected) {
                                    Haptics.tick(view)
                                    onTabSelected(tab)
                                }
                            }
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        // Visible Text below carries the label; a second description
                        // made TalkBack announce each tab twice.
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                    Text(
                        text = tab.title,
                        style = LogicLabsType.SwitchPlate,
                        color = contentColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
