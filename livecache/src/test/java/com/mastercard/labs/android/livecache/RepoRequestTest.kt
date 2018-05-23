/*
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

package com.mastercard.labs.android.livecache

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.arch.lifecycle.MutableLiveData
import com.mastercard.labs.android.livecache.utils.KMockito
import com.mastercard.labs.android.livecache.utils.Resource
import junit.framework.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

/**
 * @author ech0s7r
 */
@RunWith(JUnit4::class)
class RepoRequestTest : BaseLiveCacheTest() {

    private data class Foo(val value: String)


    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var liveData: MutableLiveData<Resource<Foo>>

    private lateinit var repoRequest: RepoRequest<Foo, Resource.Error<Foo>>

    private var loadingCalled = false
    private var preloadCalled: Foo? = null
    private var successCalled: Foo? = null
    private var errorCalled: Resource.Error<Foo>? = null


    @Before
    fun setup() {
        lifecycleOwner = KMockito.mock()
        lifecycle = LifecycleRegistry(lifecycleOwner)
        liveData = MutableLiveData()

        Mockito.`when`(lifecycleOwner.lifecycle).thenReturn(lifecycle)

        repoRequest = RepoRequest(lifecycle) { liveData }
                .onLoading { loadingCalled = true }
                .onPreload { preloadCalled = it }
                .onSuccess { successCalled = it }
                .onError { errorCalled = it }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        repoRequest.execute()
    }


    @Test
    fun `loading and success`() {
        liveData.value = Resource.Loading(null)
        liveData.value = Resource.Success(Foo("ok"))
        Assert.assertTrue(loadingCalled)
        Assert.assertEquals(Foo("ok"), successCalled)
        Assert.assertNull(preloadCalled)
        Assert.assertNull(errorCalled)
    }

    @Test
    fun `just loading`() {
        liveData.value = Resource.Loading(null)
        Assert.assertTrue(loadingCalled)
        Assert.assertNull(successCalled)
        Assert.assertNull(preloadCalled)
        Assert.assertNull(errorCalled)
    }

    @Test
    fun `loading, preload, success`() {
        liveData.value = Resource.Loading(null)
        liveData.value = Resource.Preload(Foo("preload"))
        liveData.value = Resource.Success(Foo("ok"))
        Assert.assertTrue(loadingCalled)
        Assert.assertEquals(Foo("preload"), preloadCalled)
        Assert.assertEquals(Foo("ok"), successCalled)
        Assert.assertNull(errorCalled)
    }

    @Test
    fun `loading, preload, error`() {
        val error = RuntimeException("error")
        liveData.value = Resource.Loading(null)
        liveData.value = Resource.Preload(Foo("preload"))
        liveData.value = Resource.Error(error)
        Assert.assertTrue(loadingCalled)
        Assert.assertEquals(Foo("preload"), preloadCalled)
        Assert.assertEquals(error, errorCalled?.throwable)
        Assert.assertNull(successCalled)
    }

    @Test
    fun `loading, error`() {
        val error = RuntimeException("error")
        liveData.value = Resource.Loading(null)
        liveData.value = Resource.Error(error)
        Assert.assertTrue(loadingCalled)
        Assert.assertNull(successCalled)
        Assert.assertNull(preloadCalled)
        Assert.assertEquals(error, errorCalled?.throwable)
    }

}