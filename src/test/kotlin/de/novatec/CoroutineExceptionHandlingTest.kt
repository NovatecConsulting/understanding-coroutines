package de.novatec

import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class CoroutineExceptionHandlingTest {

    @Nested
    inner class `inside a non-root coroutine scope` {

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
            val exceptionHandler = ExceptionHandler()

            assertThrows<IllegalArgumentException> {
                runBlocking {
                    launch(exceptionHandler) {
                        throw IllegalArgumentException("launch without root coroutine")
                    }
                }
            }
            assertFalse(exceptionHandler.wasCalled)
        }

        @Test
        fun `does not trigger exception handler of an async block`() {
            val exceptionHandler = ExceptionHandler()

            assertThrows<IllegalArgumentException> {
                runBlocking {
                    async(exceptionHandler) {
                        throw IllegalArgumentException("async without root coroutine")
                    }
                }
            }
            assertFalse(exceptionHandler.wasCalled)
        }
    }

    @Nested
    inner class `inside a root coroutine scope` {

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
            val exceptionHandler = ExceptionHandler()

            val result =
                root.launch(exceptionHandler) {
                    throw IllegalArgumentException("launch inside root coroutine")
                }

            runBlocking {
                result.join()
                assertTrue(exceptionHandler.wasCalled)
            }
        }

        @Test
        fun `does not trigger installed exception handler of a nested launch block`() {
            val exceptionHandler = ExceptionHandler()
            val nestedExceptionHandler = ExceptionHandler()

            val result =
                root.launch(exceptionHandler) {
                    launch(nestedExceptionHandler) {
                        throw IllegalArgumentException("launch inside root coroutine")
                    }
                }

            runBlocking {
                result.join()
                assertTrue(exceptionHandler.wasCalled)
                assertFalse(nestedExceptionHandler.wasCalled)
            }
        }

        @Test
        fun `does not trigger installed exception handler in an async block`() {
            val exceptionHandler = ExceptionHandler()

            val result =
                root.async(exceptionHandler) { throw IllegalArgumentException("async inside root coroutine") }

            runBlocking {
                result.join()
                assertFalse(exceptionHandler.wasCalled)
            }
        }
    }

    @Nested
    inner class `inside a supervised scope` {

        @Test
        fun `reports its exception to caller in an async block`() {
            val result = runBlocking {
                supervisorScope {
                    async { throw IllegalArgumentException("async inside supervised scope") }
                }
            }
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    result.await()
                }
            }
        }

        @Test
        fun `does not report its exception to caller in a launch block`() {
            // Default behavior: Log exception to System ERR
            runBlocking {
                supervisorScope {
                    launch { throw IllegalArgumentException("launch inside supervised scope") }
                }
            }
        }

        @Test
        fun `trigger installed exception handler in a launch block`() {
            val exceptionHandler = ExceptionHandler()

            val job = runBlocking {
                supervisorScope {
                    launch(exceptionHandler) { throw IllegalArgumentException("launch inside supervised scope") }
                }
            }
            runBlocking {
                job.join()
            }
            assertTrue(exceptionHandler.wasCalled)
        }

        @Test
        fun `does not trigger installed exception handler in an async block`() {
            val exceptionHandler = ExceptionHandler()

            val job = runBlocking {
                supervisorScope {
                    async(exceptionHandler) { throw IllegalArgumentException("launch inside supervised scope") }
                }
            }
            runBlocking {
                job.join()
            }
            assertFalse(exceptionHandler.wasCalled)
        }
    }

    private class ExceptionHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
        CoroutineExceptionHandler {
        val wasCalled: Boolean
            get() = this.handleExceptionCalled

        private var handleExceptionCalled = false

        override fun handleException(context: CoroutineContext, exception: Throwable) {
            this.handleExceptionCalled = true
        }
    }
}
