@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cryptomacro.app.ui.navigation

/**
 * BEGINNER: This is the app's "router". Four destinations: Markets, Portfolio, Learn, Settings.
 *
 * NavigationSuiteScaffold draws a bottom bar on phones and a side rail on tablets — we do not
 * pick the bar ourselves.
 *
 * classifyLayout():
 *   Compact  = phone portrait (width < 600dp) → one column
 *   Medium   = fold / landscape phone → two panes
 *   Expanded = tablet AND tall enough → three panes
 * Height < 500dp keeps landscape phones in Medium so three columns do not crush the chart.
 *
 * Ctrl+1..4 switch tabs (Ctrl required so typing "1" in Amount still works).
 */

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.window.layout.FoldingFeature
import com.cryptomacro.app.MainActivity
import com.cryptomacro.app.ui.learn.LearnScreen
import com.cryptomacro.app.ui.learn.LearnViewModel
import com.cryptomacro.app.ui.markets.AssetDetailPane
import com.cryptomacro.app.ui.markets.ContextPane
import com.cryptomacro.app.ui.markets.ExpandedWorkbench
import com.cryptomacro.app.ui.markets.MarketsListPane
import com.cryptomacro.app.ui.markets.MarketsViewModel
import com.cryptomacro.app.ui.portfolio.PortfolioScreen
import com.cryptomacro.app.ui.portfolio.PortfolioViewModel
import com.cryptomacro.app.ui.settings.SettingsScreen
import com.cryptomacro.app.ui.settings.SettingsViewModel

enum class AppDest { Markets, Portfolio, Learn, Settings }

enum class LayoutKind { Compact, Medium, Expanded }

fun classifyLayout(widthDp: Int, heightDp: Int): LayoutKind = when {
    widthDp < 600 -> LayoutKind.Compact
    // Landscape phones often exceed 840dp width but have a short height — keep 2-pane.
    widthDp < 840 || heightDp < 500 -> LayoutKind.Medium
    else -> LayoutKind.Expanded
}

@Composable
fun AppRoot(
    pendingAction: String?,
    foldingFeature: FoldingFeature?,
    onConsumedAction: () -> Unit,
    onLock: () -> Unit,
) {
    val marketsVm: MarketsViewModel = hiltViewModel()
    val portfolioVm: PortfolioViewModel = hiltViewModel()
    val learnVm: LearnViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    var dest by remember { mutableStateOf(AppDest.Markets) }
    var compactDetail by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAction) {
        if (pendingAction == MainActivity.ACTION_ADD_TRANSACTION) {
            dest = AppDest.Portfolio
            portfolioVm.openTxSheet()
            onConsumedAction()
        }
    }

    val config = LocalConfiguration.current
    val layout = classifyLayout(config.screenWidthDp, config.screenHeightDp)
    val tabletop = foldingFeature?.state == FoldingFeature.State.HALF_OPENED &&
        foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL

    val items = listOf(
        AppDest.Markets to (Icons.AutoMirrored.Filled.ShowChart to "Markets"),
        AppDest.Portfolio to (Icons.Default.AccountBalanceWallet to "Portfolio"),
        AppDest.Learn to (Icons.AutoMirrored.Filled.MenuBook to "Learn"),
        AppDest.Settings to (Icons.Default.Settings to "Settings"),
    )

    NavigationSuiteScaffold(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            val meta = event.nativeKeyEvent.isCtrlPressed
            if (!meta) return@onPreviewKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_F -> { dest = AppDest.Markets; true }
                KeyEvent.KEYCODE_R -> { marketsVm.refresh(); true }
                KeyEvent.KEYCODE_1 -> { dest = AppDest.Markets; true }
                KeyEvent.KEYCODE_2 -> { dest = AppDest.Portfolio; true }
                KeyEvent.KEYCODE_3 -> { dest = AppDest.Learn; true }
                KeyEvent.KEYCODE_4 -> { dest = AppDest.Settings; true }
                else -> false
            }
        },
        navigationSuiteItems = {
            items.forEach { (d, pair) ->
                item(
                    selected = dest == d,
                    onClick = { dest = d; compactDetail = false },
                    icon = { Icon(pair.first, pair.second) },
                    label = { Text(pair.second) },
                )
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            when (dest) {
                AppDest.Markets -> MarketsAdaptive(
                    layout, tabletop, compactDetail, marketsVm, portfolioVm,
                    onOpenDetail = { compactDetail = true },
                    onBack = { compactDetail = false },
                )
                AppDest.Portfolio -> PortfolioScreen(portfolioVm)
                AppDest.Learn -> LearnScreen(learnVm, wide = layout != LayoutKind.Compact)
                AppDest.Settings -> SettingsScreen(settingsVm, onLock)
            }
        }
    }
}

@Composable
private fun MarketsAdaptive(
    layout: LayoutKind,
    tabletop: Boolean,
    compactDetail: Boolean,
    vm: MarketsViewModel,
    portfolio: PortfolioViewModel,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val heightDp = LocalConfiguration.current.screenHeightDp
    val chartHeight = when {
        heightDp < 480 -> 150.dp
        heightDp < 700 -> 200.dp
        else -> 240.dp
    }
    when {
        tabletop -> Column(Modifier.fillMaxSize()) {
            AssetDetailPane(vm, Modifier.weight(1f), chartHeight)
            Box(Modifier.weight(1f)) { PortfolioScreen(portfolio) }
        }
        layout == LayoutKind.Compact -> {
            if (compactDetail) {
                BackHandler(onBack = onBack)
                AssetDetailPane(vm, chartHeight = chartHeight)
            } else {
                MarketsListPane(vm, portfolio, onOpenDetail, compact = true, chartHeight = chartHeight)
            }
        }
        layout == LayoutKind.Medium -> Row(Modifier.fillMaxSize()) {
            MarketsListPane(
                vm, portfolio, { vm.select(it) }, compact = false,
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
            )
            AssetDetailPane(vm, Modifier.weight(0.58f).fillMaxHeight(), chartHeight)
        }
        else -> ExpandedWorkbench(vm, portfolio, chartHeight = chartHeight)
    }
}
