package app.clubcheck.upload

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class UploadQueueTest {
    private class FakeStore(vararg rows: QueuedCheckIn) : CheckInStore {
        val queue = rows.toMutableList(); val sent = mutableListOf<Long>()
        override suspend fun queued() = queue.toList()
        override suspend fun markSent(id: Long) { queue.removeAll { it.id == id }; sent += id }
    }
    private class FakeApi(var failures: Int = 0) : CloudApi {
        val accepted = mutableListOf<String>(); var calls = 0
        override suspend fun postCheckIn(payload: ByteArray, image: ByteArray): Boolean {
            calls++
            if (failures > 0) { failures--; return false }
            accepted += String(payload); return true
        }
    }
    private fun row(id: Long) = QueuedCheckIn(id, "ev$id", "6162", "img$id.jpg")
    private val read: (String) -> ByteArray = { it.toByteArray() }

    @Test fun uploadRetriesAfterFailureAndNeverDuplicates() = runBlocking {
        val store = FakeStore(row(1)); val api = FakeApi(failures = 1); val q = UploadQueue(store, api, read)
        assertEquals(0, q.drain()); assertEquals(listOf(1L), store.queue.map { it.id })   // failed: still queued
        assertEquals(1, q.drain()); assertTrue(store.queue.isEmpty())                       // retried: sent
        assertEquals(0, q.drain())                                                          // nothing left to send
        assertEquals(1, api.accepted.size); assertEquals(listOf(1L), store.sent)
    }

    @Test fun stopsAfterFirstFailureSoAnOfflinePhoneDoesNotHammer() = runBlocking {
        val store = FakeStore(row(1), row(2), row(3)); val api = FakeApi(failures = 99)
        assertEquals(0, UploadQueue(store, api, read).drain())
        assertEquals(1, api.calls); assertEquals(3, store.queue.size)
    }

    @Test fun missingImageStaysQueuedAndTheRestContinue() = runBlocking {
        val store = FakeStore(row(1), row(2)); val api = FakeApi()
        val q = UploadQueue(store, api) { if (it == "img1.jpg") throw IOException("gone") else it.toByteArray() }
        assertEquals(1, q.drain())
        assertEquals(listOf(1L), store.queue.map { it.id }); assertEquals(listOf(2L), store.sent)
    }

    @Test fun payloadHexIsDecodedBeforeSending() = runBlocking {
        val api = FakeApi(); UploadQueue(FakeStore(row(1)), api, read).drain()
        assertEquals("ab", api.accepted.single())    // 0x61 0x62 = "ab"
    }
}
