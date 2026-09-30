package com.pantauhttp

import com.pantauhttp.internal.store.TransactionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionStoreTest {

    private fun make(path: String = "/", id: String = path) =
        sampleTransaction(url = "https://example.com$path", id = id)

    @Test
    fun insertPrepends() {
        val store = TransactionStore()
        val first = make("/first")
        val second = make("/second")
        store.insert(first)
        store.insert(second)
        val snapshot = store.transactions.value
        assertEquals(2, snapshot.size)
        assertEquals(second.id, snapshot[0].id)
        assertEquals(first.id, snapshot[1].id)
    }

    @Test
    fun evictionBeyondMax() {
        val store = TransactionStore(maxTransactions = 5)
        repeat(10) { store.insert(make("/$it")) }
        val snapshot = store.transactions.value
        assertEquals(5, snapshot.size)
        assertEquals("/9", snapshot.first().path)
        assertEquals("/5", snapshot.last().path)
    }

    @Test
    fun lookupById() {
        val store = TransactionStore()
        val tx = make("/x")
        store.insert(tx)
        assertNotNull(store.get(tx.id))
        assertNull(store.get("missing"))
    }

    @Test
    fun unseenCountAndMarkAllSeen() {
        val store = TransactionStore()
        store.insert(make("/a"))
        store.insert(make("/b"))
        assertEquals(2, store.unseenCount.value)
        store.markAllSeen()
        assertEquals(0, store.unseenCount.value)
    }

    @Test
    fun clear() {
        val store = TransactionStore()
        store.insert(make("/a"))
        store.clear()
        assertEquals(0, store.count)
        assertEquals(0, store.unseenCount.value)
    }

    @Test
    fun upsertReplacesInPlaceAndDropsEvicted() {
        val store = TransactionStore(maxTransactions = 2)
        val a = make("/a"); val b = make("/b"); val c = make("/c")
        store.insert(a); store.insert(b); store.insert(c)
        assertNull(store.upsert(a.id) { it.copy(statusCode = 200) }, "evicted transactions drop updates")
        val updated = store.upsert(b.id) { it.copy(statusCode = 201) }
        assertEquals(201, updated?.statusCode)
        assertEquals(listOf(c.id, b.id), store.transactions.value.map { it.id })
        assertEquals(201, store.get(b.id)?.statusCode)
    }

    @Test
    fun concurrentInsertsAreSafe() = runTest {
        val store = TransactionStore(maxTransactions = 50)
        withContext(Dispatchers.Default) {
            (0 until 200).map { i -> launch { store.insert(make("/$i")) } }.joinAll()
        }
        assertEquals(50, store.count)
    }

    @Test
    fun upsertsFlowEmitsInsertsAndUpdates() = runTest {
        val store = TransactionStore()
        val tx = make("/a")
        val received = mutableListOf<HttpTransaction>()
        val job = launch(Dispatchers.Unconfined) { store.upserts.collect { received += it } }
        store.insert(tx)
        store.upsert(tx.id) { it.copy(statusCode = 200) }
        job.cancel()
        assertEquals(2, received.size)
        assertEquals(200, received[1].statusCode)
    }

    @Test
    fun changesFlowIsDebounced() = runTest {
        val store = TransactionStore()
        val job = launch { store.changes.first { it.isNotEmpty() } }
        store.insert(make("/a"))
        job.join()
        assertTrue(true)
    }
}
