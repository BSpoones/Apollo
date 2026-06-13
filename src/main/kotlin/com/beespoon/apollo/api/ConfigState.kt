package com.beespoon.apollo.api

public sealed interface ConfigState<out T : Any> {
    public data class Loaded<T : Any>(val value: T) : ConfigState<T>
    public data class Error<T : Any>(val exception: Throwable, val lastValue: T? = null) : ConfigState<T>
    public data object Loading : ConfigState<Nothing>
}
