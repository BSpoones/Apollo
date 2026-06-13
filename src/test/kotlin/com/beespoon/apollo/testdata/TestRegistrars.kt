package com.beespoon.apollo.testdata
import com.beespoon.apollo.*
class FailingRegistrar : ApolloRegistrar {
    override fun register(builder: ApolloBuilder): Unit = error("boom")
}
class ServiceRegistrar : ApolloRegistrar {
    override fun register(builder: ApolloBuilder) {
        invocations++
        builder.register<SimpleConfig>(name = "from-registrar", namespace = "svc")
    }
    companion object { var invocations = 0 }
}
class SecondaryServiceRegistrar : ApolloRegistrar {
    override fun register(builder: ApolloBuilder) { builder.register<Standalone>(namespace = "svc2") }
}
