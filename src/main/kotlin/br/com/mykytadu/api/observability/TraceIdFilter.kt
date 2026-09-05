package br.com.mykytadu.api.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = UUID.randomUUID().toString()
        MDC.put(TRACE_ID, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID)
        }
    }

    companion object {
        const val TRACE_ID = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }
}
