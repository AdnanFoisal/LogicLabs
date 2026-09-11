package com.logiclabs.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class LogicIconsTest {

    @Test
    fun `Gear icon is initialized with correct viewport and dimensions`() {
        val gear = LogicIcons.Gear
        assertNotNull(gear)
        assertEquals("Gear", gear.name)
        assertEquals(24.dp, gear.defaultWidth)
        assertEquals(24.dp, gear.defaultHeight)
        assertEquals(24f, gear.viewportWidth)
        assertEquals(24f, gear.viewportHeight)
    }

    @Test
    fun `Settings icon aliases Gear icon exactly`() {
        val settings = LogicIcons.Settings
        val gear = LogicIcons.Gear
        assertNotNull(settings)
        assertSame(gear, settings)
    }

    @Test
    fun `Gear icon has centered bore and teeth path`() {
        val gear = LogicIcons.Gear
        val vectorPath = gear.root.first() as androidx.compose.ui.graphics.vector.VectorPath
        val nodes = vectorPath.pathData
        // Centre bore should start at (12, 9) and form concentric semicircles
        assertEquals(androidx.compose.ui.graphics.vector.PathNode.MoveTo(12f, 9f), nodes[0])
        assertEquals(androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo(3f, 3f, 0f, false, true, 0f, 6f), nodes[1])
        assertEquals(androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo(3f, 3f, 0f, false, true, 0f, -6f), nodes[2])
        assertEquals(androidx.compose.ui.graphics.vector.PathNode.Close, nodes[3])
        // Outer teeth contour starts at (19.4, 15) and ends with Close
        assertEquals(androidx.compose.ui.graphics.vector.PathNode.MoveTo(19.4f, 15f), nodes[4])
        assertEquals(androidx.compose.ui.graphics.vector.PathNode.Close, nodes.last())
    }

    @Test
    fun `All primary icons are loadable and valid`() {
        assertNotNull(LogicIcons.Chip)
        assertNotNull(LogicIcons.Scope)
        assertNotNull(LogicIcons.Labs)
        assertNotNull(LogicIcons.Wire)
        assertNotNull(LogicIcons.Roam)
        assertNotNull(LogicIcons.Trash)
        assertNotNull(LogicIcons.Verify)
        assertNotNull(LogicIcons.Close)
        assertNotNull(LogicIcons.Gear)
        assertNotNull(LogicIcons.Settings)
        assertNotNull(LogicIcons.Back)
    }
}
