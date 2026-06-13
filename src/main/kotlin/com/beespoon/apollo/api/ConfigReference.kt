package com.beespoon.apollo.api

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import com.beespoon.apollo.reference.MappedConfigReference

public interface ConfigReference<T : Any> : ReadOnlyProperty<Any?, T> {
    public val value: T
    public val state: ConfigState<T>
    public override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    public fun listen(listener: ConfigListener<T>): ConfigSubscription
    public fun <U : Any> map(transform: (T) -> U): ConfigReference<U> = MappedConfigReference(this, transform)
    public fun poll(): T
}
