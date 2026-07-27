package com.dongqh.luckyhub.prize.controller;

import com.dongqh.luckyhub.common.web.GlobalExceptionHandler;
import com.dongqh.luckyhub.prize.image.PrizeImageService;
import com.dongqh.luckyhub.prize.vo.ImageUploadView;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrizeImageControllerTests {

    private PrizeImageService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PrizeImageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PrizeImageController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadsPrizeImage() throws Exception {
        when(service.upload(any())).thenReturn(new ImageUploadView(
                "https://cdn.example.com/prizes/2026/07/image.png",
                "prizes/2026/07/image.png"
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file", "prize.png", "image/png", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/admin/prize-images").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url")
                        .value("https://cdn.example.com/prizes/2026/07/image.png"))
                .andExpect(jsonPath("$.data.objectKey")
                        .value("prizes/2026/07/image.png"));
    }

    @Test
    void requiresImageUploadPermission() throws NoSuchMethodException {
        var method = PrizeImageController.class.getMethod(
                "upload",
                org.springframework.web.multipart.MultipartFile.class
        );

        assertThat(method.getAnnotation(RequirePermission.class).value())
                .isEqualTo(PermissionCodes.PRIZE_IMAGE_UPLOAD);
    }
}
