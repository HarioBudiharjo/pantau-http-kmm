package com.pantauhttp.ui

import com.pantauhttp.HttpTransaction
import com.pantauhttp.TransactionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InspectorViewModelTest {

    private fun tx(id: String, status: Int?, state: TransactionState = TransactionState.Completed, url: String = "https://api.example.com/$id") =
        HttpTransaction(id = id, state = state, method = "GET", url = url, requestTimeMs = 0L, statusCode = status)

    private val list = listOf(
        tx("a", 200),
        tx("b", 302),
        tx("c", 404),
        tx("d", 503),
        tx("e", null, TransactionState.Failed),
        tx("f", null, TransactionState.InProgress),
    )

    @Test
    fun statusFilters() {
        assertEquals(listOf("a"), list.filter(StatusFilter.Success::matches).map { it.id })
        assertEquals(listOf("b"), list.filter(StatusFilter.Redirect::matches).map { it.id })
        assertEquals(listOf("c"), list.filter(StatusFilter.ClientError::matches).map { it.id })
        assertEquals(listOf("d"), list.filter(StatusFilter.ServerError::matches).map { it.id })
        assertEquals(listOf("e"), list.filter(StatusFilter.Failed::matches).map { it.id })
        assertEquals(6, list.count(StatusFilter.All::matches))
    }

    @Test
    fun searchAndFilterCombine() {
        assertEquals(listOf("c"), InspectorViewModel.filterItems(list, "example.com/c", StatusFilter.All).map { it.id })
        assertEquals(listOf("a"), InspectorViewModel.filterItems(list, "GET", StatusFilter.Success).map { it.id })
        assertTrue(InspectorViewModel.filterItems(list, "nothing", StatusFilter.All).isEmpty())
        assertFalse(InspectorViewModel.filterItems(list, "", StatusFilter.Failed).any { it.state != TransactionState.Failed })
    }
}
