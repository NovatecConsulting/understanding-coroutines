package de.novatec

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class CoroutineCancellationBehaviourTest {

    @Test
    fun `cancel child job does not cancel parent job`() = runBlocking {
        var child: Job? = null
        val parent = launch {
            val inner = launch {
                delay(Long.MAX_VALUE)
            }
            inner.cancel()
            inner.join()
            child = inner
        }
        parent.join()

        assertIsCancelled(child)
        assertIsNotCancelled(parent)
    }

    @Test
    fun `exception in child job does cancel parent job`() = runBlocking {
        val parent = GlobalScope.launch(NoOpExceptionHandler()) {
            launch {
                error("failed")
            }.join()
        }
        parent.join()

        assertIsCancelled(parent)
    }

    @Test
    fun `sibling jobs are not cancelled if one of them fails in SupervisorScope`() = runBlocking {
        supervisorScope {
            val first = launch(NoOpExceptionHandler()) {
                error("SupervisorFirst fails")
            }

            val second = launch {
                delay(100)
            }

            joinAll(first, second)

            assertIsCancelled(first)
            assertIsNotCancelled(second)
        }
    }

    @Test
    fun `sibling jobs are cancelled if any of them fails`() {
        var firstChild: Job? = null
        var secondChild: Job? = null
        runBlocking {
            GlobalScope.launch(NoOpExceptionHandler()) {

                val innerFirst = launch {
                    error("innerFirst fails")
                }
                firstChild = innerFirst

                val innerSecond = launch {
                    // this would run forever, if it wouldn't be cancelled
                    delay(Long.MAX_VALUE)
                }
                secondChild = innerSecond

                joinAll(innerFirst, innerSecond)
            }.join()
        }

        assertIsCancelled(firstChild)
        assertIsCancelled(secondChild)
    }

    private fun assertIsNotCancelled(job: Job?) {
        if (job == null) {
            throw AssertionError("Job is null, but should not be cancelled.")
        }

        if (job.isCancelled) {
            throw AssertionError("Job is cancelled")
        }
    }

    private fun assertIsCancelled(job: Job?) {
        if (job == null) {
            throw AssertionError("Job is null, but should be cancelled.")
        }

        if (!job.isCancelled) {
            throw AssertionError("Job is not cancelled")
        }
    }

    /**
     * This class serves the purpose that no exception is printed on System.err.
     *
     * Printing on System.err is the default behaviour for Root Coroutine Contexts or
     * in Supervision.
     */
    private class NoOpExceptionHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
        CoroutineExceptionHandler {

        override fun handleException(context: CoroutineContext, exception: Throwable) {}
    }
}