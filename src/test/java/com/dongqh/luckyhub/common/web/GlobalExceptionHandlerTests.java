package com.dongqh.luckyhub.common.web;

import com.dongqh.luckyhub.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTests.TestController.class)
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void translatesBeanValidationFailure() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30000))
                .andExpect(jsonPath("$.message").value("name: 名称不能为空"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists(RequestIdSupport.HEADER_NAME));
    }

    @Test
    void translatesMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30001))
                .andExpect(jsonPath("$.message").value("请求内容格式错误"));
    }

    @Test
    void translatesBusinessExceptionAndKeepsRequestId() throws Exception {
        mockMvc.perform(get("/test/not-found")
                        .header(RequestIdSupport.HEADER_NAME, "business-request-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30002))
                .andExpect(jsonPath("$.message").value("奖品不存在"))
                .andExpect(jsonPath("$.requestId").value("business-request-1"));
    }

    @Test
    void hidesUnknownExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(10000))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-detail"))));
    }

    @Test
    void translatesMissingEndpointToNotFound() throws Exception {
        mockMvc.perform(get("/path-that-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30002))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("奖品不存在");
        }

        @GetMapping("/unknown")
        void unknown() {
            throw new IllegalStateException("secret-detail");
        }
    }

    record TestRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}
