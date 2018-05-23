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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.mastercard.labs.android.livecache.api.ApiResponse
import com.mastercard.labs.android.livecache.api.adapter.ServerResponse
import com.mastercard.labs.android.livecache.core.LiveDataResource
import com.mastercard.labs.android.livecache.lifecycle.AbsentLiveData
import com.mastercard.labs.android.livecache.utils.KMockito
import com.mastercard.labs.android.livecache.utils.Resource
import junit.framework.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.*
import retrofit2.Response
import java.util.concurrent.CountDownLatch


/**
 * @author ech0s7r
 */
@RunWith(JUnit4::class)
class LiveDataResourceTest : BaseLiveCacheTest() {

    data class Foo(val value: String)

    private fun <T> createLiveResource(storable: Boolean, preload: Boolean,
                                       createCall: () -> Response<T>,
                                       convertResponse: ((ApiResponse<T>) -> T?)? = null,
                                       processResult: ((T) -> Unit)? = null,
                                       handleResponse: ((ApiResponse<T>) -> Unit)? = null,
                                       isCacheValid: ((data: T?) -> Boolean)? = null,
                                       loadFromDb: (() -> LiveData<T>)? = null,
                                       storeData: ((T) -> Unit)? = null): LiveDataResource<T, T> {

        return object : LiveDataResource<T, T>(appExecutors, isStorable = storable, preload = preload) {
            override fun handleResponse(call: ApiResponse<T>) = handleResponse?.invoke(call) ?: Unit
            override fun createCall(): ApiResponse<T> = ServerResponse(createCall.invoke())

            override fun processResult(result: T) = processResult?.invoke(result) ?: Unit
            override fun convertResponse(call: ApiResponse<T>): T? = convertResponse?.invoke(call)
            override fun isCacheValid(data: T?) = isCacheValid?.invoke(data)
                    ?: super.isCacheValid(data)

            override fun loadFromDb(): LiveData<T> = loadFromDb?.invoke() ?: super.loadFromDb()

            override fun storeData(data: T) {
                storeData?.invoke(data)
            }
        }
    }

    private val networkResult = Foo("Network")
    private val dbResult = Foo("Db")
    private val defaultNetworkResponse = Response.success(networkResult)
    private val dbLiveData = MutableLiveData<Foo>()
    private var loadFromDbCalled = false
    private var storeCalledObject: Any? = null
    private var remoteCalled = false

    @Test
    fun `no storable, fetch from network ok`() {
        val waitForLoading = CountDownLatch(1)
        val liveResource = createLiveResource<Foo>(
                storable = false,
                preload = false,
                createCall = { waitForLoading.await(); defaultNetworkResponse },
                convertResponse = { networkResult },
                loadFromDb = { loadFromDbCalled = true; AbsentLiveData.new() },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        val inOrder = inOrder(observer)
        inOrder.verify(observer).onChanged(Resource.Loading(null))
        waitForLoading.countDown()
        drain()
        inOrder.verify(observer).onChanged(Resource.Success(networkResult))
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(loadFromDbCalled)
        Assert.assertNull(storeCalledObject)
    }

    @Test
    fun `no storable, fetch from network fails`() {
        val exception = RuntimeException()
        val waitForLoading = CountDownLatch(1)
        val liveResource = createLiveResource<Foo>(
                storable = false,
                preload = false,
                createCall = { waitForLoading.await(); throw exception },
                loadFromDb = { loadFromDbCalled = true; AbsentLiveData.new() },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer).onChanged(Resource.Loading(null))
        waitForLoading.countDown()
        drain()
        verify(observer).onChanged(KMockito.isA<Resource.Error<Foo>>())
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(loadFromDbCalled)
        Assert.assertNull(storeCalledObject)
    }

    @Test
    fun `no storable, fetch from network ok, but convert fail`() {
        val waitForLoading = CountDownLatch(1)
        val liveResource = createLiveResource<Foo>(
                storable = false,
                preload = false,
                createCall = { waitForLoading.await(); defaultNetworkResponse },
                loadFromDb = { loadFromDbCalled = true; AbsentLiveData.new() },
                convertResponse = { null },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer).onChanged(Resource.Loading(null))
        waitForLoading.countDown()
        drain()
        verify(observer, times(1)).onChanged(KMockito.isA<Resource.Error<Foo>>())
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(loadFromDbCalled)
        Assert.assertNull(storeCalledObject)
    }

    @Test
    fun `no storable, fetch from network fails and convert fail`() {
        val exception = RuntimeException()
        val waitForLoading = CountDownLatch(1)
        val liveResource = createLiveResource<Foo>(
                storable = false,
                preload = false,
                createCall = { waitForLoading.await(); throw exception },
                loadFromDb = { loadFromDbCalled = true; AbsentLiveData.new() },
                convertResponse = { null },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer).onChanged(Resource.Loading(null))
        waitForLoading.countDown()
        drain()
        verify(observer, times(1)).onChanged(KMockito.isA<Resource.Error<Foo>>())
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(loadFromDbCalled)
        Assert.assertNull(storeCalledObject)
    }

    @Test
    fun `is storable, no preload, cache is valid and read from db`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = false,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { dbLiveData },
                isCacheValid = { true },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        dbLiveData.value = dbResult
        drain()
        verify(observer, times(1)).onChanged(Resource.Loading(null))
        verify(observer, times(1)).onChanged(Resource.Success(dbResult))
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(remoteCalled)
        Assert.assertNull(storeCalledObject)
    }

    @Test
    fun `is storable, no preload, cache is valid but cannot read from db`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = false,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { throw RuntimeException() },
                isCacheValid = { true },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer, times(1)).onChanged(KMockito.isA<Resource.Error<Foo>>())
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(remoteCalled)
        Assert.assertNull(storeCalledObject)
    }

    @Test
    fun `is storable, with preload, cache is valid and read from db`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = true,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { dbLiveData },
                isCacheValid = { true },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer, times(1)).onChanged(Resource.Loading(null))
        dbLiveData.value = dbResult
        drain()
        verify(observer, times(1)).onChanged(Resource.Success(dbResult))
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(remoteCalled)
        Assert.assertNull(storeCalledObject)
    }


    @Test
    fun `is storable, with preload, cache not valid, convert fail`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = true,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { dbLiveData },
                isCacheValid = { false },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer, times(1)).onChanged(Resource.Loading(null))

        dbLiveData.value = dbResult
        drain()

        verify(observer, times(1)).onChanged(Resource.Preload(dbResult))

        verifyNoMoreInteractions(observer)

        Assert.assertTrue(remoteCalled)
        Assert.assertNull(storeCalledObject)
    }


    @Test
    fun `is storable, with preload, cache not valid, convert ok`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = true,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { dbLiveData },
                isCacheValid = { false },
                convertResponse = { networkResult },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer, times(1)).onChanged(Resource.Loading(null))

        dbLiveData.value = dbResult
        drain()

        verify(observer, times(1)).onChanged(Resource.Preload(dbResult))


        verify(observer, times(1)).onChanged(Resource.Success(networkResult))
        verifyNoMoreInteractions(observer)

        Assert.assertTrue(remoteCalled)
        Assert.assertNotNull(storeCalledObject)
        Assert.assertEquals(networkResult, storeCalledObject)
    }

    @Test
    fun `is storable, with preload, cache valid, convert ok`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = true,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { dbLiveData },
                isCacheValid = { true },
                convertResponse = { networkResult },
                storeData = { storeCalledObject = it })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer, times(1)).onChanged(Resource.Loading(null))

        dbLiveData.value = dbResult

        drain()
        verify(observer, times(1)).onChanged(Resource.Success(dbResult))
        verifyNoMoreInteractions(observer)

        Assert.assertFalse(remoteCalled)
        Assert.assertNull(storeCalledObject)
    }


    @Test
    fun `is storable, with preload, cache not valid, convert ok, update db after`() {
        val liveResource = createLiveResource<Foo>(
                storable = true,
                preload = true,
                createCall = { remoteCalled = true; defaultNetworkResponse },
                loadFromDb = { dbLiveData },
                isCacheValid = { false },
                convertResponse = { networkResult },
                storeData = { storeCalledObject = it; })

        val observer: Observer<Resource<Foo>> = KMockito.mock()
        liveResource.asLiveData().observeForever(observer)

        verify(observer, times(1)).onChanged(Resource.Loading(null))

        dbLiveData.value = dbResult
        drain()

        verify(observer, times(1)).onChanged(Resource.Preload(dbResult))

        verify(observer, times(1)).onChanged(Resource.Success(networkResult))

        // simulate save in db after fetching from network
        dbLiveData.value = dbResult.copy(value = "New DB after network")

        drain()

        verify(observer, times(1)).onChanged(Resource.Preload(dbResult.copy(value = "New DB after network")))

        // for some reason somebody update the db after a while...
        dbLiveData.value = dbResult.copy(value = "after a while...")
        drain()

        verify(observer, times(1)).onChanged(Resource.Preload(dbResult.copy(value = "after a while...")))

        verifyNoMoreInteractions(observer)

        Assert.assertTrue(remoteCalled)
        Assert.assertNotNull(storeCalledObject)
        Assert.assertEquals(networkResult, storeCalledObject)
    }

}