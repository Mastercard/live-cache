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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.mastercard.labs.android.livecache.api.ApiResponse
import com.mastercard.labs.android.livecache.cache.CachePolicy
import com.mastercard.labs.android.livecache.utils.KMockito
import com.mastercard.labs.android.livecache.utils.Resource
import junit.framework.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.Mockito.*

/**
 * @author ech0s7r
 */
@RunWith(JUnit4::class)
class LiveResourceTest : BaseLiveCacheTest() {

    data class ServerResponse(val value: String) : ApiResponse<ServerResponse> {
        override val isSuccessful: Boolean = false
        override fun body(): ServerResponse = ServerResponse(value)
    }


    private lateinit var remoteCall: () -> ApiResponse<ServerResponse>
    private lateinit var onResult: (ServerResponse) -> Unit
    private lateinit var mapper: ((t: ServerResponse) -> ServerResponse)
    private lateinit var cachePolicy: CachePolicy<ServerResponse>
    private lateinit var liveData: LiveResource<ServerResponse, ServerResponse>

    private lateinit var serverResponseFailure: ApiResponse<ServerResponse>
    private lateinit var serverResponseOK: ApiResponse<ServerResponse>

    @Before
    fun setup() {
        remoteCall = KMockito.mock()
        onResult = KMockito.mock()
        mapper = KMockito.mock()
        cachePolicy = KMockito.mock()
        serverResponseOK = KMockito.mock()
        serverResponseFailure = KMockito.mock()

        `when`(serverResponseFailure.isSuccessful).thenReturn(false)

        `when`(serverResponseOK.isSuccessful).thenReturn(true)
        //`when`(serverResponseOK.code()).thenReturn(200)
        `when`(serverResponseOK.body()).thenReturn(ServerResponse("ok from server"))
    }

    @Test
    fun `not storable, no pre-loading, server fails, no cache, no mapper`() {
        `when`(remoteCall.invoke()).thenReturn(serverResponseFailure) // return false from server
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid

        LiveResource.fetch<ServerResponse, String>(appExecutors, false, false, remoteCall)

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = false, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .onResult { onResult }
        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        drain()
        Mockito.verify(observer).onChanged(Resource.Loading(null))
        Mockito.verify(observer, times(1)).onChanged(KMockito.isA<Resource.Error<ServerResponse>>())
        verifyNoMoreInteractions(observer)
    }


    @Test
    fun `not storable, no pre-loading, server ok, no cache, no mapper`() {
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return OK from server

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = false, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        drain()
        Mockito.verify(observer).onChanged(Resource.Loading(null))
        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse(value = "ok from server")))
        verifyNoMoreInteractions(observer)
    }

    @Test
    fun `not storable, no pre-loading, server ok, no cache, with mapper ok`() {
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return OK from server
        `when`(mapper.invoke(KMockito.any())).thenReturn(ServerResponse("mapper changed"))

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = false, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .mapper(mapper)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        drain()
        Mockito.verify(observer).onChanged(Resource.Loading(null))
        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse(value = "mapper changed")))
        verifyNoMoreInteractions(observer)
    }

    @Test
    fun `not storable, no pre-loading, server ok, no cache, with mapper fails`() {
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return OK from server
        `when`(mapper.invoke(KMockito.any())).thenThrow(RuntimeException::class.java)

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = false, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .mapper(mapper)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        drain()
        Mockito.verify(observer).onChanged(Resource.Loading(null))
        Mockito.verify(observer, times(1)).onChanged(KMockito.isA<Resource.Error<ServerResponse>>())
        verifyNoMoreInteractions(observer)
    }

    @Test
    fun `storable, no pre-loading, cache valid, server ok, with mapper ok`() {
        val dbData = MutableLiveData<ServerResponse>()
        `when`(cachePolicy.isValidCbk).thenReturn { true } // cache valid
        `when`(cachePolicy.loadCbk).thenReturn { dbData }

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = true, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .mapper(mapper)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        Mockito.verify(observer).onChanged(Resource.Loading(null))
        dbData.value = ServerResponse("my from db")

        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse("my from db")))
        verifyNoMoreInteractions(observer)

        Mockito.verify(cachePolicy, times(0)).saveCbk // save not called
        Mockito.verifyZeroInteractions(mapper) // No mapper calls
        Mockito.verifyZeroInteractions(remoteCall) // No server calls, cache is valid
    }

    @Test
    fun `storable, no pre-loading, cache not valid, server ok, with no mapper`() {
        var saved: ServerResponse? = null
        val dbData = MutableLiveData<ServerResponse>()
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid
        `when`(cachePolicy.loadCbk).thenReturn { dbData }
        `when`(cachePolicy.saveCbk).thenReturn { saved = it }
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return ok from server

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = true, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        Mockito.verify(observer).onChanged(Resource.Loading(null))
        dbData.value = ServerResponse("my from db")

        drain()

        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse("ok from server")))
        verifyNoMoreInteractions(observer)

        // verify save to db new value fetched from server
        Mockito.verify(cachePolicy, times(1)).saveCbk // save called
        Mockito.verifyZeroInteractions(mapper) // No mapper calls
        Assert.assertEquals(ServerResponse("ok from server"), saved)
    }


    @Test
    fun `storable, no pre-loading, cache not valid, server ok, with mapper`() {
        var saved: ServerResponse? = null
        val dbData = MutableLiveData<ServerResponse>()
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid
        `when`(cachePolicy.loadCbk).thenReturn { dbData }
        `when`(cachePolicy.saveCbk).thenReturn { saved = it }
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return ok from server
        `when`(mapper.invoke(KMockito.any())).thenReturn(ServerResponse("after mapping"))

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = true, preload = false, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .mapper(mapper)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        Mockito.verify(observer).onChanged(Resource.Loading(null))
        dbData.value = ServerResponse("my from db")

        drain()

        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse("after mapping")))
        verifyNoMoreInteractions(observer)

        // verify save to db new value fetched from server
        Mockito.verify(cachePolicy, times(1)).saveCbk // save called
        Assert.assertEquals(ServerResponse("after mapping"), saved)
    }


    @Test
    fun `storable, pre-loading, cache valid, server ok, with mapper`() {
        val dbData = MutableLiveData<ServerResponse>()
        `when`(cachePolicy.isValidCbk).thenReturn { true } // cache valid
        `when`(cachePolicy.loadCbk).thenReturn { dbData }
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return ok from server
        `when`(mapper.invoke(KMockito.any())).thenReturn(ServerResponse("after mapping"))

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = true, preload = true, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .mapper(mapper)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        Mockito.verify(observer).onChanged(Resource.Loading(null))
        dbData.value = ServerResponse("my from db")

        drain()

        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse("my from db")))


        verifyNoMoreInteractions(observer)

        // verify save to db new value fetched from server
        Mockito.verify(cachePolicy, times(0)).saveCbk // save not called
    }

    @Test
    fun `storable, pre-loading, cache not valid, server ok, with mapper`() {
        var saved: ServerResponse? = null
        val dbData = MutableLiveData<ServerResponse>()
        `when`(cachePolicy.isValidCbk).thenReturn { false } // cache not valid
        `when`(cachePolicy.loadCbk).thenReturn { dbData }
        `when`(cachePolicy.saveCbk).thenReturn { saved = it; dbData.value = it }
        `when`(remoteCall.invoke()).thenReturn(serverResponseOK) // return ok from server
        `when`(mapper.invoke(KMockito.any())).thenReturn(ServerResponse("after mapping"))

        liveData = LiveResource.fetch<ServerResponse, ServerResponse>(appExecutors, isStorable = true, preload = true, remoteCall = remoteCall)
                .setCachePolicy(cachePolicy)
                .mapper(mapper)
                .onResult { onResult }

        val observer: Observer<Resource<ServerResponse>> = KMockito.mock()
        liveData.asLiveData().observeForever(observer)

        Mockito.verify(observer).onChanged(Resource.Loading(null))
        dbData.value = ServerResponse("my from db")

        drain()

        Mockito.verify(observer, times(1)).onChanged(Resource.Preload(ServerResponse("my from db")))

        Mockito.verify(observer, times(1)).onChanged(Resource.Success(ServerResponse("after mapping")))

        Mockito.verify(observer, times(1)).onChanged(Resource.Preload(ServerResponse("after mapping")))

        verifyNoMoreInteractions(observer)

        // verify save to db new value fetched from server
        Mockito.verify(cachePolicy, times(1)).saveCbk // save called
        Assert.assertEquals(ServerResponse("after mapping"), saved)
    }

}