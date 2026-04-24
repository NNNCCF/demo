package com.ncf.demo.web;

import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.domain.Gender;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.Alarm;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.AlarmRepository;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppGuardianProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private ClientUserRepository clientUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockBean
    private MqttConfig.MqttGateway mqttGateway;

    @BeforeEach
    void setUp() {
        alarmRepository.deleteAll();
        serviceOrderRepository.deleteAll();
        wardRepository.deleteAll();
        deviceRepository.deleteAll();
        familyRepository.deleteAll();
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void guardianProfilePageChainReturnsProfileAndStatsSourceData() throws Exception {
        Organization organization = createOrganization("Profile Org", "No. 30 Health Road", "03510000301");
        ClientUser guardian = createGuardian("Guardian Profile", "13800000301");
        ClientUser otherGuardian = createGuardian("Guardian Other", "13800000302");
        ClientUser caregiver = createCaregiver("Caregiver Profile", "13800000303", organization);

        Family guardianFamily = createFamily("Family Profile", "Room 1101", organization, guardian);
        Family otherFamily = createFamily("Family Other", "Room 1102", organization, otherGuardian);

        Device guardianDevice = createDevice("DEV-PROFILE-01", guardian, guardianFamily);
        Device otherDevice = createDevice("DEV-PROFILE-02", otherGuardian, otherFamily);

        Ward memberOne = createWard(7001L, "Member One", guardianDevice);
        Ward memberTwo = createWard(7002L, "Member Two", guardianDevice);
        Ward otherMember = createWard(8001L, "Member Other", otherDevice);

        createAlarm(memberOne.getMemberId(), "DEV-PROFILE-01", AlarmType.FALL, AlarmHandleStatus.UNHANDLED,
                Instant.parse("2026-04-23T01:00:00Z"));
        createAlarm(memberTwo.getMemberId(), "DEV-PROFILE-01", AlarmType.HEART_RATE, AlarmHandleStatus.HANDLED,
                Instant.parse("2026-04-22T01:00:00Z"));
        createAlarm(otherMember.getMemberId(), "DEV-PROFILE-02", AlarmType.EMERGENCY, AlarmHandleStatus.UNHANDLED,
                Instant.parse("2026-04-24T01:00:00Z"));

        createOrder(organization, guardianFamily, memberOne, guardian, caregiver,
                "护理服务", ServiceOrderStatus.PENDING, LocalDateTime.of(2026, 5, 11, 9, 0),
                LocalDateTime.of(2026, 5, 1, 8, 0));
        createOrder(organization, guardianFamily, memberTwo, guardian, caregiver,
                "上门探访", ServiceOrderStatus.COMPLETED, LocalDateTime.of(2026, 5, 12, 9, 0),
                LocalDateTime.of(2026, 5, 2, 8, 0));
        createOrder(organization, otherFamily, otherMember, otherGuardian, caregiver,
                "预约体检", ServiceOrderStatus.PENDING, LocalDateTime.of(2026, 5, 13, 9, 0),
                LocalDateTime.of(2026, 5, 3, 8, 0));

        String token = guardianToken(guardian);

        mockMvc.perform(get("/api/mini/guardian/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(guardian.getId()))
                .andExpect(jsonPath("$.data.name").value("Guardian Profile"))
                .andExpect(jsonPath("$.data.phone").value("13800000301"))
                .andExpect(jsonPath("$.data.role").value("guardian"));

        mockMvc.perform(get("/api/mini/member/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(memberOne.getMemberId()))
                .andExpect(jsonPath("$.data[1].id").value(memberTwo.getMemberId()));

        mockMvc.perform(get("/api/mini/alarms")
                        .param("status", "unhandled")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].member.name").value("Member One"))
                .andExpect(jsonPath("$.data[0].guardian.name").value("Guardian Profile"))
                .andExpect(jsonPath("$.data[0].status").value("unhandled"));

        mockMvc.perform(get("/api/mini/appointments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2));
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

    private ClientUser createCaregiver(String name, String mobile, Organization organization) {
        ClientUser caregiver = new ClientUser();
        caregiver.setName(name);
        caregiver.setMobile(mobile);
        caregiver.setPassword("encoded");
        caregiver.setRole(ClientUserRole.CAREGIVER);
        caregiver.setOrgId(organization.getId());
        return clientUserRepository.save(caregiver);
    }

    private Family createFamily(String name, String address, Organization organization, ClientUser guardian) {
        Family family = new Family();
        family.setName(name);
        family.setAddress(address);
        family.setOrgId(organization.getId());
        family.setGuardians(List.of(guardian));
        return familyRepository.save(family);
    }

    private Device createDevice(String deviceId, ClientUser guardian, Family family) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setDeviceType(DeviceType.HEART_RATE);
        device.setStatus(DeviceStatus.ONLINE);
        device.setGuardian(guardian);
        device.setFamilyId(family.getId());
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
        ward.setMobile("13900000300");
        return wardRepository.save(ward);
    }

    private Alarm createAlarm(Long memberId,
                              String deviceId,
                              AlarmType alarmType,
                              AlarmHandleStatus handleStatus,
                              Instant occurredAt) {
        Alarm alarm = new Alarm();
        alarm.setTargetId(memberId);
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(AlarmLevel.EMERGENCY);
        alarm.setHandleStatus(handleStatus);
        alarm.setOccurredAt(occurredAt);
        alarm.setCurrentValue("1");
        return alarmRepository.save(alarm);
    }

    private ServiceOrder createOrder(Organization organization,
                                     Family family,
                                     Ward ward,
                                     ClientUser guardian,
                                     ClientUser caregiver,
                                     String type,
                                     ServiceOrderStatus status,
                                     LocalDateTime appointTime,
                                     LocalDateTime createdAt) {
        ServiceOrder order = new ServiceOrder();
        order.setOrgId(organization.getId());
        order.setFamilyId(family.getId());
        order.setMemberId(ward.getMemberId());
        order.setGuardianId(guardian.getId());
        order.setCreatedById(guardian.getId());
        order.setDisplayType(type);
        order.setStatus(status);
        order.setAppointmentTime(appointTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        order.setCreatedAt(createdAt.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        order.setContactName(guardian.getName());
        order.setContactPhone(guardian.getMobile());
        order.setRequirement("Profile page service order");
        if (caregiver != null) {
            order.setNurseId(caregiver.getId());
            order.setNurseName(caregiver.getName());
            order.setNursePhone(caregiver.getMobile());
        }
        return serviceOrderRepository.save(order);
    }

    private String guardianToken(ClientUser guardian) {
        return jwtService.generate(guardian.getId(), "GUARDIAN", null);
    }
}
