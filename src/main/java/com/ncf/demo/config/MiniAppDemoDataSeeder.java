package com.ncf.demo.config;

import com.ncf.demo.domain.AlarmHandleStatus;
import com.ncf.demo.domain.AlarmLevel;
import com.ncf.demo.domain.AlarmType;
import com.ncf.demo.domain.ClientUserRole;
import com.ncf.demo.domain.DeviceStatus;
import com.ncf.demo.domain.DeviceType;
import com.ncf.demo.domain.Gender;
import com.ncf.demo.domain.OrgType;
import com.ncf.demo.domain.ServiceOrderStatus;
import com.ncf.demo.domain.UserStatus;
import com.ncf.demo.entity.Alarm;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Doctor;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.GuardianAlarmSetting;
import com.ncf.demo.entity.HealthData;
import com.ncf.demo.entity.NewsPost;
import com.ncf.demo.entity.Organization;
import com.ncf.demo.entity.ServiceOrder;
import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.AlarmRepository;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.DoctorRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.GuardianAlarmSettingRepository;
import com.ncf.demo.repository.HealthDataRepository;
import com.ncf.demo.repository.NewsPostRepository;
import com.ncf.demo.repository.OrganizationRepository;
import com.ncf.demo.repository.ServiceOrderRepository;
import com.ncf.demo.repository.WardRepository;
import com.ncf.demo.service.TdengineService;
import com.ncf.demo.service.model.ParsedDeviceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MiniAppDemoDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(MiniAppDemoDataSeeder.class);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final AppProperties appProperties;
    private final OrganizationRepository organizationRepository;
    private final ClientUserRepository clientUserRepository;
    private final FamilyRepository familyRepository;
    private final DeviceRepository deviceRepository;
    private final WardRepository wardRepository;
    private final DoctorRepository doctorRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final AlarmRepository alarmRepository;
    private final HealthDataRepository healthDataRepository;
    private final NewsPostRepository newsPostRepository;
    private final GuardianAlarmSettingRepository guardianAlarmSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final TdengineService tdengineService;

    public MiniAppDemoDataSeeder(
            AppProperties appProperties,
            OrganizationRepository organizationRepository,
            ClientUserRepository clientUserRepository,
            FamilyRepository familyRepository,
            DeviceRepository deviceRepository,
            WardRepository wardRepository,
            DoctorRepository doctorRepository,
            ServiceOrderRepository serviceOrderRepository,
            AlarmRepository alarmRepository,
            HealthDataRepository healthDataRepository,
            NewsPostRepository newsPostRepository,
            GuardianAlarmSettingRepository guardianAlarmSettingRepository,
            PasswordEncoder passwordEncoder,
            TdengineService tdengineService
    ) {
        this.appProperties = appProperties;
        this.organizationRepository = organizationRepository;
        this.clientUserRepository = clientUserRepository;
        this.familyRepository = familyRepository;
        this.deviceRepository = deviceRepository;
        this.wardRepository = wardRepository;
        this.doctorRepository = doctorRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.alarmRepository = alarmRepository;
        this.healthDataRepository = healthDataRepository;
        this.newsPostRepository = newsPostRepository;
        this.guardianAlarmSettingRepository = guardianAlarmSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.tdengineService = tdengineService;
    }

    @Transactional
    public void seedIfNeeded() {
        if (!appProperties.isMockDataEnabled()) {
            return;
        }
        if (organizationRepository.count() > 0 || clientUserRepository.count() > 0 || familyRepository.count() > 0) {
            log.info("Mini-app demo data already exists, skipping seed.");
            return;
        }

        String password = passwordEncoder.encode("123456");

        Organization org = new Organization();
        org.setName("卓凯安伴社区服务中心");
        org.setType(OrgType.MEDICAL_INSTITUTION);
        org.setRegion("山西省太原市小店区");
        org.setAddress("山西省太原市小店区龙城街道康宁路 88 号");
        org.setContactPhone("03511234567");
        org.setLicenseNo("SX-YLJG-2026-0001");
        org.setStatus(UserStatus.ENABLED);
        organizationRepository.save(org);

        ClientUser institution = new ClientUser();
        institution.setName("机构管理员");
        institution.setMobile("18800000003");
        institution.setPassword(password);
        institution.setRole(ClientUserRole.INSTITUTION);
        institution.setOrgId(org.getId());
        clientUserRepository.save(institution);

        ClientUser caregiver = new ClientUser();
        caregiver.setName("张医护");
        caregiver.setMobile("18800000002");
        caregiver.setPassword(password);
        caregiver.setRole(ClientUserRole.CAREGIVER);
        caregiver.setOrgId(org.getId());
        caregiver.setDepartment("老年护理");
        caregiver.setTitle("主管护师");
        caregiver.setIdCard("140123199001011234");
        clientUserRepository.save(caregiver);

        ClientUser guardian = new ClientUser();
        guardian.setName("李家属");
        guardian.setMobile("18800000001");
        guardian.setPassword(password);
        guardian.setRole(ClientUserRole.GUARDIAN);
        clientUserRepository.save(guardian);

        GuardianAlarmSetting alarmSetting = new GuardianAlarmSetting();
        alarmSetting.setGuardianId(guardian.getId());
        guardianAlarmSettingRepository.save(alarmSetting);

        Family family = new Family();
        family.setName("李奶奶家庭");
        family.setAddress("太原市小店区龙城街道幸福花园 8 号楼 2 单元 1202");
        family.setOrgId(org.getId());
        family.setCaregiverId(caregiver.getId());
        family.setGuardians(List.of(guardian));
        familyRepository.save(family);

        Doctor doctor = new Doctor();
        doctor.setName("王医生");
        doctor.setMobile("18800000004");
        doctor.setPassword(password);
        doctor.setTitle("全科医生");
        doctor.setOrgId(org.getId());
        doctorRepository.save(doctor);

        Device device = new Device();
        device.setDeviceId("DEV-MINI-001");
        device.setDeviceType(DeviceType.HEALTH_MONITOR);
        device.setAddress(family.getAddress());
        device.setHomeLocation("37.788180,112.550864");
        device.setLatitude(37.78818);
        device.setLongitude(112.550864);
        device.setStatus(DeviceStatus.ONLINE);
        device.setCreatedAt(Instant.now().minusSeconds(86400));
        device.setBindTime(Instant.now().minusSeconds(7200));
        device.setLastOnlineAt(Instant.now());
        device.setFamilyId(family.getId());
        device.setDoctorId(doctor.getId());
        device.setGuardian(guardian);
        deviceRepository.save(device);

        Ward ward = new Ward();
        ward.setMemberId(1L);
        ward.setName("李奶奶");
        ward.setGender(Gender.FEMALE);
        ward.setBirthday(LocalDate.of(1948, 5, 12).atStartOfDay(ZONE_ID).toInstant());
        ward.setMobile("18800009999");
        ward.setChronicDisease("高血压");
        ward.setRemark("需要定期巡诊");
        ward.setEmergencyPhone("13800138000");
        ward.setDevice(device);
        wardRepository.save(ward);

        device.setTargetId(ward.getMemberId());
        deviceRepository.save(device);

        ServiceOrder pending = new ServiceOrder();
        pending.setDisplayType("上门探诊");
        pending.setOrgId(org.getId());
        pending.setFamilyId(family.getId());
        pending.setMemberId(ward.getMemberId());
        pending.setGuardianId(guardian.getId());
        pending.setCreatedById(guardian.getId());
        pending.setNurseId(caregiver.getId());
        pending.setNurseName(caregiver.getName());
        pending.setNursePhone(caregiver.getMobile());
        pending.setDispatchedBy(org.getName());
        pending.setAppointmentTime(Instant.now().plusSeconds(86400));
        pending.setRequirement("家属发起的上门探诊，重点查看血压与睡眠情况");
        pending.setStatus(ServiceOrderStatus.PENDING);
        pending.setCreatedAt(Instant.now().minusSeconds(7200));
        serviceOrderRepository.save(pending);

        ServiceOrder completed = new ServiceOrder();
        completed.setDisplayType("护理服务");
        completed.setOrgId(org.getId());
        completed.setFamilyId(family.getId());
        completed.setMemberId(ward.getMemberId());
        completed.setGuardianId(guardian.getId());
        completed.setCreatedById(guardian.getId());
        completed.setNurseId(caregiver.getId());
        completed.setNurseName(caregiver.getName());
        completed.setNursePhone(caregiver.getMobile());
        completed.setDispatchedBy(org.getName());
        completed.setAppointmentTime(Instant.now().minusSeconds(172800));
        completed.setAcceptTime(Instant.now().minusSeconds(170000));
        completed.setVisitTime(Instant.now().minusSeconds(86400));
        completed.setVisitRemark("已完成血压测量与用药指导");
        completed.setPayAmount("80");
        completed.setPayStatus("已支付");
        completed.setRequirement("常规护理服务");
        completed.setStatus(ServiceOrderStatus.COMPLETED);
        completed.setCreatedAt(Instant.now().minusSeconds(200000));
        serviceOrderRepository.save(completed);

        Alarm alarm = new Alarm();
        alarm.setTargetId(ward.getMemberId());
        alarm.setDeviceId(device.getDeviceId());
        alarm.setAlarmType(AlarmType.FALL);
        alarm.setAlarmLevel(AlarmLevel.EMERGENCY);
        alarm.setOccurredAt(Instant.now().minusSeconds(1800));
        alarm.setCurrentValue("1");
        alarm.setHandleStatus(AlarmHandleStatus.UNHANDLED);
        alarmRepository.save(alarm);

        NewsPost allNews = new NewsPost();
        allNews.setTitle("居家巡诊服务已上线");
        allNews.setContent("可在小程序内直接预约上门探诊、护理服务和送药服务。");
        allNews.setVisibility("ALL");
        allNews.setCategory("health");
        allNews.setTargetScope("ALL");
        allNews.setPublisherId(caregiver.getId());
        allNews.setPublisherName(caregiver.getName());
        allNews.setPublishTime(Instant.now().minusSeconds(3600));
        allNews.setCreatedAt(Instant.now().minusSeconds(3600));
        allNews.setAttachments("[]");
        newsPostRepository.save(allNews);

        NewsPost familyNews = new NewsPost();
        familyNews.setTitle("李奶奶本周随访提醒");
        familyNews.setContent("请按时关注本周三上午的上门随访安排。");
        familyNews.setVisibility("ALL");
        familyNews.setCategory("urgent");
        familyNews.setTargetScope("FAMILY");
        familyNews.setTargetFamilyId(family.getId());
        familyNews.setTargetFamilyName(family.getName());
        familyNews.setPublisherId(caregiver.getId());
        familyNews.setPublisherName(caregiver.getName());
        familyNews.setPublishTime(Instant.now().minusSeconds(1800));
        familyNews.setCreatedAt(Instant.now().minusSeconds(1800));
        familyNews.setAttachments("[]");
        newsPostRepository.save(familyNews);

        seedHealthData(ward.getMemberId(), device.getDeviceId());
        log.info("Mini-app demo data seeded with guardian={}, caregiver={}, institution={}",
                guardian.getMobile(), caregiver.getMobile(), institution.getMobile());
    }

    private void seedHealthData(Long memberId, String deviceId) {
        for (int i = 23; i >= 0; i--) {
            Instant point = Instant.now().minusSeconds(i * 3600L);

            HealthData data = new HealthData();
            data.setDataId(1000L + (23 - i));
            data.setMemberId(memberId);
            data.setHeartRateAvg(72.0 + ((23 - i) % 5));
            data.setHeartRateMax(78.0 + ((23 - i) % 5));
            data.setHeartRateMin(68.0 + ((23 - i) % 4));
            data.setRespRateAvg(17.0 + ((23 - i) % 3));
            data.setRespRateMax(19.0 + ((23 - i) % 3));
            data.setRespRateMin(15.0 + ((23 - i) % 2));
            data.setFallStatus(false);
            data.setLocationStatus(true);
            data.setRecordTime(point);
            healthDataRepository.save(data);

            Map<String, Object> payload = new HashMap<>();
            payload.put("heartRate", data.getHeartRateAvg().intValue());
            payload.put("breathRate", data.getRespRateAvg().intValue());
            payload.put("is_fall", false);
            payload.put("is_person_present", true);
            tdengineService.save(new ParsedDeviceData(deviceId, DeviceType.HEALTH_MONITOR, point, payload));
        }
    }
}
