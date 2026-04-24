# Miniapp Backend Contract Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Spring Boot backend fully match the current miniapp contract for image upload, institution news publishing, news attachment rendering, appointment dispatch, and visit-record submission without requiring any miniapp rollback.

**Architecture:** Keep the existing `auth`, `mini`, and `news` route structure intact, then add missing behavior by extending configuration, security rules, persistence fields, `MiniAppController` request handling, and `NewsPostController` response mapping. Use integration tests for controller contracts and a focused news API test so the backend is locked to the miniapp's current request/response format.

**Tech Stack:** Spring Boot 3.3, Spring MVC, Spring Security, Spring Data JPA, Jackson, H2 test profile, MockMvc, JUnit 5

---

### File Structure

**Files:**
- Create: `demo/src/main/java/com/ncf/demo/config/UploadResourceConfig.java`
- Create: `demo/src/test/java/com/ncf/demo/web/MiniAppUploadIntegrationTest.java`
- Create: `demo/src/test/java/com/ncf/demo/web/MiniAppAppointmentIntegrationTest.java`
- Create: `demo/src/test/java/com/ncf/demo/web/NewsPostControllerIntegrationTest.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/AppProperties.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/NewsPostController.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/MiniAppController.java`
- Modify: `demo/src/main/resources/application.properties`
- Modify: `demo/src/test/resources/application-test.properties`

**Responsibilities:**
- `demo/src/main/java/com/ncf/demo/config/AppProperties.java`: define upload storage and public URL properties in one place.
- `demo/src/main/java/com/ncf/demo/config/UploadResourceConfig.java`: expose the upload directory as a public static resource path under `/uploads/**`.
- `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`: permit anonymous reads for `/uploads/**` and allow institution users to create miniapp news posts.
- `demo/src/main/java/com/ncf/demo/web/NewsPostController.java`: convert stored attachment JSON strings into response arrays so miniapp detail pages can render images directly.
- `demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java`: persist visit photo URLs in a dedicated JSON text column.
- `demo/src/main/java/com/ncf/demo/web/MiniAppController.java`: add `/api/mini/upload`, accept `visitDate`, parse second-precision `visitTime`, and persist `visitPhotos`.
- `demo/src/main/resources/application.properties`: configure upload path, public path, and multipart limits for runtime.
- `demo/src/test/resources/application-test.properties`: isolate uploads into `build/test-uploads` during tests.
- `demo/src/test/java/com/ncf/demo/web/MiniAppUploadIntegrationTest.java`: lock down upload success, public file serving, and invalid file rejection.
- `demo/src/test/java/com/ncf/demo/web/MiniAppAppointmentIntegrationTest.java`: lock down dispatch date storage and visit-record time/photo persistence.
- `demo/src/test/java/com/ncf/demo/web/NewsPostControllerIntegrationTest.java`: lock down institution news publishing authorization plus attachment JSON persistence.

### Task 1: Add Public Miniapp Upload Storage

**Files:**
- Create: `demo/src/main/java/com/ncf/demo/config/UploadResourceConfig.java`
- Create: `demo/src/test/java/com/ncf/demo/web/MiniAppUploadIntegrationTest.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/AppProperties.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/MiniAppController.java`
- Modify: `demo/src/main/resources/application.properties`
- Modify: `demo/src/test/resources/application-test.properties`

- [ ] **Step 1: Write the failing upload integration tests**

```java
package com.ncf.demo.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("/uploads/miniapp/")))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String url = body.get("data").asText();
        String publicPath = java.net.URI.create(url).getPath();

        mockMvc.perform(get(publicPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(contentBytes));

        assertThat(Files.list(Paths.get(uploadBaseDir)).findAny()).isPresent();
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
```

- [ ] **Step 2: Run the upload tests to verify they fail**

Run: `./gradlew test --tests "com.ncf.demo.web.MiniAppUploadIntegrationTest"`

Expected: FAIL because `/api/mini/upload` does not exist and `/uploads/**` is not publicly served

- [ ] **Step 3: Add upload properties, public resource mapping, and anonymous read access**

```java
// demo/src/main/java/com/ncf/demo/config/AppProperties.java
private final Upload upload = new Upload();

public Upload getUpload() {
    return upload;
}

public static class Upload {
    private String baseDir = "./data/uploads";
    private String publicPath = "/uploads";
    private String publicBaseUrl = "";

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
```

```java
// demo/src/main/java/com/ncf/demo/config/UploadResourceConfig.java
package com.ncf.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public UploadResourceConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path baseDir = Paths.get(appProperties.getUpload().getBaseDir()).toAbsolutePath().normalize();
        String publicPath = appProperties.getUpload().getPublicPath();
        String pattern = publicPath.endsWith("/**") ? publicPath : publicPath + "/**";
        String location = baseDir.toUri().toString();
        registry.addResourceHandler(pattern).addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
```

```java
// demo/src/main/java/com/ncf/demo/config/SecurityConfig.java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(publicEndpoints.toArray(String[]::new)).permitAll()
        .requestMatchers("/uploads/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/news/**").authenticated()
        .requestMatchers(HttpMethod.GET, "/api/service-orders/**").hasAnyRole("ADMIN", "GUARDIAN")
        .requestMatchers("/api/service-orders/**", "/api/news/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/organizations").authenticated()
        .requestMatchers("/api/mini/**").hasAnyRole("ADMIN", "NURSE", "DOCTOR", "GUARDIAN", "INSTITUTION", "CAREGIVER")
        .requestMatchers("/api/families/**", "/api/devices/**", "/api/alarms/**", "/api/data/**",
                "/api/guardian-targets/**", "/api/client-users/**", "/api/doctors/**").hasAnyRole("ADMIN", "GUARDIAN")
        .anyRequest().authenticated()
)
```

```properties
# demo/src/main/resources/application.properties
app.upload.base-dir=${UPLOAD_BASE_DIR:./data/uploads}
app.upload.public-path=${UPLOAD_PUBLIC_PATH:/uploads}
app.upload.public-base-url=${UPLOAD_PUBLIC_BASE_URL:}
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

```properties
# demo/src/test/resources/application-test.properties
app.upload.base-dir=./build/test-uploads
app.upload.public-path=/uploads
app.upload.public-base-url=
```

- [ ] **Step 4: Implement the miniapp upload endpoint in `MiniAppController`**

```java
// demo/src/main/java/com/ncf/demo/web/MiniAppController.java
private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

private final AppProperties appProperties;

public MiniAppController(
        AlarmRepository alarmRepo,
        WardRepository wardRepo,
        DeviceRepository deviceRepo,
        FamilyRepository familyRepo,
        ClientUserRepository clientUserRepo,
        UserRepository userRepo,
        DoctorRepository doctorRepo,
        ServiceOrderRepository orderRepo,
        HealthDataRepository healthDataRepo,
        OrganizationRepository orgRepo,
        NewsPostRepository newsRepo,
        GuardianAlarmSettingRepository guardianAlarmSettingRepo,
        FeedbackSubmissionRepository feedbackRepo,
        AlarmService alarmService,
        TdengineService tdengineService,
        AppProperties appProperties
) {
    this.alarmRepo = alarmRepo;
    this.wardRepo = wardRepo;
    this.deviceRepo = deviceRepo;
    this.familyRepo = familyRepo;
    this.clientUserRepo = clientUserRepo;
    this.userRepo = userRepo;
    this.doctorRepo = doctorRepo;
    this.orderRepo = orderRepo;
    this.healthDataRepo = healthDataRepo;
    this.orgRepo = orgRepo;
    this.newsRepo = newsRepo;
    this.guardianAlarmSettingRepo = guardianAlarmSettingRepo;
    this.feedbackRepo = feedbackRepo;
    this.alarmService = alarmService;
    this.tdengineService = tdengineService;
    this.appProperties = appProperties;
}

@PostMapping(path = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<String> uploadMiniappImage(@RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
    if (file.isEmpty()) {
        throw new BizException(4000, "Image file is required");
    }

    String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    String extension = org.springframework.util.StringUtils.getFilenameExtension(originalName);
    String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    if (!ALLOWED_UPLOAD_EXTENSIONS.contains(normalizedExtension)) {
        throw new BizException(4000, "Only jpg, jpeg, png, and webp images are supported");
    }

    try {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        Path root = Paths.get(appProperties.getUpload().getBaseDir()).toAbsolutePath().normalize();
        Path targetDir = root.resolve("miniapp").resolve(day).normalize();
        Files.createDirectories(targetDir);

        String filename = UUID.randomUUID() + "." + normalizedExtension;
        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(root)) {
            throw new BizException(4000, "Illegal upload path");
        }

        file.transferTo(targetFile);

        String baseUrl = org.springframework.util.StringUtils.hasText(appProperties.getUpload().getPublicBaseUrl())
                ? appProperties.getUpload().getPublicBaseUrl().replaceAll("/$", "")
                : org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
                        .build()
                        .toUriString();
        String publicPath = appProperties.getUpload().getPublicPath().replaceAll("/$", "");
        String relativePath = "/miniapp/" + day + "/" + filename;
        return ApiResponse.ok(baseUrl + publicPath + relativePath);
    } catch (IOException ex) {
        throw new BizException(5000, "Failed to store uploaded image");
    }
}
```

- [ ] **Step 5: Run the upload tests to verify they pass**

Run: `./gradlew test --tests "com.ncf.demo.web.MiniAppUploadIntegrationTest"`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ncf/demo/config/AppProperties.java src/main/java/com/ncf/demo/config/UploadResourceConfig.java src/main/java/com/ncf/demo/config/SecurityConfig.java src/main/java/com/ncf/demo/web/MiniAppController.java src/main/resources/application.properties src/test/resources/application-test.properties src/test/java/com/ncf/demo/web/MiniAppUploadIntegrationTest.java
git commit -m "feat: add miniapp upload endpoint"
```

### Task 2: Align Appointment Dispatch And Visit Record Contracts

**Files:**
- Create: `demo/src/test/java/com/ncf/demo/web/MiniAppAppointmentIntegrationTest.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/MiniAppController.java`

- [ ] **Step 1: Write the failing appointment contract integration tests**

```java
package com.ncf.demo.web;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.ServiceOrderRepository;
import com.ncf.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppAppointmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private FamilyRepository familyRepository;

    private Organization organization;
    private ClientUser institutionUser;
    private ClientUser caregiver;
    private ClientUser guardian;
    private Family family;

    @BeforeEach
    void setUp() {
        serviceOrderRepository.deleteAll();
        familyRepository.deleteAll();
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();

        organization = new Organization();
        organization.setName("卓凯机构");
        organization.setType(OrgType.MEDICAL_INSTITUTION);
        organization.setRegion("太原");
        organization.setAddress("太原市迎泽区");
        organization.setContactPhone("03510000000");
        organization = organizationRepository.save(organization);

        institutionUser = new ClientUser();
        institutionUser.setName("机构管理员");
        institutionUser.setMobile("13800000001");
        institutionUser.setPassword("encoded");
        institutionUser.setRole(ClientUserRole.INSTITUTION);
        institutionUser.setOrgId(organization.getId());
        institutionUser = clientUserRepository.save(institutionUser);

        caregiver = new ClientUser();
        caregiver.setName("护理员王敏");
        caregiver.setMobile("13800000002");
        caregiver.setPassword("encoded");
        caregiver.setRole(ClientUserRole.CAREGIVER);
        caregiver.setOrgId(organization.getId());
        caregiver = clientUserRepository.save(caregiver);

        guardian = new ClientUser();
        guardian.setName("家属李华");
        guardian.setMobile("13800000003");
        guardian.setPassword("encoded");
        guardian.setRole(ClientUserRole.GUARDIAN);
        guardian = clientUserRepository.save(guardian);

        family = new Family();
        family.setName("迎泽苑一号");
        family.setAddress("迎泽苑 1 号楼");
        family.setOrgId(organization.getId());
        family.setGuardians(List.of(guardian));
        family = familyRepository.save(family);
    }

    @Test
    void dispatchStoresVisitDateAtStartOfDay() throws Exception {
        ServiceOrder order = new ServiceOrder();
        order.setOrgId(organization.getId());
        order.setFamilyId(family.getId());
        order.setGuardianId(guardian.getId());
        order.setCreatedById(institutionUser.getId());
        order.setDisplayType("上门护理");
        order.setStatus(ServiceOrderStatus.PENDING);
        order = serviceOrderRepository.save(order);

        mockMvc.perform(put("/api/mini/appointments/{id}/dispatch", order.getId())
                        .header("Authorization", "Bearer " + jwtService.generate(institutionUser.getId(), "INSTITUTION", organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nurseId": %d,
                                  "nurseName": "%s",
                                  "visitDate": "2026-05-01"
                                }
                                """.formatted(caregiver.getId(), caregiver.getName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ServiceOrder saved = serviceOrderRepository.findById(order.getId()).orElseThrow();
        Instant expected = LocalDate.of(2026, 5, 1)
                .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .toInstant();

        assertThat(saved.getAppointmentTime()).isEqualTo(expected);
        assertThat(saved.getNurseId()).isEqualTo(caregiver.getId());
        assertThat(saved.getNursePhone()).isEqualTo("13800000002");
        assertThat(saved.getDispatchedBy()).isEqualTo("卓凯机构");
    }

    @Test
    void visitRecordAcceptsSecondPrecisionTimeAndPersistsPhotos() throws Exception {
        ServiceOrder order = new ServiceOrder();
        order.setOrgId(organization.getId());
        order.setFamilyId(family.getId());
        order.setGuardianId(guardian.getId());
        order.setCreatedById(institutionUser.getId());
        order.setNurseId(caregiver.getId());
        order.setNurseName(caregiver.getName());
        order.setNursePhone(caregiver.getMobile());
        order.setDisplayType("上门护理");
        order.setStatus(ServiceOrderStatus.ACCEPTED);
        order = serviceOrderRepository.save(order);

        mockMvc.perform(post("/api/mini/appointments/{id}/visit-record", order.getId())
                        .header("Authorization", "Bearer " + jwtService.generate(caregiver.getId(), "CAREGIVER", organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visitTime": "2026-05-01 08:30:45",
                                  "remark": "服务完成",
                                  "payAmount": "199.00",
                                  "payStatus": "paid",
                                  "photos": [
                                    "https://example.com/uploads/miniapp/20260501/a.jpg",
                                    "https://example.com/uploads/miniapp/20260501/b.jpg"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ServiceOrder saved = serviceOrderRepository.findById(order.getId()).orElseThrow();
        Instant expected = LocalDateTime.of(2026, 5, 1, 8, 30, 45)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant();

        assertThat(saved.getVisitTime()).isEqualTo(expected);
        assertThat(saved.getStatus()).isEqualTo(ServiceOrderStatus.COMPLETED);
        assertThat(saved.getPayAmount()).isEqualTo("199.00");
        assertThat(saved.getPayStatus()).isEqualTo("paid");
        assertThat(saved.getVisitRemark()).isEqualTo("服务完成");
        assertThat(saved.getVisitPhotos()).isEqualTo("[\"https://example.com/uploads/miniapp/20260501/a.jpg\",\"https://example.com/uploads/miniapp/20260501/b.jpg\"]");
    }
}
```

- [ ] **Step 2: Run the appointment contract tests to verify they fail**

Run: `./gradlew test --tests "com.ncf.demo.web.MiniAppAppointmentIntegrationTest"`

Expected: FAIL because `visitDate` is ignored, second-precision `visitTime` is not parsed correctly, and `ServiceOrder` does not have a `visitPhotos` field

- [ ] **Step 3: Add `visitPhotos` persistence to `ServiceOrder`**

```java
// demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java
@Column(name = "visit_photos", columnDefinition = "TEXT")
private String visitPhotos;

public String getVisitPhotos() {
    return visitPhotos;
}

public void setVisitPhotos(String visitPhotos) {
    this.visitPhotos = visitPhotos;
}
```

- [ ] **Step 4: Update `MiniAppController` to accept `visitDate`, validate assigned caregivers, parse second-precision times, and save photo JSON**

```java
// demo/src/main/java/com/ncf/demo/web/MiniAppController.java
private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

record DispatchRequest(Long nurseId, String nurseName, String visitDate) {}
record VisitRecordRequest(String visitTime, String payAmount, String payStatus, String remark, List<String> photos) {}

public MiniAppController(
        AlarmRepository alarmRepo,
        WardRepository wardRepo,
        DeviceRepository deviceRepo,
        FamilyRepository familyRepo,
        ClientUserRepository clientUserRepo,
        UserRepository userRepo,
        DoctorRepository doctorRepo,
        ServiceOrderRepository orderRepo,
        HealthDataRepository healthDataRepo,
        OrganizationRepository orgRepo,
        NewsPostRepository newsRepo,
        GuardianAlarmSettingRepository guardianAlarmSettingRepo,
        FeedbackSubmissionRepository feedbackRepo,
        AlarmService alarmService,
        TdengineService tdengineService,
        AppProperties appProperties,
        com.fasterxml.jackson.databind.ObjectMapper objectMapper
) {
    this.alarmRepo = alarmRepo;
    this.wardRepo = wardRepo;
    this.deviceRepo = deviceRepo;
    this.familyRepo = familyRepo;
    this.clientUserRepo = clientUserRepo;
    this.userRepo = userRepo;
    this.doctorRepo = doctorRepo;
    this.orderRepo = orderRepo;
    this.healthDataRepo = healthDataRepo;
    this.orgRepo = orgRepo;
    this.newsRepo = newsRepo;
    this.guardianAlarmSettingRepo = guardianAlarmSettingRepo;
    this.feedbackRepo = feedbackRepo;
    this.alarmService = alarmService;
    this.tdengineService = tdengineService;
    this.appProperties = appProperties;
    this.objectMapper = objectMapper;
}

private Instant parseVisitDate(String visitDate) {
    if (visitDate == null || visitDate.isBlank()) {
        return null;
    }
    try {
        return LocalDate.parse(visitDate)
                .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .toInstant();
    } catch (Exception ex) {
        throw new BizException(4000, "visitDate format must be yyyy-MM-dd");
    }
}

private Instant parseVisitTime(String visitTime) {
    if (visitTime == null || visitTime.isBlank()) {
        return Instant.now();
    }
    List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );
    for (DateTimeFormatter formatter : formatters) {
        try {
            return LocalDateTime.parse(visitTime, formatter)
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toInstant();
        } catch (Exception ignored) {
        }
    }
    throw new BizException(4000, "visitTime format must be yyyy-MM-dd HH:mm:ss or yyyy-MM-dd HH:mm");
}

private String writeJsonArray(List<String> values) {
    try {
        return objectMapper.writeValueAsString(values == null ? List.of() : values);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        throw new BizException(5000, "Failed to serialize visit photos");
    }
}

@PutMapping("/appointments/{id}/dispatch")
public ApiResponse<Void> dispatchAppointment(@PathVariable Long id, @RequestBody DispatchRequest body) {
    ServiceOrder order = orderRepo.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new BizException(4004, "预约记录不存在"));

    ClientUser nurse = body.nurseId() == null ? null : clientUserRepo.findById(body.nurseId())
            .orElseThrow(() -> new BizException(4004, "护理人员不存在"));
    Long currentOrgId = SecurityUtil.currentOrgId();
    if (nurse != null && nurse.getRole() != ClientUserRole.CAREGIVER) {
        throw new BizException(4001, "指派对象不是护理人员");
    }
    if (nurse != null && currentOrgId != null && !currentOrgId.equals(nurse.getOrgId())) {
        throw new BizException(4003, "不能指派其他机构的护理人员");
    }

    String orgName = currentOrgId != null
            ? orgRepo.findById(currentOrgId).map(Organization::getName).orElse("机构")
            : "机构";

    order.setStatus(ServiceOrderStatus.PENDING);
    order.setNurseId(nurse != null ? nurse.getId() : body.nurseId());
    order.setNurseName(nurse != null ? nurse.getName() : body.nurseName());
    order.setNursePhone(nurse != null ? nurse.getMobile() : null);
    order.setAcceptTime(null);
    order.setDispatchedBy(orgName);
    if (body.visitDate() != null && !body.visitDate().isBlank()) {
        order.setAppointmentTime(parseVisitDate(body.visitDate()));
    }
    orderRepo.save(order);
    return ApiResponse.ok(null);
}

@PostMapping("/appointments/{id}/visit-record")
public ApiResponse<Void> submitVisitRecord(@PathVariable Long id, @RequestBody VisitRecordRequest body) {
    ServiceOrder order = orderRepo.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
    order.setVisitTime(parseVisitTime(body.visitTime()));
    order.setPayAmount(body.payAmount());
    order.setPayStatus(body.payStatus());
    order.setVisitRemark(body.remark());
    order.setVisitPhotos(writeJsonArray(body.photos()));
    order.setStatus(ServiceOrderStatus.COMPLETED);
    orderRepo.save(order);
    return ApiResponse.ok(null);
}
```

- [ ] **Step 5: Run the appointment contract tests to verify they pass**

Run: `./gradlew test --tests "com.ncf.demo.web.MiniAppAppointmentIntegrationTest"`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ncf/demo/entity/ServiceOrder.java src/main/java/com/ncf/demo/web/MiniAppController.java src/test/java/com/ncf/demo/web/MiniAppAppointmentIntegrationTest.java
git commit -m "feat: align miniapp appointment contracts"
```

### Task 3: Allow Institution Users To Publish Miniapp News

**Files:**
- Create: `demo/src/test/java/com/ncf/demo/web/NewsPostControllerIntegrationTest.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/NewsPostController.java`

- [ ] **Step 1: Write the failing news publishing integration test**

```java
package com.ncf.demo.web;

import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.NewsPost;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.NewsPostRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private FamilyRepository familyRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization organization;
    private ClientUser institutionUser;

    @BeforeEach
    void setUp() {
        newsPostRepository.deleteAll();
        familyRepository.deleteAll();
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();

        organization = new Organization();
        organization.setName("卓凯机构");
        organization.setType(OrgType.MEDICAL_INSTITUTION);
        organization.setRegion("太原");
        organization.setAddress("太原市迎泽区");
        organization.setContactPhone("03510000000");
        organization = organizationRepository.save(organization);

        institutionUser = new ClientUser();
        institutionUser.setName("太原机构管理员");
        institutionUser.setMobile("13800000011");
        institutionUser.setPassword("encoded");
        institutionUser.setRole(ClientUserRole.INSTITUTION);
        institutionUser.setOrgId(organization.getId());
        institutionUser = clientUserRepository.save(institutionUser);

        Family family = new Family();
        family.setName("迎泽苑一号");
        family.setAddress("迎泽苑 1 号楼");
        family.setOrgId(organization.getId());
        familyRepository.save(family);
    }

    @Test
    void institutionUserCanCreateFamilyScopedNewsWithAttachments() throws Exception {
        mockMvc.perform(post("/api/news")
                        .header("Authorization", "Bearer " + jwtService.generate(institutionUser.getId(), "INSTITUTION", organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "社区巡诊通知",
                                  "content": "请于本周三上午九点到活动室参加巡诊。",
                                  "category": "announcement",
                                  "targetScope": "FAMILY",
                                  "targetFamilyName": "迎泽苑一号",
                                  "attachments": [
                                    "https://example.com/uploads/miniapp/20260501/notice.jpg"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.targetFamilyName").value("迎泽苑一号"))
                .andExpect(jsonPath("$.data.publisherName").value("太原机构管理员"))
                .andExpect(jsonPath("$.data.attachments[0]").value("https://example.com/uploads/miniapp/20260501/notice.jpg"));

        List<NewsPost> saved = newsPostRepository.findAllByOrderByCreatedAtDesc();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPublisherId()).isEqualTo(institutionUser.getId());
        assertThat(saved.get(0).getPublisherName()).isEqualTo("太原机构管理员");
        assertThat(saved.get(0).getAttachments()).isEqualTo("[\"https://example.com/uploads/miniapp/20260501/notice.jpg\"]");

        mockMvc.perform(post("/api/news")
                        .header("Authorization", "Bearer " + jwtService.generate(institutionUser.getId(), "INSTITUTION", organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "二次通知",
                                  "content": "下午两点继续签到。",
                                  "category": "announcement",
                                  "targetScope": "ALL",
                                  "attachments": [
                                    "https://example.com/uploads/miniapp/20260501/notice-2.jpg",
                                    "https://example.com/uploads/miniapp/20260501/notice-3.jpg"
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        NewsPost latest = newsPostRepository.findAllByOrderByCreatedAtDesc().get(0);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/news/{id}", latest.getId())
                        .header("Authorization", "Bearer " + jwtService.generate(institutionUser.getId(), "INSTITUTION", organization.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachments[0]").value("https://example.com/uploads/miniapp/20260501/notice-2.jpg"))
                .andExpect(jsonPath("$.data.attachments[1]").value("https://example.com/uploads/miniapp/20260501/notice-3.jpg"));
    }
}
```

- [ ] **Step 2: Run the news publishing test to verify it fails**

Run: `./gradlew test --tests "com.ncf.demo.web.NewsPostControllerIntegrationTest"`

Expected: FAIL with `403` or `401` because `POST /api/news` is currently restricted to `ADMIN`

- [ ] **Step 3: Allow institution users to create news posts while keeping update and delete admin-only**

```java
// demo/src/main/java/com/ncf/demo/config/SecurityConfig.java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(publicEndpoints.toArray(String[]::new)).permitAll()
        .requestMatchers("/uploads/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/news/**").authenticated()
        .requestMatchers(HttpMethod.POST, "/api/news").hasAnyRole("ADMIN", "INSTITUTION")
        .requestMatchers("/api/news/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/service-orders/**").hasAnyRole("ADMIN", "GUARDIAN")
        .requestMatchers("/api/service-orders/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/organizations").authenticated()
        .requestMatchers("/api/mini/**").hasAnyRole("ADMIN", "NURSE", "DOCTOR", "GUARDIAN", "INSTITUTION", "CAREGIVER")
        .requestMatchers("/api/families/**", "/api/devices/**", "/api/alarms/**", "/api/data/**",
                "/api/guardian-targets/**", "/api/client-users/**", "/api/doctors/**").hasAnyRole("ADMIN", "GUARDIAN")
        .anyRequest().authenticated()
)
```

- [ ] **Step 4: Convert stored attachment JSON strings into response arrays**

```java
// demo/src/main/java/com/ncf/demo/web/NewsPostController.java
private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

public NewsPostController(NewsPostService newsPostService, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.newsPostService = newsPostService;
    this.objectMapper = objectMapper;
}

record NewsPostResponse(
        Long id,
        String title,
        String content,
        String visibility,
        String category,
        String targetScope,
        Long targetFamilyId,
        String targetFamilyName,
        Long publisherId,
        String publisherName,
        java.time.Instant publishTime,
        java.util.List<String> attachments,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
) {}

private NewsPostResponse toResponse(NewsPost post) {
    java.util.List<String> attachments = java.util.List.of();
    if (post.getAttachments() != null && !post.getAttachments().isBlank()) {
        try {
            attachments = objectMapper.readValue(
                    post.getAttachments(),
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            attachments = java.util.List.of(post.getAttachments());
        }
    }
    return new NewsPostResponse(
            post.getId(),
            post.getTitle(),
            post.getContent(),
            post.getVisibility(),
            post.getCategory(),
            post.getTargetScope(),
            post.getTargetFamilyId(),
            post.getTargetFamilyName(),
            post.getPublisherId(),
            post.getPublisherName(),
            post.getPublishTime(),
            attachments,
            post.getCreatedAt(),
            post.getUpdatedAt()
    );
}

@GetMapping
public ApiResponse<List<NewsPostResponse>> list() {
    return ApiResponse.ok(newsPostService.list().stream().map(this::toResponse).toList());
}

@GetMapping("/{id}")
public ApiResponse<NewsPostResponse> getById(@PathVariable Long id) {
    return ApiResponse.ok(toResponse(newsPostService.getById(id)));
}

@PostMapping
public ApiResponse<NewsPostResponse> create(@RequestBody @Valid NewsPostCreateRequest request) {
    return ApiResponse.ok(toResponse(newsPostService.create(request)));
}

@PutMapping("/{id}")
public ApiResponse<NewsPostResponse> update(@PathVariable Long id, @RequestBody @Valid NewsPostCreateRequest request) {
    return ApiResponse.ok(toResponse(newsPostService.update(id, request)));
}
```

- [ ] **Step 5: Run the news publishing test to verify it passes**

Run: `./gradlew test --tests "com.ncf.demo.web.NewsPostControllerIntegrationTest"`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ncf/demo/config/SecurityConfig.java src/main/java/com/ncf/demo/web/NewsPostController.java src/test/java/com/ncf/demo/web/NewsPostControllerIntegrationTest.java
git commit -m "feat: align miniapp news publishing responses"
```

## Final Verification

- [ ] Run all three contract test classes together.

Run: `./gradlew test --tests "com.ncf.demo.web.MiniAppUploadIntegrationTest" --tests "com.ncf.demo.web.MiniAppAppointmentIntegrationTest" --tests "com.ncf.demo.web.NewsPostControllerIntegrationTest"`
Expected: PASS with `BUILD SUCCESSFUL`

- [ ] Run the full backend test suite.

Run: `./gradlew test`
Expected: PASS with `BUILD SUCCESSFUL`

- [ ] Run a compile-only packaging check after tests are green.

Run: `./gradlew build -x test`
Expected: PASS with `BUILD SUCCESSFUL`
