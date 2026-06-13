package com.beespoon.apollo.reference

import com.beespoon.apollo.api.*

internal class MappedConfigReference<T : Any, U : Any>(
    private val source: ConfigReference<T>,
    private val transform: (T) -> U
) : ConfigReference<U> {
    @Volatile
    private var cached: Any? = UNSET
    private val lock = Any()
    override val value: U
        get() {
            if (cached === UNSET) synchronized(lock) {
                if (cached === UNSET) {
                    source.listen { cached = transform(it) }
                    if (cached === UNSET) cached = transform(source.value)
                }
            }
            @Suppress("UNCHECKED_CAST")
            return cached as U
        }
    override val state: ConfigState<U>
        get() = when (val state = source.state) {
            is ConfigState.Loaded -> ConfigState.Loaded(transform(state.value))
            is ConfigState.Error -> ConfigState.Error(state.exception, state.lastValue?.let(transform))
            is ConfigState.Loading -> ConfigState.Loading
        }

    override fun listen(listener: ConfigListener<U>): ConfigSubscription {
        var last: Any? = UNSET
        return source.listen { newValue ->
            val projected = transform(newValue)
            if (projected != last) {
                last = projected
                listener.onReload(projected)
            }
        }
    }

    override fun poll(): U = transform(source.poll())

    private companion object {
        private val UNSET = Any()
    }
}
