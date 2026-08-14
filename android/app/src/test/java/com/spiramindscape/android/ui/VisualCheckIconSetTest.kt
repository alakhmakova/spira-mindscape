package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Every** icon in the set, drawn, on 3 sheets in `app/build/reports/visual/`.
 *
 * This is the only check that can catch a bad port. A mis-transcribed path draws *nothing* while
 * `assertExists` stays green, and a wrong viewport draws something microscopic or clipped — neither
 * fails a test. So when a glyph is added or changed, run this and **look at the PNG**.
 *
 * Each row shows the mark at 34dp and again at 18dp, because a glyph that survives being large can
 * still turn to mush at the size a footer or a badge actually uses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckIconSetTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w460dp-h1400dp")
    fun `sheet 1 draws`() {
        renderSheet(
            "icon-set-1",
            listOf(
            "Plus" to SpiraIcons.Plus,
            "X" to SpiraIcons.X,
            "Trash" to SpiraIcons.Trash,
            "ChevronDown" to SpiraIcons.ChevronDown,
            "ChevronUp" to SpiraIcons.ChevronUp,
            "ChevronRight" to SpiraIcons.ChevronRight,
            "Menu" to SpiraIcons.Menu,
            "Check" to SpiraIcons.Check,
            "User" to SpiraIcons.User,
            "PersonFilled" to SpiraIcons.PersonFilled,
            "LogOut" to SpiraIcons.LogOut,
            "Home" to SpiraIcons.Home,
            "ArrowLeft" to SpiraIcons.ArrowLeft,
            "ArrowUp" to SpiraIcons.ArrowUp,
            "ArrowDown" to SpiraIcons.ArrowDown,
            "ArrowUpRight" to SpiraIcons.ArrowUpRight,
            "ArrowUpDown" to SpiraIcons.ArrowUpDown,
            "Eye" to SpiraIcons.Eye,
            "Folder" to SpiraIcons.Folder,
            "Target" to SpiraIcons.Target,
            "Search" to SpiraIcons.Search,
            "Filter" to SpiraIcons.Filter,
            "Ellipsis" to SpiraIcons.Ellipsis,
            "EllipsisVertical" to SpiraIcons.EllipsisVertical,
            "Star" to SpiraIcons.Star,
            "Idea" to SpiraIcons.Idea,
            "FileText" to SpiraIcons.FileText,
            "File" to SpiraIcons.File,
            "Mail" to SpiraIcons.Mail,
            "ExternalLink" to SpiraIcons.ExternalLink,
            "Download" to SpiraIcons.Download,
            "Smartphone" to SpiraIcons.Smartphone,
            ),
        )
    }

    @Test
    @Config(qualifiers = "w460dp-h1400dp")
    fun `sheet 2 draws`() {
        renderSheet(
            "icon-set-2",
            listOf(
            "Bold" to SpiraIcons.Bold,
            "Italic" to SpiraIcons.Italic,
            "Underline" to SpiraIcons.Underline,
            "Strikethrough" to SpiraIcons.Strikethrough,
            "PenLine" to SpiraIcons.PenLine,
            "Quote" to SpiraIcons.Quote,
            "Code" to SpiraIcons.Code,
            "List" to SpiraIcons.List,
            "ListOrdered" to SpiraIcons.ListOrdered,
            "ListChecks" to SpiraIcons.ListChecks,
            "Expand" to SpiraIcons.Expand,
            "Copy" to SpiraIcons.Copy,
            "CopyCheck" to SpiraIcons.CopyCheck,
            "Undo" to SpiraIcons.Undo,
            "Redo" to SpiraIcons.Redo,
            "Minus" to SpiraIcons.Minus,
            "Link" to SpiraIcons.Link,
            "Unlink" to SpiraIcons.Unlink,
            "Lock" to SpiraIcons.Lock,
            "LockOpen" to SpiraIcons.LockOpen,
            "Eraser" to SpiraIcons.Eraser,
            "Calendar" to SpiraIcons.Calendar,
            "CirclePlus" to SpiraIcons.CirclePlus,
            "CircleCheck" to SpiraIcons.CircleCheck,
            "Camera" to SpiraIcons.Camera,
            "Paperclip" to SpiraIcons.Paperclip,
            "Info" to SpiraIcons.Info,
            "TriangleAlert" to SpiraIcons.TriangleAlert,
            "CircleExclamationFilled" to SpiraIcons.CircleExclamationFilled,
            "CheckShape" to SpiraIcons.CheckShape,
            "Key" to SpiraIcons.Key,
            "Clock" to SpiraIcons.Clock,
            ),
        )
    }

    @Test
    @Config(qualifiers = "w460dp-h1400dp")
    fun `sheet 3 draws`() {
        renderSheet(
            "icon-set-3",
            listOf(
            "Pencil" to SpiraIcons.Pencil,
            "Sparkles" to SpiraIcons.Sparkles,
            "SwitchArrows" to SpiraIcons.SwitchArrows,
            "Shield" to SpiraIcons.Shield,
            "Thunderbolt" to SpiraIcons.Thunderbolt,
            "TrendingUp" to SpiraIcons.TrendingUp,
            "Smile" to SpiraIcons.Smile,
            "Frown" to SpiraIcons.Frown,
            "CaretDown" to SpiraIcons.CaretDown,
            "ChevronLeft" to SpiraIcons.ChevronLeft,
            "FolderOpen" to SpiraIcons.FolderOpen,
            "FolderOpenFilled" to SpiraIcons.FolderOpenFilled,
            "BookOpen" to SpiraIcons.BookOpen,
            "CircleQuestion" to SpiraIcons.CircleQuestion,
            "ChartColumn" to SpiraIcons.ChartColumn,
            "SparklesFilled" to SpiraIcons.SparklesFilled,
            "SortAscending" to SpiraIcons.SortAscending,
            "SortDescending" to SpiraIcons.SortDescending,
            ),
        )
    }

    private fun renderSheet(name: String, icons: List<Pair<String, ImageVector>>) {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    icons.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (label, icon) -> Cell(label, icon) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow(name)
    }

    @androidx.compose.runtime.Composable
    private fun Cell(label: String, icon: ImageVector) {
        Row(
            Modifier.width(210.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(34.dp),
            )
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}
