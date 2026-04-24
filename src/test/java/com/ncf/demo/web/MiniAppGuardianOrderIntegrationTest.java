package com.ncf.demo.web;

import com.ncf.demo.config.MqttConfig;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.domain.Gender;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.entity.Ward;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppGuardianOrderIntegrationTest {

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
        clientUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void guardianListReturnsOwnOrdersWithPageFields() throws Exception {
        Organization organization = createOrganization("Order Org", "No. 9 Health Road", "03510000099");
        ClientUser guardian = createGuardian("Guardian One", "13800000121");
        ClientUser otherGuardian = createGuardian("Guardian Two", "13800000122");
        ClientUser caregiver = createCaregiver("Caregiver Xu", "13800000123", organization);

        Family guardianFamily = createFamily("Family One", "Room 501", organization, guardian);
        Family otherFamily = createFamily("Family Two", "Room 601", organization, otherGuardian);

        Device guardianDevice = createDevice("DEV-ORDER-01", guardian, guardianFamily);
        Device otherDevice = createDevice("DEV-ORDER-02", otherGuardian, otherFamily);

        Ward dispatchedWard = createWard(3001L, "Member A", guardianDevice);
        Ward completedWard = createWard(3002L, "Member B", guardianDevice);
        Ward otherWard = createWard(4001L, "Member X", otherDevice);

        ServiceOrder dispatchedOrder = createOrder(
                organization, guardianFamily, dispatchedWard, guardian, caregiver,
                "护理服务", ServiceOrderStatus.PENDING,
                LocalDateTime.of(2026, 5, 6, 10, 0),
                LocalDateTime.of(2026, 5, 1, 9, 0),
                null, null, null, null, "按时上门护理");
        ServiceOrder completedOrder = createOrder(
                organization, guardianFamily, completedWard, guardian, caregiver,
                "上门探访", ServiceOrderStatus.COMPLETED,
                LocalDateTime.of(2026, 5, 7, 14, 30),
                LocalDateTime.of(2026, 5, 2, 9, 0),
                LocalDateTime.of(2026, 5, 7, 15, 0),
                "Visit completed", "199.00", "paid", "上门探访记录");
        createOrder(
                organization, otherFamily, otherWard, otherGuardian, caregiver,
                "预约体检", ServiceOrderStatus.PENDING,
                LocalDateTime.of(2026, 5, 8, 8, 30),
                LocalDateTime.of(2026, 5, 3, 9, 0),
                null, null, null, null, "其他监护人订单");

        mockMvc.perform(get("/api/mini/appointments")
                        .header("Authorization", "Bearer " + guardianToken(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(completedOrder.getId()))
                .andExpect(jsonPath("$.data[0].type").value("上门探访"))
                .andExpect(jsonPath("$.data[0].status").value("completed"))
                .andExpect(jsonPath("$.data[0].acceptNurse").value("Caregiver Xu"))
                .andExpect(jsonPath("$.data[0].member.name").value("Member B"))
                .andExpect(jsonPath("$.data[0].family.address").value("Room 501"))
                .andExpect(jsonPath("$.data[0].guardian.name").value("Guardian One"))
                .andExpect(jsonPath("$.data[0].visitRemark").value("Visit completed"))
                .andExpect(jsonPath("$.data[0].payAmount").value("199.00"))
                .andExpect(jsonPath("$.data[1].id").value(dispatchedOrder.getId()))
                .andExpect(jsonPath("$.data[1].status").value("pending"))
                .andExpect(jsonPath("$.data[1].acceptNurse").value("Caregiver Xu"))
                .andExpect(jsonPath("$.data[1].member.name").value("Member A"));
    }

    @Test
    void guardianDetailReturnsFieldsUsedByOrderDetailPage() throws Exception {
        Organization organization = createOrganization("Detail Org", "No. 10 Health Road", "03510000101");
        ClientUser guardian = createGuardian("Guardian Detail", "13800000131");
        ClientUser caregiver = createCaregiver("Caregiver Detail", "13800000132", organization);
        Family family = createFamily("Family Detail", "Room 701", organization, guardian);
        Device device = createDevice("DEV-DETAIL-01", guardian, family);
        Ward ward = createWard(5001L, "Member Detail", device);

        ServiceOrder order = createOrder(
                organization, family, ward, guardian, caregiver,
                "护理服务", ServiceOrderStatus.COMPLETED,
                LocalDateTime.of(2026, 5, 9, 9, 15),
                LocalDateTime.of(2026, 5, 4, 8, 0),
                LocalDateTime.of(2026, 5, 9, 10, 5),
                "Service record remark", "299.00", "paid", "服务对象：Member Detail");

        mockMvc.perform(get("/api/mini/appointments/{id}", order.getId())
                        .header("Authorization", "Bearer " + guardianToken(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(order.getId()))
                .andExpect(jsonPath("$.data.type").value("护理服务"))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.appointTime").value("2026-05-09 09:15:00"))
                .andExpect(jsonPath("$.data.member.name").value("Member Detail"))
                .andExpect(jsonPath("$.data.guardian.name").value("Guardian Detail"))
                .andExpect(jsonPath("$.data.requirement").value("服务对象：Member Detail"))
                .andExpect(jsonPath("$.data.acceptNurse").value("Caregiver Detail"))
                .andExpect(jsonPath("$.data.nursePhone").value("13800000132"))
                .andExpect(jsonPath("$.data.visitTime").value("2026-05-09 10:05:00"))
                .andExpect(jsonPath("$.data.visitRemark").value("Service record remark"))
                .andExpect(jsonPath("$.data.payAmount").value("299.00"))
                .andExpect(jsonPath("$.data.payStatus").value("paid"));
    }

    @Test
    void guardianCannotOpenAnotherGuardiansOrderDetail() throws Exception {
        Organization organization = createOrganization("Secure Org", "No. 11 Health Road", "03510000111");
        ClientUser guardian = createGuardian("Guardian Safe", "13800000141");
        ClientUser otherGuardian = createGuardian("Guardian Locked", "13800000142");
        ClientUser caregiver = createCaregiver("Caregiver Safe", "13800000143", organization);

        Family family = createFamily("Family Locked", "Room 801", organization, otherGuardian);
        Device device = createDevice("DEV-LOCK-01", otherGuardian, family);
        Ward ward = createWard(6001L, "Member Locked", device);
        ServiceOrder foreignOrder = createOrder(
                organization, family, ward, otherGuardian, caregiver,
                "预约体检", ServiceOrderStatus.PENDING,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                null, null, null, null, "不可越权查看");

        mockMvc.perform(get("/api/mini/appointments/{id}", foreignOrder.getId())
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
        ward.setMobile("13900000100");
        return wardRepository.save(ward);
    }

    private ServiceOrder createOrder(Organization organization,
                                     Family family,
                                     Ward ward,
                                     ClientUser guardian,
                                     ClientUser caregiver,
                                     String type,
                                     ServiceOrderStatus status,
                                     LocalDateTime appointTime,
                                     LocalDateTime createdAt,
                                     LocalDateTime visitTime,
                                     String visitRemark,
                                     String payAmount,
                                     String payStatus,
                                     String requirement) {
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
        order.setRequirement(requirement);
        if (caregiver != null) {
            order.setNurseId(caregiver.getId());
            order.setNurseName(caregiver.getName());
            order.setNursePhone(caregiver.getMobile());
        }
        if (visitTime != null) {
            order.setVisitTime(visitTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        }
        order.setVisitRemark(visitRemark);
        order.setPayAmount(payAmount);
        order.setPayStatus(payStatus);
        return serviceOrderRepository.save(order);
    }

    private String guardianToken(ClientUser guardian) {
        return jwtService.generate(guardian.getId(), "GUARDIAN", null);
    }
}
