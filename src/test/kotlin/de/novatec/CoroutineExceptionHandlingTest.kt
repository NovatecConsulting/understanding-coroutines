package de.novatec

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.CoroutineContext

class CoroutineExceptionHandlingTest {

    @Nested
    inner class `inside a non-root coroutine` {

        @Test
        fun `throws exception to parent in an async block`() {
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    async {
                        throw IllegalArgumentException("async without root coroutine")
                    }
                }
            }
        }

        @Test
        fun `throws exception to parent in a launch block`() {
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    launch {
                        throw IllegalArgumentException("launch without root coroutine")
                    }
                }
            }
        }

        @Test
        fun `triggers installed exception handler of a launch block`() {
            var isExceptionHandlerInvoked = false

            assertThrows<IllegalArgumentException> {
                runBlocking {
                    launch(CoroutineExceptionHandler { _: CoroutineContext, _: Throwable -> isExceptionHandlerInvoked = true }) {
                        throw IllegalArgumentException("launch without root coroutine")
                    }
                }
            }
            assertFalse(isExceptionHandlerInvoked)
        }

        @Test
        fun `does not trigger exception handler of an async block`() {
            var isExceptionHandlerInvoked = false

            assertThrows<IllegalArgumentException> {
                runBlocking {
                    async(CoroutineExceptionHandler { _: CoroutineContext, _: Throwable -> isExceptionHandlerInvoked = true }) {
                        throw IllegalArgumentException("async without root coroutine")
                    }
                }
            }
            assertFalse(isExceptionHandlerInvoked)
        }
    }

    @Nested
    inner class `inside a root coroutine` {

        private val root = CoroutineScope(Job())

        @Test
        fun `reports its exception to caller in an async block`() {
            val result = root.async {
                throw IllegalArgumentException("async inside root coroutine")
            }

            assertThrows<IllegalArgumentException> {
                runBlocking {
                    result.await()
                }
            }
        }

        @Test
        fun `does not report its exception to caller in a launch block`() {
            val result = root.launch {
                throw IllegalArgumentException("launch inside root coroutine")
            }
            // Default behavior: Log exception to System ERR
            runBlocking {
                result.join()
            }
        }

        @Test
        fun `trigger installed exception handler in a launch block`() {
            var isExceptionHandlerInvoked = false
            val rootWithExceptionHandler = CoroutineScope(CoroutineExceptionHandler { _: CoroutineContext, _: Throwable ->
                isExceptionHandlerInvoked = true
            })

            val result = rootWithExceptionHandler.launch { throw IllegalArgumentException("launch inside root coroutine") }

            runBlocking {
                result.join()
                assertTrue(isExceptionHandlerInvoked)
            }
        }

        @Test
        fun `does not trigger installed exception handler in an async block`() {
            var isExceptionHandlerInvoked = false
            val rootWithExceptionHandler = CoroutineScope(CoroutineExceptionHandler { _: CoroutineContext, _: Throwable ->
                isExceptionHandlerInvoked = true
            })

            val result = rootWithExceptionHandler.async { throw IllegalArgumentException("async inside root coroutine") }

            runBlocking {
                result.join()
                assertFalse(isExceptionHandlerInvoked)
            }
        }
    }
}