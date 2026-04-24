package com.ncf.demo.web;

import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.NewsPost;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.NewsPostRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NewsPostControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NewsPostRepository newsPostRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @MockBean
    private MqttConfig.MqttGateway mqttGateway;

    private Organization organization;
    private ClientUser institutionUser;
    private ClientUser caregiverUser;

    @BeforeEach
    void setUp() {
        newsPostRepository.deleteAll();
        wardRepository.deleteAll();
        deviceRepository.deleteAll();
        familyRepository.deleteAll();
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();

        organization = new Organization();
        organization.setName("News Org");
        organization.setType(OrgType.MEDICAL_INSTITUTION);
        organization.setRegion("Taiyuan");
        organization.setAddress("No. 2 Health Road");
        organization.setContactPhone("03510000001");
        organization = organizationRepository.save(organization);

        institutionUser = new ClientUser();
        institutionUser.setName("Institution Publisher");
        institutionUser.setMobile("13800000111");
        institutionUser.setPassword("encoded");
        institutionUser.setRole(ClientUserRole.INSTITUTION);
        institutionUser.setOrgId(organization.getId());
        institutionUser = clientUserRepository.save(institutionUser);

        caregiverUser = new ClientUser();
        caregiverUser.setName("Caregiver Publisher");
        caregiverUser.setMobile("13800000112");
        caregiverUser.setPassword("encoded");
        caregiverUser.setRole(ClientUserRole.CAREGIVER);
        caregiverUser.setOrgId(organization.getId());
        caregiverUser = clientUserRepository.save(caregiverUser);
    }

    @Test
    void institutionCanCreateNewsAndResponsesExposeAttachmentArray() throws Exception {
        String token = jwtService.generate(institutionUser.getId(), "INSTITUTION", organization.getId());

        mockMvc.perform(post("/api/news")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Health Visit Notice",
                                  "content": "Home visit updates",
                                  "visibility": "ALL",
                                  "category": "NOTICE",
                                  "targetScope": "ALL",
                                  "attachments": [
                                    "https://example.com/uploads/news/a.jpg",
                                    "https://example.com/uploads/news/b.jpg"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attachments[0]").value("https://example.com/uploads/news/a.jpg"))
                .andExpect(jsonPath("$.data.attachments[1]").value("https://example.com/uploads/news/b.jpg"));

        NewsPost saved = newsPostRepository.findAll().stream().findFirst().orElseThrow();
        assertThat(saved.getAttachments()).isEqualTo(
                "[\"https://example.com/uploads/news/a.jpg\",\"https://example.com/uploads/news/b.jpg\"]");

        mockMvc.perform(get("/api/news/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attachments[0]").value("https://example.com/uploads/news/a.jpg"))
                .andExpect(jsonPath("$.data.attachments[1]").value("https://example.com/uploads/news/b.jpg"));

        mockMvc.perform(get("/api/news")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].attachments[0]").value("https://example.com/uploads/news/a.jpg"))
                .andExpect(jsonPath("$.data[0].attachments[1]").value("https://example.com/uploads/news/b.jpg"));
    }

    @Test
    void getByIdWrapsLegacyPlainAttachmentStringIntoArray() throws Exception {
        String token = jwtService.generate(institutionUser.getId(), "INSTITUTION", organization.getId());

        NewsPost post = new NewsPost();
        post.setTitle("Legacy Attachment News");
        post.setContent("Legacy record");
        post.setVisibility("ALL");
        post.setCategory("NOTICE");
        post.setTargetScope("ALL");
        post.setPublisherId(institutionUser.getId());
        post.setPublisherName(institutionUser.getName());
        post.setPublishTime(Instant.parse("2026-05-01T00:00:00Z"));
        post.setAttachments("https://example.com/uploads/news/legacy.jpg");
        post = newsPostRepository.save(post);

        mockMvc.perform(get("/api/news/{id}", post.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attachments[0]").value("https://example.com/uploads/news/legacy.jpg"));
    }

    @Test
    void caregiverCanCreateNewsForStaffPublishPage() throws Exception {
        String token = jwtService.generate(caregiverUser.getId(), "CAREGIVER", organization.getId());

        mockMvc.perform(post("/api/news")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Caregiver Notice",
                                  "content": "Caregiver update for service follow-up",
                                  "category": "health",
                                  "targetScope": "ALL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Caregiver Notice"))
                .andExpect(jsonPath("$.data.publisherName").value("Caregiver Publisher"));
    }
}
