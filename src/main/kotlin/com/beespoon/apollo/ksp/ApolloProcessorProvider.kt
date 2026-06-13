package com.beespoon.apollo.ksp

import com.google.devtools.ksp.processing.*

public class ApolloProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ApolloProcessor(environment.codeGenerator, environment.logger, ProcessorOptions.from(environment.options))
}
