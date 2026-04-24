package com.ncf.demo.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MqttConfig.MqttGateway mqttGateway;

    @Value("${app.upload.base-dir}")
    private String uploadBaseDir;

    @BeforeEach
    void cleanUploadDirectory() throws IOException {
        Path dir = Paths.get(uploadBaseDir);
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(dir))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
            }
        }
        Files.createDirectories(dir);
    }

    @Test
    void uploadEndpointStoresImageAndReturnsPublicUrl() throws Exception {
        String token = jwtService.generate(2001L, "CAREGIVER", 3001L);
        byte[] contentBytes = "fake-image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "visit.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                contentBytes
        );

        MvcResult result = mockMvc.perform(multipart("/api/mini/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(containsString("/uploads/miniapp/")))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String url = body.get("data").asText();
        String publicPath = URI.create(url).getPath();

        mockMvc.perform(get(publicPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(contentBytes));

        try (Stream<Path> files = Files.list(Paths.get(uploadBaseDir))) {
            assertThat(files.findAny()).isPresent();
        }
    }

    @Test
    void uploadEndpointRejectsNonImageFile() throws Exception {
        String token = jwtService.generate(2001L, "CAREGIVER", 3001L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "bad".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/mini/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("Only jpg, jpeg, png, and webp images are supported"));
    }
}
