package com.prudvi.trackbudget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.AppThemePreference
import com.prudvi.trackbudget.model.DashboardSnapshot
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import com.prudvi.trackbudget.ui.receiptMoney
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The home-screen card: one hero plate — period, amount spent, what's left, a pace rail — over
 * three stat tiles. Nothing else, at any size.
 *
 * The theme is an in-app preference rather than a system uiMode, so nothing themed can be baked
 * into the layout XML. Every surface is an [android.widget.ImageView] holding an opaque rounded
 * shape re-tinted at update time with `setColorFilter`, which — unlike `setBackgroundTintList` —
 * is remotable on every API level down to our minSdk of 26. Hard borders come from stacking a
 * border plate under a 2dp-inset fill plate so the two colours tint independently.
 */
class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateWidget(context, manager, id)
    }

    /**
     * Replacing the APK resets every widget to the layout's design-time text, and nothing else
     * would repaint it until the next mutation or the half-hourly tick — so a freshly updated app
     * left a card of placeholder figures on the home screen looking like real money.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) updateAll(context)
    }

    companion object {
        /**
         * The widget's slice of a [com.prudvi.trackbudget.ui.ReceiptsColors] palette. Kept as plain
         * ARGB ints because RemoteViews cannot see Compose colours.
         */
        private data class WidgetPalette(
            val paper: Int,
            val ink: Int,
            val fade: Int,
            val hero: Int,
            val heroOn: Int,
            /** Rail fill while spending is at or under pace. */
            val pace: Int,
            /** Rail fill once spending runs ahead of the day-of-period pace. */
            val alert: Int,
            val tileA: Int,
            val tileB: Int,
            val tileC: Int,
        ) {
            /** Unspent rail segments: the hero ground, knocked toward its own text colour. */
            val railTrack: Int = blend(hero, heroOn, 0.24f)
        }

        private data class Tile(val label: String, val value: String, val ground: Int)

        private data class WidgetData(
            val kicker: String,
            val period: String,
            val hero: String,
            val sub: String,
            val heroDescription: String,
            /** Budget used, 0f..1f+. Null when no budget is set. */
            val progress: Float?,
            val overPace: Boolean,
            val railDescription: String,
            val tiles: List<Tile>,
            val palette: WidgetPalette,
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java)).forEach {
                updateWidget(context, manager, it)
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val data = loadData(context.applicationContext)
            val views = RemoteViews(context.packageName, R.layout.widget_budget)
            bind(context, views, id, data)
            manager.updateAppWidget(id, views)
        }

        // ---------------------------------------------------------------- binding

        private fun bind(context: Context, views: RemoteViews, id: Int, data: WidgetData) {
            val palette = data.palette
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, id))
            views.setContentDescription(R.id.widget_root, "Receipts. ${data.heroDescription}. Opens Receipts.")

            views.tint(R.id.widget_card_border, palette.ink)
            views.tint(R.id.widget_card_fill, palette.paper)
            views.tint(R.id.widget_hero_border, palette.ink)
            views.tint(R.id.widget_hero_fill, palette.hero)

            views.setTextViewText(R.id.widget_kicker, data.kicker)
            views.setTextColor(R.id.widget_kicker, palette.heroOn)
            views.setTextViewText(R.id.widget_period, data.period)
            views.setTextColor(R.id.widget_period, palette.heroOn)
            views.setTextViewText(R.id.widget_hero_amount, data.hero)
            views.setTextColor(R.id.widget_hero_amount, palette.heroOn)
            views.setContentDescription(R.id.widget_hero_amount, data.heroDescription)
            views.setTextViewText(R.id.widget_hero_sub, data.sub)
            views.setTextColor(R.id.widget_hero_sub, palette.heroOn)

            bindRail(views, data)
            bindTiles(views, data)
        }

        /** The pace rail is a run of individually tinted segments — no ProgressBar, so it themes on API 26. */
        private fun bindRail(views: RemoteViews, data: WidgetData) {
            val palette = data.palette
            views.setViewVisibility(R.id.widget_rail, if (data.progress == null) View.GONE else View.VISIBLE)
            val progress = data.progress ?: return
            val count = RailIds.size
            val filled = (progress * count).roundToInt().coerceIn(if (progress > 0f) 1 else 0, count)
            val fill = if (data.overPace) palette.alert else palette.pace
            RailIds.forEachIndexed { index, viewId ->
                views.tint(viewId, if (index < filled) fill else palette.railTrack)
            }
            views.setContentDescription(R.id.widget_rail, data.railDescription)
        }

        private fun bindTiles(views: RemoteViews, data: WidgetData) {
            val palette = data.palette
            TileIds.forEachIndexed { index, ids ->
                val tile = data.tiles[index]
                views.tint(ids.border, palette.ink)
                views.tint(ids.fill, tile.ground)
                views.setTextViewText(ids.label, tile.label)
                views.setTextColor(ids.label, palette.fade)
                views.setTextViewText(ids.value, tile.value)
                views.setTextColor(ids.value, palette.ink)
                views.setContentDescription(ids.frame, "${tile.label}, ${tile.value}")
            }
        }

        /**
         * Re-tints one of the opaque shape plates. `ImageView.setColorFilter(int)` is remotable on
         * every supported API, which is why the widget needs neither `setBackgroundTintList`
         * (API 31+) nor a drawable baked per theme.
         */
        private fun RemoteViews.tint(viewId: Int, color: Int) = setInt(viewId, "setColorFilter", color)

        private val RailIds = listOf(
            R.id.widget_rail_0, R.id.widget_rail_1, R.id.widget_rail_2, R.id.widget_rail_3,
            R.id.widget_rail_4, R.id.widget_rail_5, R.id.widget_rail_6, R.id.widget_rail_7,
            R.id.widget_rail_8, R.id.widget_rail_9, R.id.widget_rail_10, R.id.widget_rail_11,
        )

        private data class TileIdSet(val frame: Int, val border: Int, val fill: Int, val label: Int, val value: Int)

        private val TileIds = listOf(
            TileIdSet(R.id.widget_tile1, R.id.widget_tile1_border, R.id.widget_tile1_fill, R.id.widget_tile1_label, R.id.widget_tile1_value),
            TileIdSet(R.id.widget_tile2, R.id.widget_tile2_border, R.id.widget_tile2_fill, R.id.widget_tile2_label, R.id.widget_tile2_value),
            TileIdSet(R.id.widget_tile3, R.id.widget_tile3_border, R.id.widget_tile3_fill, R.id.widget_tile3_label, R.id.widget_tile3_value),
        )

        // ---------------------------------------------------------------- data

        private fun loadData(context: Context): WidgetData {
            val repository = TrackRepository(context)
            val transactions = repository.transactions.value
            val activeBudget = if (repository.currentPeriodBudgetConfirmed) repository.budget else repository.budget.copy(amountMinor = 0L)
            val snapshot = dashboard(transactions, activeBudget)
            val palette = widgetPalette(repository.receipts.preferencesFlow.value.theme)

            // Obligations are money that was never available, so the ceiling for the rail and for
            // "left of" is the spendable figure, not the raw budget amount.
            val ceiling = snapshot.spendableMinor
            val hasBudget = ceiling > 0L
            val progress = if (hasBudget) (snapshot.spentMinor.toFloat() / ceiling).coerceAtLeast(0f) else null
            val dayFraction = snapshot.dayOfPeriod.toFloat() / snapshot.daysInPeriod.coerceAtLeast(1)
            val daysLeft = (snapshot.daysInPeriod - snapshot.dayOfPeriod + 1).coerceAtLeast(0)
            val over = snapshot.remainingMinor < 0L

            val sub = when {
                !hasBudget -> "No budget set · tap to add one"
                over -> "${money(snapshot.remainingMinor)} over ${money(ceiling)}"
                snapshot.obligationsMinor > 0L -> "${money(snapshot.remainingMinor)} left of ${money(ceiling)} after ${money(snapshot.obligationsMinor)} fixed"
                else -> "${money(snapshot.remainingMinor)} left of ${money(ceiling)}"
            }

            return WidgetData(
                kicker = "SPENT THIS ${if (activeBudget.period.equals("Week", true)) "WEEK" else "MONTH"}",
                period = "DAY ${snapshot.dayOfPeriod}/${snapshot.daysInPeriod}",
                hero = signedMoney(snapshot.spentMinor),
                sub = sub,
                heroDescription = "${signedMoney(snapshot.spentMinor)} spent in ${periodLabel(snapshot.range)}. $sub",
                progress = progress,
                overPace = progress != null && progress > dayFraction,
                railDescription = if (progress == null) {
                    "No budget set"
                } else {
                    "${(progress * 100).roundToInt()} percent of budget used on day ${snapshot.dayOfPeriod} of ${snapshot.daysInPeriod}"
                },
                tiles = tilesFor(snapshot, hasBudget, over, daysLeft, palette),
                palette = palette,
            )
        }

        private fun tilesFor(
            snapshot: DashboardSnapshot,
            hasBudget: Boolean,
            over: Boolean,
            daysLeft: Int,
            palette: WidgetPalette,
        ): List<Tile> = listOf(
            Tile(
                label = if (over) "OVER BY" else "LEFT",
                value = if (hasBudget) money(snapshot.remainingMinor) else "—",
                ground = palette.tileA,
            ),
            Tile(
                label = "SAFE TODAY",
                value = if (hasBudget) money(snapshot.safeTodayMinor.coerceAtLeast(0L)) else "—",
                ground = palette.tileB,
            ),
            Tile(
                label = "DAYS LEFT",
                value = daysLeft.toString(),
                ground = palette.tileC,
            ),
        )

        private fun money(minor: Long): String = receiptMoney(abs(minor))

        private fun signedMoney(minor: Long): String = if (minor < 0L) "−${receiptMoney(abs(minor))}" else receiptMoney(minor)

        // ---------------------------------------------------------------- palettes

        /**
         * Mirrors the four palettes in `ui/Tokens.kt`. Keep these in step with that file: switching
         * the theme in Settings has to visibly change the widget.
         */
        private fun widgetPalette(theme: AppThemePreference): WidgetPalette = when (theme) {
            AppThemePreference.COLORFUL -> WidgetPalette(
                paper = 0xFFFFFDF6.toInt(), ink = 0xFF14121F.toInt(), fade = 0xFF6E6880.toInt(),
                hero = 0xFFFFD23F.toInt(), heroOn = 0xFF14121F.toInt(),
                pace = 0xFF14121F.toInt(), alert = 0xFFFF3D7F.toInt(),
                tileA = 0xFFE7F9EF.toInt(), tileB = 0xFFDFF7F9.toInt(), tileC = 0xFFFFF3DE.toInt(),
            )
            AppThemePreference.SUBTLE -> WidgetPalette(
                paper = 0xFFFAF8F3.toInt(), ink = 0xFF2E2B36.toInt(), fade = 0xFF8B8696.toInt(),
                hero = 0xFFE4C77E.toInt(), heroOn = 0xFF2E2B36.toInt(),
                pace = 0xFF2E2B36.toInt(), alert = 0xFFC98999.toInt(),
                tileA = 0xFFE9EFE9.toInt(), tileB = 0xFFE7EEEC.toInt(), tileC = 0xFFF1ECE1.toInt(),
            )
            // Strict mono: the rail's two states separate by value, not hue.
            AppThemePreference.LIGHT -> WidgetPalette(
                paper = 0xFFFFFFFF.toInt(), ink = 0xFF121212.toInt(), fade = 0xFF6E6E6E.toInt(),
                hero = 0xFF383838.toInt(), heroOn = 0xFFFFFFFF.toInt(),
                pace = 0xFFFFFFFF.toInt(), alert = 0xFFAFAFAF.toInt(),
                tileA = 0xFFF2F2F2.toInt(), tileB = 0xFFEDEDED.toInt(), tileC = 0xFFF1F1F1.toInt(),
            )
            AppThemePreference.DARK -> WidgetPalette(
                paper = 0xFF121212.toInt(), ink = 0xFFF2F2F2.toInt(), fade = 0xFF8F8F8F.toInt(),
                hero = 0xFFC7C7C7.toInt(), heroOn = 0xFF121212.toInt(),
                pace = 0xFF121212.toInt(), alert = 0xFF6B6B6B.toInt(),
                tileA = 0xFF1E1E1E.toInt(), tileB = 0xFF1A1A1A.toInt(), tileC = 0xFF1D1D1D.toInt(),
            )
        }

        private fun blend(base: Int, toward: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = (base shr shift) and 0xFF
                val b = (toward shr shift) and 0xFF
                return (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        // ---------------------------------------------------------------- intents

        private fun openAppIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
