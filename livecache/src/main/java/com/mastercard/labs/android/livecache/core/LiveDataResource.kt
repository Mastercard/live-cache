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

package com.mastercard.labs.android.livecache.core

import android.annotation.SuppressLint
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.support.annotation.MainThread
import com.mastercard.labs.android.livecache.concurrent.IAppExecutors
import com.mastercard.labs.android.livecache.core.base.ILiveResource
import com.mastercard.labs.android.livecache.lifecycle.AbsentLiveData
import com.mastercard.labs.android.livecache.utils.Resource
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A generic class that can provide a resource backed by both the database and/or the network.
 *
 * @param ResultType Type for the Resource data
 * @param ApiResultType Type for the API response
 * @property isStorable True if the data can be stored, false otherwise (default is false)
 * @property appExecutors executor pools to use
 * @constructor Create a new Request Resource
 *
 * @author ech0s7r
 */
@Suppress("LeakingThis")
@SuppressLint("NoLoggedException")
internal abstract class LiveDataResource<ResultType, ApiResultType>
@MainThread
internal constructor(private val appExecutors: IAppExecutors,
                     private val isStorable: Boolean = false,
                     private val preload: Boolean = false)
    : ILiveResource<ResultType, ApiResultType>, ILiveResource.IStorable<ResultType> {

    private inner class CallResult(val result: ResultType? = null, val throwable: Throwable? = null)


    private var preloaded = AtomicBoolean(false)
    private val result = MediatorLiveData<Resource<ResultType>>()

    init {
        result.value = Resource.Loading(null)
        if (isStorable) {
            val dbSource = try {
                loadFromDb()
            } catch (t: Throwable) {
                AbsentLiveData.new<ResultType>()
            }
            result.addSource(dbSource) { data ->
                result.removeSource(dbSource)
                if (isCacheValid(data)) {
                    result.addSource(dbSource) { newData ->
                        appExecutors.diskIO.execute { handleCacheResponse(newData) }
                        result.value = newData?.let {
                            Resource.Success(it)
                        } ?: processFailure()

                    }
                } else {
                    if (preload) {
                        result.addSource(dbSource) { newData ->
                            newData?.let { result.value = Resource.Preload(it) }
                            if (preloaded.compareAndSet(false, true)) {
                                fetchData() // only once
                            }
                        }
                    } else {
                        fetchData()
                    }
                }
            }
        } else {
            fetchData()
        }
    }

    private fun fetchData() {
        val remoteData = MutableLiveData<CallResult>()
        executeCall(remoteData)
        dispatchResult(remoteData)
    }

    private fun executeCall(remoteData: MutableLiveData<CallResult>) {
        appExecutors.networkIO.execute {
            try {
                val call = createCall()
                if (!call.isSuccessful) {
                    result.postValue(processFailure())
                } else {
                    handleResponse(call)
                    val converted = convertResponse(call)
                    if (converted != null) {
                        remoteData.postValue(CallResult(converted))
                        processResult(converted)
                    } else {
                        if (!preload) {
                            remoteData.postValue(null)
                        }
                    }
                }
            } catch (t: Throwable) {
                remoteData.postValue(CallResult(throwable = t))
            }
        }
    }


    private fun dispatchResult(remoteData: MutableLiveData<CallResult>) {
        result.addSource(remoteData) { data ->
            result.removeSource(remoteData)
            if (data?.result != null) {
                if (isStorable) {
                    appExecutors.diskIO.execute {
                        storeData(data.result)
                        result.postValue(Resource.Success(data.result))
                    }
                } else {
                    result.value = Resource.Success(data.result)
                }
            } else {
                result.value = processFailure(data?.throwable)
            }
        }
    }

    override fun asLiveData(): LiveData<Resource<ResultType>> = result
}