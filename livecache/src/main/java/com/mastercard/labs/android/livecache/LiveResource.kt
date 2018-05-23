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
import com.mastercard.labs.android.livecache.api.ApiResponse
import com.mastercard.labs.android.livecache.api.ResponseHandler
import com.mastercard.labs.android.livecache.api.adapter.LiveRetrofitCall
import com.mastercard.labs.android.livecache.cache.CachePolicy
import com.mastercard.labs.android.livecache.concurrent.IAppExecutors
import com.mastercard.labs.android.livecache.core.LiveDataResource
import com.mastercard.labs.android.livecache.lifecycle.AbsentLiveData
import com.mastercard.labs.android.livecache.utils.Resource

/**
 *
 * @author ech0s7r
 */
open class LiveResource<T, M> internal constructor(private val isStorable: Boolean = false) {

    constructor(appExecutors: IAppExecutors, isStorable: Boolean, preload: Boolean,
                remoteCall: () -> ApiResponse<T>, responseClass: Class<T>) : this(isStorable) {
        this.appExecutors = appExecutors
        this.remoteCall = remoteCall
        this.responseClass = responseClass
        this.preload = preload
    }

    protected constructor(appExecutors: IAppExecutors,
                          isStorable: Boolean,
                          preload: Boolean,
                          remoteCall: () -> ApiResponse<T>,
                          responseClass: Class<T>,
                          apiResponseHandler: ResponseHandler<T, M>?) : this(appExecutors, isStorable, preload, remoteCall, responseClass) {
        this.apiResponseHandler = apiResponseHandler
    }


    constructor(isStorable: Boolean,
                requestRetrofit: LiveRetrofitCall<T>.() -> Unit, responseClass: Class<T>) : this(isStorable) {
        this.retrofitCall = requestRetrofit
        this.responseClass = responseClass
    }

    private var mapper: ((t: T) -> M)? = null

    private var onResult: ((M) -> Unit)? = null

    private var remoteCall: (() -> ApiResponse<T>)? = null

    private var retrofitCall: (LiveRetrofitCall<T>.() -> Unit)? = null

    private var cachePolicy: CachePolicy<M>? = null

    private var responseClass: Class<T>? = null

    private var preload: Boolean = false

    private var appExecutors: IAppExecutors? = null

    private var apiResponseHandler: ResponseHandler<T, M>? = null

    private val liveResource by lazy {
        object : LiveDataResource<M, T>(appExecutors!!, isStorable, preload) {
            override fun createCall(): ApiResponse<T> {
                return if (remoteCall != null) {
                    remoteCall?.invoke()!!
                } else {
                    LiveRetrofitCall(retrofitCall!!).createCall()
                }
            }

            override fun storeData(data: M) {
                cachePolicy?.saveCbk?.invoke(data)
            }

            override fun isCacheValid(data: M?): Boolean {
                return cachePolicy?.isValidCbk?.invoke(data) ?: false
            }

            override fun loadFromDb(): LiveData<M> {
                return if (cachePolicy?.loadCbk != null) {
                    cachePolicy?.loadCbk?.invoke() ?: AbsentLiveData.new()
                } else {
                    super.loadFromDb()
                }
            }

            override fun processResult(result: M) {
                onResult?.invoke(result)
            }

            override fun processFailure(e: Throwable?): Resource.Error<M> {
                return apiResponseHandler?.onFailure(e) ?: Resource.Error(e)
            }

            override fun handleResponse(call: ApiResponse<T>) {
                apiResponseHandler?.onSuccess(call)
            }

            @Suppress("UNCHECKED_CAST")
            override fun convertResponse(call: ApiResponse<T>): M? {
                return mapper?.invoke(call.body()!!) ?: call.body() as M
            }

            override fun handleCacheResponse(result: M?) {
                if (cachePolicy?.isValidCbk?.invoke(result) == true) {
                    apiResponseHandler?.onCacheSuccess(result)
                } else {
                    super.handleCacheResponse(result)
                }
            }
        }
    }

    /**
     * Set a mapper for the web services data
     */
    fun mapper(mapper: (t: T) -> M): LiveResource<T, M> {
        this.mapper = mapper
        return this
    }

    /**
     * Handle result data, after mapping
     */
    fun onResult(onResult: (M) -> Unit): LiveResource<T, M> {
        this.onResult = onResult
        return this
    }

    /**
     * Define a cache policy for the resource
     */
    fun setCachePolicy(cachePolicy: CachePolicy<M>): LiveResource<T, M> {
        this.cachePolicy = cachePolicy
        return this
    }

    /**
     * Return the stream as Repository Resource
     */
    fun asLiveData(): LiveData<Resource<M>> = liveResource.asLiveData()

    companion object {

        /**
         * Use to fetch a resource remotely (or from the cache), this function should to be used from Repository classes
         *
         * @param isStorable true if the resource can be stored
         * @param preload true if the observer would be notified on preload event
         * @param remoteCall Remote call to perform. The remote call is executed in a worker thread.
         */
        inline fun <reified T, M> fetch(appExecutors: IAppExecutors,
                                        isStorable: Boolean = false,
                                        preload: Boolean = false,
                                        noinline remoteCall: () -> ApiResponse<T>): LiveResource<T, M> {
            return LiveResource(appExecutors,
                    isStorable = isStorable,
                    preload = preload,
                    remoteCall = remoteCall,
                    responseClass = T::class.java)
        }

        inline fun <reified T : ApiResponse<T>, M> fetchLive(isStorable: Boolean = false,
                                                             noinline requestRetrofit: LiveRetrofitCall<T>.() -> Unit): LiveResource<T, M> {
            return LiveResource(isStorable = isStorable,
                    requestRetrofit = requestRetrofit,
                    responseClass = T::class.java)
        }

    }

}
