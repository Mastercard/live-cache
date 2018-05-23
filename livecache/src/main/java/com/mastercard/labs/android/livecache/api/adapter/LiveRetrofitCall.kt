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

package com.mastercard.labs.android.livecache.api.adapter

import com.mastercard.labs.android.livecache.api.ApiResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.CountDownLatch

/**
 *
 * @author ech0s7r
 */
class LiveRetrofitCall<ApiResultType>(private val retrofitCall: LiveRetrofitCall<ApiResultType>.() -> Unit) {

    private val latch = CountDownLatch(1)
    private var throwable: Throwable? = null
    private var response: Response<ApiResultType>? = null

    val callback: Callback<ApiResultType> = object : Callback<ApiResultType> {
        override fun onFailure(call: Call<ApiResultType>?, t: Throwable) {
            throwable = t
            latch.countDown()
        }

        override fun onResponse(call: Call<ApiResultType>?, res: Response<ApiResultType>) {
            response = res
            latch.countDown()
        }

    }

    fun createCall(): ApiResponse<ApiResultType> {
        retrofitCall.invoke(this)
        latch.await()
        if (response != null) {
            return response!! as ApiResponse<ApiResultType>
        } else {
            throw throwable!!
        }
    }

}