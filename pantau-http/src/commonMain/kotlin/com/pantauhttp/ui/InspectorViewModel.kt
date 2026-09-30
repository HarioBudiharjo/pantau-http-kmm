package com.pantauhttp.ui

import com.pantauhttp.HttpTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

/** Search + status filtering over the store, debounced for the UI. */
internal class InspectorViewModel(
    source: StateFlow<List<HttpTransaction>>,
    scope: CoroutineScope,
) {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(StatusFilter.All)

    val items: StateFlow<List<HttpTransaction>> = combine(
        source.debounce(100.milliseconds),
        query,
        filter,
    ) { list, q, f -> filterItems(list, q, f) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), filterItems(source.value, query.value, filter.value))

    val total: Flow<Int> = source.debounce(100.milliseconds).let { flow -> combine(flow, flow) { a, _ -> a.size } }

    companion object {
        fun filterItems(list: List<HttpTransaction>, query: String, filter: StatusFilter): List<HttpTransaction> =
            list.filter { filter.matches(it) && it.matches(query) }
    }
}
