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

class PersonsCapacityTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val data = TileSyncHelper.loadActiveStationData(this)
        val stationName = if (data.isZgoraj) "ZGORAJ" else "SPODAJ"
        val badgeColor = if (data.isZgoraj) 0xFF38BDF8.toInt() else 0xFF10B981.toInt()

        val curStr = if (!data.isOnline && data.currentPersons == 0) "--" else String.format(Locale.US, "%02d", data.currentPersons)
        val maxStr = if (data.maxPersons > 0) String.format(Locale.US, "/%02d", data.maxPersons) else "/28"

        val countColor = when {
            !data.isOnline -> 0xFF64748B.toInt()
            data.maxPersons > 0 && data.currentPersons >= data.maxPersons -> 0xFFEF4444.toInt()
            data.maxPersons > 0 && data.currentPersons.toDouble() / data.maxPersons >= 0.85 -> 0xFFF59E0B.toInt()
            else -> 0xFFF8FAFC.toInt()
        }

        val appLaunchClick = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("si.vzpenjaca.kontrola.wear.WearMainActivity")
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

        val numberRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)

        val curText = LayoutElementBuilders.Text.Builder()
            .setText(curStr)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(34f))
                    .setColor(ColorBuilders.argb(countColor))
                    .build()
            )
            .build()

        val maxText = LayoutElementBuilders.Text.Builder()
            .setText(maxStr)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(24f))
                    .setColor(ColorBuilders.argb(0xFF94A3B8.toInt()))
                    .build()
            )
            .build()

        numberRow.addContent(curText)
        numberRow.addContent(maxText)

        val subText = LayoutElementBuilders.Text.Builder()
            .setText("Število oseb v kabini")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(11f))
                    .setColor(ColorBuilders.argb(0xFF94A3B8.toInt()))
                    .build()
            )
            .build()

        val btnRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        val btnMinus = createPillButton("− 1", 0xFFEF4444.toInt())
        val btnPlus = createPillButton("+ 1", 0xFF10B981.toInt())

        btnRow.addContent(btnMinus)
        btnRow.addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setWidth(DimensionBuilders.dp(8f))
                .build()
        )
        btnRow.addContent(btnPlus)

        column.addContent(stationBadge)
        column.addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(4f))
                .build()
        )
        column.addContent(numberRow.build())
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

    private fun createPillButton(label: String, accentColor: Int): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF1C2638.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(8f)).build())
                            .build()
                    )
                    .setBorder(
                        ModifiersBuilders.Border.Builder()
                            .setWidth(DimensionBuilders.dp(1f))
                            .setColor(ColorBuilders.argb(accentColor))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(14f))
                            .setEnd(DimensionBuilders.dp(14f))
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
                            .setSize(DimensionBuilders.sp(13f))
                            .setColor(ColorBuilders.argb(accentColor))
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
                getUpdater(context).requestUpdate(PersonsCapacityTileService::class.java)
            } catch (_: Throwable) {
            }
        }
    }
}
