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
import android.arch.lifecycle.LiveData
import com.mastercard.labs.android.livecache.utils.*

/**
 * Helper class used to request repository resources
 *
 * @param T Data type
 * @param E Error type
 *
 * @author ech0s7r
 */
open class RepoRequest<T, E : Resource.Error<T>>(private val lifecycle: Lifecycle,
                                                 private val request: () -> LiveData<Resource<T>>) {

    private var loadingCbk: (() -> Unit)? = null
    private var preloadCbk: ((T) -> Unit)? = null
    private var successCbk: ((T) -> Unit)? = null
    private var errorCbk: ((E) -> Unit)? = null

    fun onLoading(onLoading: () -> Unit): RepoRequest<T, E> {
        loadingCbk = onLoading
        return this
    }

    fun onPreload(onPreload: (T) -> Unit): RepoRequest<T, E> {
        preloadCbk = onPreload
        return this
    }

    fun onSuccess(onSuccess: (T) -> Unit): RepoRequest<T, E> {
        successCbk = onSuccess
        return this
    }

    fun onError(onError: (E) -> Unit): RepoRequest<T, E> {
        errorCbk = onError
        return this
    }

    fun execute() {
        val result = request()
        result.observe({ lifecycle }) {
            when (it) {
                is Loading -> loadingCbk?.invoke()
                is Preload -> preloadCbk?.invoke(it.data)
                is Success -> successCbk?.invoke(it.data)
                is Error -> errorCbk?.invoke((it as E))
            }
        }
    }

}