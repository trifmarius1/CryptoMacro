package com.cryptomacro.app.ui.learn

/** BEGINNER: Loads Fear & Greed (via market.overview), mempool fees, and halving once. Tab + selected lesson are just StateFlows. */
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptomacro.app.data.repository.MarketRepository
import com.cryptomacro.app.domain.model.EducationCatalog
import com.cryptomacro.app.domain.model.FeeEstimates
import com.cryptomacro.app.domain.model.HalvingInfo
import com.cryptomacro.app.domain.model.ShemitahData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LearnTab(val label: String) {
    SENTIMENT("Sentiment"),
    CYCLES("Cycles"),
    LESSONS("Lessons"),
}

@HiltViewModel
class LearnViewModel @Inject constructor(
    private val market: MarketRepository,
) : ViewModel() {
    val tab = MutableStateFlow(LearnTab.SENTIMENT)
    val selectedModule = MutableStateFlow(EducationCatalog.modules.first().id)
    val shemitah = ShemitahData.stats()
    val events = ShemitahData.events
    val modules = EducationCatalog.modules
    val halving = MutableStateFlow<HalvingInfo?>(null)
    val fees = MutableStateFlow<FeeEstimates?>(null)
    val overview = market.overview.stateIn(viewModelScope, SharingStarted.Eagerly, com.cryptomacro.app.domain.model.MarketOverview())

    init {
        viewModelScope.launch { halving.value = runCatching { market.halving() }.getOrNull() }
        viewModelScope.launch { fees.value = runCatching { market.fees() }.getOrNull() }
    }

    fun setTab(value: LearnTab) { tab.value = value }
    fun open(id: String) { selectedModule.value = id }
}
