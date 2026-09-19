package br.com.mykytadu.api.error

import org.springframework.http.HttpStatus

class ApiProblemException(val status: HttpStatus, val code: ProblemCode, val retryAfterSeconds: Long? = null) :
    RuntimeException(code.defaultTitle)
