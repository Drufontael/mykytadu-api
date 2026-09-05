package br.com.mykytadu.api.error

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(ProblemDetailsFixtureController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler::class, ApiProblemFactory::class)
@ActiveProfiles("test")
class ApiExceptionHandlerTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `returns field violations as problem details`() {
        mockMvc.post("/test/problem-details/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":""}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:mykytadu:problem:request_validation_failed") }
            jsonPath("$.title") { value("Request validation failed") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.detail") { value("One or more fields are invalid.") }
            jsonPath("$.instance") { value("/test/problem-details/validation") }
            jsonPath("$.code") { value("request_validation_failed") }
            jsonPath("$.traceId") { isString() }
            jsonPath("$.errors") { isArray() }
            jsonPath("$.errors[0].field") { value("name") }
            jsonPath("$.errors[0].code") { value("invalid") }
            jsonPath("$.errors[1]") { doesNotExist() }
        }
    }

    @Test
    fun `hides implementation details from unexpected errors`() {
        val response = mockMvc.get("/test/problem-details/unexpected")
            .andExpect {
                status { isInternalServerError() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("internal_error") }
                jsonPath("$.traceId") { isString() }
                jsonPath("$.errors") { isEmpty() }
            }
            .andReturn()
            .response

        assertThat(response.contentAsString)
            .doesNotContain("sensitive-detail", "stackTrace", "exception")
    }
}

@RestController
@RequestMapping("/test/problem-details")
private class ProblemDetailsFixtureController {

    @PostMapping("/validation")
    fun validation(@Valid @RequestBody request: FixtureRequest) = request

    @GetMapping("/unexpected")
    fun unexpected(): Nothing = error("sensitive-detail")

    data class FixtureRequest(@field:NotBlank val name: String?)
}
