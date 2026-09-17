package mykytadu.fixture.invalid.consumer

import mykytadu.fixture.invalid.forbidden.internal.ForbiddenApi

class IllegalDependency(private val forbiddenApi: ForbiddenApi)
