/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright 2018 MasterCard International.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 * Neither the name of the MasterCard International Incorporated nor the names of its
 * contributors may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package com.mastercard.labs.android.livecache.utils

import com.mastercard.labs.android.livecache.concurrent.IAppExecutors
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CountingAppExecutors {

    private val LOCK = Object()

    private var taskCount = 0

    val appExecutors: IAppExecutors

    init {
        val increment = Runnable {
            synchronized(LOCK) {
                taskCount--
                //println("taskCount=$taskCount (inc)")
                if (taskCount == 0) {
                    LOCK.notifyAll()
                }
            }
        }
        val decrement = Runnable {
            synchronized(LOCK) {
                taskCount++
                //println("taskCount=$taskCount (dec)")
            }
        }
        appExecutors = object : IAppExecutors {
            override val networkIO: Executor = CountingExecutor(increment, decrement)
            override val mainThread: Executor = CountingExecutor(increment, decrement)
            override val diskIO: Executor = CountingExecutor(increment, decrement)
        }
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    fun drainTasks(time: Int, timeUnit: TimeUnit) {
        val end = System.currentTimeMillis() + timeUnit.toMillis(time.toLong())
        while (true) {
            synchronized(LOCK) {
                if (taskCount == 0) {
                    return
                }
                val now = System.currentTimeMillis()
                val remaining = end - now
                if (remaining > 0) {
                    LOCK.wait(remaining)
                } else {
                    throw TimeoutException("could not drain tasks")
                }
            }
        }
    }

    private class CountingExecutor(private val increment: Runnable, private val decrement: Runnable) : Executor {

        private val delegate = Executors.newSingleThreadExecutor()

        override fun execute(command: Runnable) {
            increment.run()
            delegate.execute {
                try {
                    command.run()
                } finally {
                    decrement.run()
                }
            }
        }
    }
}