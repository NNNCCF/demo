package com.ncf.demo.web;

import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Family;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppGuardianServiceNewsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NewsPostRepository newsPostRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @MockBean
    private MqttConfig.MqttGateway mqttGateway;

    @BeforeEach
    void setUp() {
        newsPostRepository.deleteAll();
        wardRepository.deleteAll();
        deviceRepository.deleteAll();
        familyRepository.deleteAll();
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void guardianNewsListReturnsOnlyVisiblePostsWithPageFields() throws Exception {
        Organization organization = createOrganization("Service News Org", "No. 20 Service Road", "03510000201");
        ClientUser guardian = createGuardian("Guardian News", "13800000201");
        ClientUser otherGuardian = createGuardian("Guardian Hidden", "13800000202");
        Family guardianFamily = createFamily("Family Visible", "Room 901", organization, guardian);
        Family otherFamily = createFamily("Family Hidden", "Room 902", organization, otherGuardian);

        NewsPost allPost = createNewsPost(
                "Global Notice",
                "Visible to all guardians",
                "ALL",
                null,
                "Publisher A",
                Instant.parse("2026-04-20T01:00:00Z"),
                Instant.parse("2026-04-20T01:00:00Z"),
                "[\"https://example.com/news/all.jpg\"]");
        NewsPost familyPost = createNewsPost(
                "Family Notice",
                "Visible to the current guardian family",
                "FAMILY",
                guardianFamily,
                "Publisher B",
                Instant.parse("2026-04-21T01:00:00Z"),
                Instant.parse("2026-04-21T01:00:00Z"),
                "[\"https://example.com/news/family-1.jpg\",\"https://example.com/news/family-2.jpg\"]");
        createNewsPost(
                "Hidden Notice",
                "Should not leak to other guardians",
                "FAMILY",
                otherFamily,
                "Publisher C",
                Instant.parse("2026-04-22T01:00:00Z"),
                Instant.parse("2026-04-22T01:00:00Z"),
                "[\"https://example.com/news/hidden.jpg\"]");

        mockMvc.perform(get("/api/news")
                        .header("Authorization", "Bearer " + guardianToken(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(familyPost.getId()))
                .andExpect(jsonPath("$.data[0].title").value("Family Notice"))
                .andExpect(jsonPath("$.data[0].publisherName").value("Publisher B"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-04-21T01:00:00Z"))
                .andExpect(jsonPath("$.data[0].attachments[0]").value("https://example.com/news/family-1.jpg"))
                .andExpect(jsonPath("$.data[0].attachments[1]").value("https://example.com/news/family-2.jpg"))
                .andExpect(jsonPath("$.data[1].id").value(allPost.getId()))
                .andExpect(jsonPath("$.data[1].title").value("Global Notice"))
                .andExpect(jsonPath("$.data[1].publisherName").value("Publisher A"))
                .andExpect(jsonPath("$.data[1].createdAt").value("2026-04-20T01:00:00Z"))
                .andExpect(jsonPath("$.data[1].attachments[0]").value("https://example.com/news/all.jpg"));
    }

    @Test
    void guardianNewsDetailAllowsVisiblePostAndBlocksForeignFamilyPost() throws Exception {
        Organization organization = createOrganization("Detail News Org", "No. 21 Service Road", "03510000211");
        ClientUser guardian = createGuardian("Guardian Detail", "13800000211");
        ClientUser otherGuardian = createGuardian("Guardian Blocked", "13800000212");
        Family guardianFamily = createFamily("Family Detail", "Room 1001", organization, guardian);
        Family otherFamily = createFamily("Family Blocked", "Room 1002", organization, otherGuardian);

        NewsPost visiblePost = createNewsPost(
                "Visible Detail",
                "Detail page content for current guardian",
                "FAMILY",
                guardianFamily,
                "Publisher Detail",
                Instant.parse("2026-04-23T01:00:00Z"),
                Instant.parse("2026-04-23T01:00:00Z"),
                "https://example.com/news/detail.jpg");
        NewsPost hiddenPost = createNewsPost(
                "Hidden Detail",
                "Must not be visible to current guardian",
                "FAMILY",
                otherFamily,
                "Publisher Hidden",
                Instant.parse("2026-04-24T01:00:00Z"),
                Instant.parse("2026-04-24T01:00:00Z"),
                "[\"https://example.com/news/hidden.jpg\"]");

        mockMvc.perform(get("/api/news/{id}", visiblePost.getId())
                        .header("Authorization", "Bearer " + guardianToken(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(visiblePost.getId()))
                .andExpect(jsonPath("$.data.title").value("Visible Detail"))
                .andExpect(jsonPath("$.data.content").value("Detail page content for current guardian"))
                .andExpect(jsonPath("$.data.publisherName").value("Publisher Detail"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-04-23T01:00:00Z"))
                .andExpect(jsonPath("$.data.attachments[0]").value("https://example.com/news/detail.jpg"));

        mockMvc.perform(get("/api/news/{id}", hiddenPost.getId())
                        .header("Authorization", "Bearer " + guardianToken(guardian)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
    }

    private Organization createOrganization(String name, String address, String phone) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setType(OrgType.MEDICAL_INSTITUTION);
        organization.setRegion("Taiyuan");
        organization.setAddress(address);
        organization.setContactPhone(phone);
        return organizationRepository.save(organization);
    }

    private ClientUser createGuardian(String name, String mobile) {
        ClientUser guardian = new ClientUser();
        guardian.setName(name);
        guardian.setMobile(mobile);
        guardian.setPassword("encoded");
        guardian.setRole(ClientUserRole.GUARDIAN);
        return clientUserRepository.save(guardian);
    }

    private Family createFamily(String name, String address, Organization organization, ClientUser guardian) {
        Family family = new Family();
        family.setName(name);
        family.setAddress(address);
        family.setOrgId(organization.getId());
        family.setGuardians(List.of(guardian));
        return familyRepository.save(family);
    }

    private NewsPost createNewsPost(String title,
                                    String content,
                                    String targetScope,
                                    Family targetFamily,
                                    String publisherName,
                                    Instant createdAt,
                                    Instant publishTime,
                                    String attachments) {
        NewsPost post = new NewsPost();
        post.setTitle(title);
        post.setContent(content);
        post.setVisibility("ALL");
        post.setCategory("NOTICE");
        post.setTargetScope(targetScope);
        post.setTargetFamilyId(targetFamily != null ? targetFamily.getId() : null);
        post.setTargetFamilyName(targetFamily != null ? targetFamily.getName() : null);
        post.setPublisherName(publisherName);
        post.setPublishTime(publishTime);
        post.setAttachments(attachments);
        post.setCreatedAt(createdAt);
        return newsPostRepository.save(post);
    }

    private String guardianToken(ClientUser guardian) {
        return jwtService.generate(guardian.getId(), "GUARDIAN", null);
    }
}
