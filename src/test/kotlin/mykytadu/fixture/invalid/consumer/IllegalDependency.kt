package mykytadu.fixture.invalid.consumer

import mykytadu.fixture.invalid.forbidden.ForbiddenApi

class IllegalDependency(
    private val forbiddenApi: ForbiddenApi,
)
