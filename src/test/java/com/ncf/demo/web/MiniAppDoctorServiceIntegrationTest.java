package com.ncf.demo.web;

import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.domain.Gender;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Doctor;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.DoctorRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.ServiceOrderRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppDoctorServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockBean
    private MqttConfig.MqttGateway mqttGateway;

    @BeforeEach
    void setUp() {
        serviceOrderRepository.deleteAll();
        wardRepository.deleteAll();
        deviceRepository.deleteAll();
        familyRepository.deleteAll();
        doctorRepository.deleteAll();
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void guardianServiceEndpointsUseBoundDeviceContext() throws Exception {
        Organization unrelatedOrg = createOrganization("Unrelated Org", "Shanghai Road", "02100000000");
        Organization serviceOrg = createOrganization("Taiyuan Service Center", "No. 8 Kangning Road", "03510000088");
        ClientUser guardian = createGuardian("Guardian Li", "13800000031");
        Family family = createFamily("Family Beta", "Building 2", serviceOrg, guardian);
        Doctor doctor = createDoctor("Dr. Zhao", "13800000032", "Chief Physician", serviceOrg);
        Device device = createDevice("DEV-DOCTOR-01", guardian, family, doctor);
        Ward ward = createWard(1001L, "Grandpa Sun", device);

        String token = guardianToken(guardian);

        mockMvc.perform(get("/api/mini/service/doctor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(doctor.getId()))
                .andExpect(jsonPath("$.data.name").value("Dr. Zhao"))
                .andExpect(jsonPath("$.data.phone").value("13800000032"))
                .andExpect(jsonPath("$.data.institution").value("Taiyuan Service Center"))
                .andExpect(jsonPath("$.data.title").value("Chief Physician"));

        mockMvc.perform(get("/api/mini/service/center")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(serviceOrg.getId()))
                .andExpect(jsonPath("$.data.name").value("Taiyuan Service Center"))
                .andExpect(jsonPath("$.data.address").value("No. 8 Kangning Road"))
                .andExpect(jsonPath("$.data.phone").value("03510000088"))
                .andExpect(jsonPath("$.data.type").value(serviceOrg.getType().name()));

        mockMvc.perform(get("/api/mini/member/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(ward.getMemberId()))
                .andExpect(jsonPath("$.data[0].name").value("Grandpa Sun"))
                .andExpect(jsonPath("$.data[0].deviceId").value("DEV-DOCTOR-01"))
                .andExpect(jsonPath("$.data[0].deviceStatus").value("online"));

        mockMvc.perform(get("/api/mini/doctor/{id}", doctor.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(doctor.getId()))
                .andExpect(jsonPath("$.data.name").value("Dr. Zhao"))
                .andExpect(jsonPath("$.data.phone").value("13800000032"))
                .andExpect(jsonPath("$.data.title").value("Chief Physician"))
                .andExpect(jsonPath("$.data.hospital").value("Taiyuan Service Center"));

        assertThat(unrelatedOrg.getId()).isNotEqualTo(serviceOrg.getId());
    }

    @Test
    void guardianAppointmentUsesSelectedMemberFamilyAndOrganization() throws Exception {
        Organization firstOrg = createOrganization("First Org", "First Street", "01000000001");
        Organization selectedOrg = createOrganization("Selected Org", "Second Street", "02000000002");
        ClientUser guardian = createGuardian("Guardian Wang", "13800000041");

        Family firstFamily = createFamily("Family One", "Room 101", firstOrg, guardian);
        Family selectedFamily = createFamily("Family Two", "Room 202", selectedOrg, guardian);

        Device firstDevice = createDevice("DEV-FAMILY-01", guardian, firstFamily, null);
        Device selectedDevice = createDevice("DEV-FAMILY-02", guardian, selectedFamily, null);

        createWard(2001L, "Member One", firstDevice);
        Ward selectedWard = createWard(2002L, "Member Two", selectedDevice);

        String token = guardianToken(guardian);

        mockMvc.perform(post("/api/mini/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "护理服务",
                                  "memberId": 2002,
                                  "appointTime": "2026-05-04 09:30",
                                  "contactName": "Guardian Wang",
                                  "contactPhone": "13800000041",
                                  "requirement": "服务对象：Member Two\\n补充说明：需要重点护理"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.member.name").value("Member Two"))
                .andExpect(jsonPath("$.data.family.id").value(selectedFamily.getId()))
                .andExpect(jsonPath("$.data.family.address").value("Room 202"))
                .andExpect(jsonPath("$.data.guardian.name").value("Guardian Wang"))
                .andExpect(jsonPath("$.data.appointTime").exists())
                .andExpect(jsonPath("$.data.contactName").value("Guardian Wang"))
                .andExpect(jsonPath("$.data.contactPhone").value("13800000041"))
                .andExpect(jsonPath("$.data.requirement").value("服务对象：Member Two\n补充说明：需要重点护理"));

        List<ServiceOrder> orders = serviceOrderRepository.findAll();
        assertThat(orders).hasSize(1);

        ServiceOrder saved = orders.get(0);
        Instant expectedTime = LocalDateTime.of(2026, 5, 4, 9, 30)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant();

        assertThat(saved.getGuardianId()).isEqualTo(guardian.getId());
        assertThat(saved.getMemberId()).isEqualTo(selectedWard.getMemberId());
        assertThat(saved.getFamilyId()).isEqualTo(selectedFamily.getId());
        assertThat(saved.getOrgId()).isEqualTo(selectedOrg.getId());
        assertThat(saved.getAppointmentTime()).isEqualTo(expectedTime);
        assertThat(saved.getContactName()).isEqualTo("Guardian Wang");
        assertThat(saved.getContactPhone()).isEqualTo("13800000041");
        assertThat(saved.getRequirement()).isEqualTo("服务对象：Member Two\n补充说明：需要重点护理");
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

    private Doctor createDoctor(String name, String mobile, String title, Organization organization) {
        Doctor doctor = new Doctor();
        doctor.setName(name);
        doctor.setMobile(mobile);
        doctor.setPassword("encoded");
        doctor.setTitle(title);
        doctor.setOrgId(organization.getId());
        return doctorRepository.save(doctor);
    }

    private Device createDevice(String deviceId, ClientUser guardian, Family family, Doctor doctor) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setDeviceType(DeviceType.HEART_RATE);
        device.setStatus(DeviceStatus.ONLINE);
        device.setGuardian(guardian);
        device.setFamilyId(family.getId());
        device.setDoctorId(doctor != null ? doctor.getId() : null);
        device.setAddress(family.getAddress());
        device.setBindTime(Instant.now());
        return deviceRepository.save(device);
    }

    private Ward createWard(Long memberId, String name, Device device) {
        Ward ward = new Ward();
        ward.setMemberId(memberId);
        ward.setName(name);
        ward.setDevice(device);
        ward.setGender(Gender.MALE);
        ward.setMobile("13900000000");
        return wardRepository.save(ward);
    }

    private String guardianToken(ClientUser guardian) {
        return jwtService.generate(guardian.getId(), "GUARDIAN", null);
    }
}
