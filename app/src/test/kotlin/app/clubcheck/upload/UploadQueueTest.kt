package app.clubcheck.upload

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class UploadQueueTest {
    private class FakeStore(vararg rows: QueuedCheckIn) : CheckInStore {
        val queue = rows.toMutableList(); val sent = mutableListOf<Long>(); val failed = mutableListOf<Long>()
        override suspend fun queued() = queue.toList()
        override suspend fun markSent(id: Long) { queue.removeAll { it.id == id }; sent += id }
        override suspend fun markFailed(id: Long) { queue.removeAll { it.id == id }; failed += id }
    }
    private class FakeApi(var retries: Int = 0, val rejectIds: Set<String> = emptySet()) : CloudApi {
        val accepted = mutableListOf<String>(); var calls = 0
        override suspend fun postCheckIn(payload: ByteArray, image: ByteArray): UploadResult {
            calls++
            if (retries > 0) { retries--; return UploadResult.RETRY }
            val text = String(payload)
            if (text in rejectIds) return UploadResult.REJECTED
            accepted += text; return UploadResult.OK
        }
    }
    private fun row(id: Long, hex: String = "6162") = QueuedCheckIn(id, "ev$id", hex, "img$id.jpg")
    private val read: (String) -> ByteArray = { it.toByteArray() }

    @Test fun uploadRetriesAfterFailureAndNeverDuplicates() = runBlocking {
        val store = FakeStore(row(1)); val api = FakeApi(retries = 1); val q = UploadQueue(store, api, read)
        assertEquals(0, q.drain()); assertEquals(listOf(1L), store.queue.map { it.id })
        assertEquals(1, q.drain()); assertTrue(store.queue.isEmpty())
        assertEquals(0, q.drain())
        assertEquals(1, api.accepted.size); assertEquals(listOf(1L), store.sent)
    }

    @Test fun stopsAfterFirstRetryableFailureSoAnOfflinePhoneDoesNotHammer() = runBlocking {
        val store = FakeStore(row(1), row(2), row(3)); val api = FakeApi(retries = 99)
        assertEquals(0, UploadQueue(store, api, read).drain())
        assertEquals(1, api.calls); assertEquals(3, store.queue.size)
    }

    @Test fun missingImageIsMarkedFailedAndTheRestContinue() = runBlocking {
        val store = FakeStore(row(1), row(2)); val api = FakeApi()
        val q = UploadQueue(store, api) { if (it == "img1.jpg") throw IOException("gone") else it.toByteArray() }
        assertEquals(1, q.drain())
        assertEquals(listOf(1L), store.failed); assertEquals(listOf(2L), store.sent); assertTrue(store.queue.isEmpty())
    }

    @Test fun aRowTheServerRefusesDoesNotBlockTheOnesAfterIt() = runBlocking {
        val store = FakeStore(row(1, "6262"), row(2, "6161")); val api = FakeApi(rejectIds = setOf("bb"))
        assertEquals(1, UploadQueue(store, api, read).drain())
        assertEquals(listOf(1L), store.failed); assertEquals(listOf(2L), store.sent)
    }

    @Test fun payloadHexIsDecodedBeforeSending() = runBlocking {
        val api = FakeApi(); UploadQueue(FakeStore(row(1)), api, read).drain()
        assertEquals("ab", api.accepted.single())
    }
}
