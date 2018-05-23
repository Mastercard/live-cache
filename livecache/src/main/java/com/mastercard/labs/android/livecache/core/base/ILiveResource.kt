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

package com.mastercard.labs.android.livecache.core.base

import android.arch.lifecycle.LiveData
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import com.mastercard.labs.android.livecache.api.ApiResponse
import com.mastercard.labs.android.livecache.lifecycle.AbsentLiveData
import com.mastercard.labs.android.livecache.utils.Resource

interface ILiveResource<ResultType, ApiResultType> {

    @WorkerThread
    fun handleResponse(call: ApiResponse<ApiResultType>)

    @WorkerThread
    fun handleCacheResponse(result: ResultType?) {
    }

    @WorkerThread
    fun createCall(): ApiResponse<ApiResultType>

    @WorkerThread
    fun convertResponse(call: ApiResponse<ApiResultType>): ResultType? = call.body() as ResultType

    @WorkerThread
    fun processResult(result: ResultType)

    @WorkerThread
    fun processFailure(e: Throwable? = null): Resource.Error<ResultType> = Resource.Error(e)

    fun asLiveData(): LiveData<Resource<ResultType>>

    interface IStorable<ResultType> {
        @WorkerThread
        fun storeData(data: ResultType) {
        }

        @MainThread
        fun isCacheValid(data: ResultType?): Boolean = false // in the simple implementation fetch always from the network

        @MainThread
        fun loadFromDb(): LiveData<ResultType> = AbsentLiveData.new() // in the simple implementation load an empty value from DB
    }
}