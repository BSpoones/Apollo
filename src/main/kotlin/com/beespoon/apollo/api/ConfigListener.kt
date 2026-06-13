package com.beespoon.apollo.api

public fun interface ConfigListener<T> {
    public fun onReload(value: T)
}
