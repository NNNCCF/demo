package com.ncf.demo.web;

import com.ncf.demo.config.MqttConfig;
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
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private MqttConfig.MqttGateway mqttGateway;

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
        organization.setName("Test Organization");
        organization.setType(OrgType.MEDICAL_INSTITUTION);
        organization.setRegion("Taiyuan");
        organization.setAddress("No. 1 Health Road");
        organization.setContactPhone("03510000000");
        organization = organizationRepository.save(organization);

        institutionUser = new ClientUser();
        institutionUser.setName("Institution Manager");
        institutionUser.setMobile("13800000001");
        institutionUser.setPassword("encoded");
        institutionUser.setRole(ClientUserRole.INSTITUTION);
        institutionUser.setOrgId(organization.getId());
        institutionUser = clientUserRepository.save(institutionUser);

        caregiver = new ClientUser();
        caregiver.setName("Caregiver Wang");
        caregiver.setMobile("13800000002");
        caregiver.setPassword("encoded");
        caregiver.setRole(ClientUserRole.CAREGIVER);
        caregiver.setOrgId(organization.getId());
        caregiver = clientUserRepository.save(caregiver);

        guardian = new ClientUser();
        guardian.setName("Guardian Li");
        guardian.setMobile("13800000003");
        guardian.setPassword("encoded");
        guardian.setRole(ClientUserRole.GUARDIAN);
        guardian = clientUserRepository.save(guardian);

        family = new Family();
        family.setName("Family One");
        family.setAddress("Building 1");
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
        order.setDisplayType("Home Visit");
        order.setStatus(ServiceOrderStatus.PENDING);
        order = serviceOrderRepository.save(order);

        mockMvc.perform(put("/api/mini/appointments/{id}/dispatch", order.getId())
                        .header("Authorization", "Bearer " + jwtService.generate(
                                institutionUser.getId(), "INSTITUTION", organization.getId()))
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
        assertThat(saved.getNursePhone()).isEqualTo(caregiver.getMobile());
        assertThat(saved.getDispatchedBy()).isEqualTo(organization.getName());
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
        order.setDisplayType("Home Visit");
        order.setStatus(ServiceOrderStatus.ACCEPTED);
        order = serviceOrderRepository.save(order);

        mockMvc.perform(post("/api/mini/appointments/{id}/visit-record", order.getId())
                        .header("Authorization", "Bearer " + jwtService.generate(
                                caregiver.getId(), "CAREGIVER", organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visitTime": "2026-05-01 08:30:45",
                                  "remark": "Visit completed",
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
        assertThat(saved.getVisitRemark()).isEqualTo("Visit completed");
        assertThat(saved.getVisitPhotos()).isEqualTo(
                "[\"https://example.com/uploads/miniapp/20260501/a.jpg\",\"https://example.com/uploads/miniapp/20260501/b.jpg\"]");
    }
}
