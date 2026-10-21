package si.vzpenjaca.kontrola.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import kotlin.math.max

class TimeCountdownTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val data = TileSyncHelper.loadActiveStationData(this)
        val stationName = if (data.isZgoraj) "ZGORAJ" else "SPODAJ"
        val badgeColor = if (data.isZgoraj) 0xFF38BDF8.toInt() else 0xFF10B981.toInt()

        val timeStr = if (!data.isOnline && data.remainingMs <= 0L) {
            "--:--"
        } else {
            val totalSeconds = (max(0L, data.remainingMs) + 499L) / 1000L
            val mins = totalSeconds / 60L
            val secs = totalSeconds % 60L
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }

        val intervalMin = max(1, ((data.setTimeMs + 30_000L) / 60_000L).toInt())
        val intervalStr = if (data.setTimeMs > 0L) "/$intervalMin" else "/--"

        val textColor = when {
            !data.isOnline -> 0xFF64748B.toInt()
            data.remainingMs <= 0L || timeStr == "00:00" -> 0xFFEF4444.toInt()
            data.remainingMs <= 60_000L -> 0xFFF59E0B.toInt()
            else -> 0xFFF8FAFC.toInt()
        }

        val appLaunchClick = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("si.vzpenjaca.kontrola.wear.WearMainActivity")
                            .addKeyToExtraMapping("target_page", ActionBuilders.intExtra(0))
                            .build()
                    )
                    .build()
            )
            .build()

        val rootBox = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF0B0F17.toInt()))
                            .build()
                    )
                    .setClickable(appLaunchClick)
                    .build()
            )

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        val stationBadge = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0x2238BDF8.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(8f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(10f))
                            .setEnd(DimensionBuilders.dp(10f))
                            .setTop(DimensionBuilders.dp(3f))
                            .setBottom(DimensionBuilders.dp(3f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(stationName)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setColor(ColorBuilders.argb(badgeColor))
                            .build()
                    )
                    .build()
            )
            .build()

        val timeText = LayoutElementBuilders.Text.Builder()
            .setText(timeStr)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(32f))
                    .setColor(ColorBuilders.argb(textColor))
                    .build()
            )
            .build()

        val subText = LayoutElementBuilders.Text.Builder()
            .setText("Interval: $intervalStr min")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(11f))
                    .setColor(ColorBuilders.argb(0xFF94A3B8.toInt()))
                    .build()
            )
            .build()

        val btnRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        val btn6 = createPillButton("6 min", intervalMin == 6)
        val btn10 = createPillButton("10 min", intervalMin == 10)

        btnRow.addContent(btn6)
        btnRow.addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setWidth(DimensionBuilders.dp(6f))
                .build()
        )
        btnRow.addContent(btn10)

        column.addContent(stationBadge)
        column.addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(4f))
                .build()
        )
        column.addContent(timeText)
        column.addContent(subText)
        column.addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(6f))
                .build()
        )
        column.addContent(btnRow.build())

        rootBox.addContent(column.build())

        val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox.build()).build())
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(timelineEntry)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline)
            .build()

        return Futures.immediateFuture(tile)
    }

    private fun createPillButton(label: String, isActive: Boolean): LayoutElementBuilders.LayoutElement {
        val bgCol = if (isActive) 0xFF162C46.toInt() else 0xFF1C2638.toInt()
        val textCol = if (isActive) 0xFF38BDF8.toInt() else 0xFFF8FAFC.toInt()
        val borderCol = if (isActive) 0xFF38BDF8.toInt() else 0xFF2E3D56.toInt()

        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(bgCol))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(8f)).build())
                            .build()
                    )
                    .setBorder(
                        ModifiersBuilders.Border.Builder()
                            .setWidth(DimensionBuilders.dp(1f))
                            .setColor(ColorBuilders.argb(borderCol))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(10f))
                            .setEnd(DimensionBuilders.dp(10f))
                            .setTop(DimensionBuilders.dp(4f))
                            .setBottom(DimensionBuilders.dp(4f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setColor(ColorBuilders.argb(textCol))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return Futures.immediateFuture(resources)
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                getUpdater(context).requestUpdate(TimeCountdownTileService::class.java)
            } catch (_: Throwable) {
            }
        }
    }
}
