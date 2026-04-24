package com.ncf.demo.web;

import com.ncf.demo.common.BizException;
import com.ncf.demo.config.AppProperties;
import com.ncf.demo.domain.*;
import com.ncf.demo.entity.*;
import com.ncf.demo.repository.*;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.service.AlarmService;
import com.ncf.demo.service.TdengineService;
import com.ncf.demo.web.dto.mini.NurseListItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mini-app business endpoints 閳?all routes require authentication.
 * All endpoints live under /api/mini/ to avoid collisions with existing admin routes.
 */
@RestController
@RequestMapping("/api/mini")
public class MiniAppController {

    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final AlarmRepository alarmRepo;
    private final WardRepository wardRepo;
    private final DeviceRepository deviceRepo;
    private final FamilyRepository familyRepo;
    private final ClientUserRepository clientUserRepo;
    private final UserRepository userRepo;
    private final DoctorRepository doctorRepo;
    private final ServiceOrderRepository orderRepo;
    private final HealthDataRepository healthDataRepo;
    private final OrganizationRepository orgRepo;
    private final NewsPostRepository newsRepo;
    private final GuardianAlarmSettingRepository guardianAlarmSettingRepo;
    private final FeedbackSubmissionRepository feedbackRepo;
    private final AlarmService alarmService;
    private final TdengineService tdengineService;
    private final AppProperties appProperties;

    public MiniAppController(AlarmRepository alarmRepo, WardRepository wardRepo,
                             DeviceRepository deviceRepo, FamilyRepository familyRepo,
                             ClientUserRepository clientUserRepo, UserRepository userRepo,
                             DoctorRepository doctorRepo, ServiceOrderRepository orderRepo,
                             HealthDataRepository healthDataRepo, OrganizationRepository orgRepo,
                             NewsPostRepository newsRepo,
                             GuardianAlarmSettingRepository guardianAlarmSettingRepo,
                             FeedbackSubmissionRepository feedbackRepo,
                             AlarmService alarmService,
                             TdengineService tdengineService,
                             AppProperties appProperties) {
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

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // Inner VO / request record types
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    record FamilyRef(Long id, String address, String community) {}
    record MemberRef(String name, Integer age) {}
    record GuardianRef(String name, String phone) {}

    record AlarmVo(Long id, String alarmTime, String type, String status,
                   FamilyRef family, MemberRef member, GuardianRef guardian,
                   String handleTime, String handleNurse, String handleRemark) {}

    record ApptVo(Long id, String type, String status,
                  String doctor, String acceptNurse,
                  FamilyRef family, MemberRef member, GuardianRef guardian,
                  String appointTime, String requirement,
                  String payAmount, String payStatus,
                  String visitTime, String visitRemark,
                  String completeTime, String dispatchedBy,
                  String contactName, String contactPhone,
                  String serviceAddress, String medicineList) {}

    record FamilyVo(Long id, String shortName, String address, String community,
                    Double latitude, Double longitude,
                    boolean hasAlarm, String level, String status,
                    GuardianRef guardian, List<MemberVo> members) {}

    record MemberVo(Long id, String name, Integer age, String gender,
                    String mobile, String chronicDisease, String remark,
                    String deviceId, String deviceStatus) {}

    record DeviceVo(String id, String deviceCode, String location, String address,
                    String status, String bindTime, List<MemberVo> members) {}

    record MonitorVo(Integer heartRate, Integer breathRate,
                     Boolean fallStatus, Boolean locationStatus,
                     Integer activityLevel, String recordTime,
                     String heartStatus, String breathStatus, String activityStatus) {}

    record HistoryPoint(String time, double value) {}

    record HistoryDataVo(List<String> hours, List<Double> values, String unit) {}

    record AlarmTypeBar(String type, int count) {}

    record SummaryVo(int familyCount, int totalAppt, int completedAppt,
                     int handledAlarms, List<AlarmTypeBar> alarmTypeBars) {}

    record HandleAlarmRequest(Boolean calledGuardian, Boolean memberDanger,
                               Boolean handled, String remark) {}

    record CreateApptRequest(@NotBlank String type, Long memberId, Long familyId,
                              Long guardianId, String appointTime, String requirement,
                              String contactName, String contactPhone) {}

    record DispatchRequest(Long nurseId, String nurseName) {}

    record VisitRecordRequest(String visitTime, String payAmount, String payStatus,
                               String remark, List<String> photos) {}

    record BindDeviceRequest(@NotBlank String deviceCode, String location, String address,
                             Double latitude, Double longitude) {}

    record UnbindDeviceRequest(String deviceId, Boolean keepHistory) {}

    record CreateMemberRequest(String name, String gender, String birthday,
                                Double height, Double weight, String mobile,
                                String chronicDisease, String remark,
                                String emergencyPhone, String deviceId) {}

    record StaffProfileUpdateRequest(String name, String department, String title, String idCard) {}

    record GuardianProfileUpdateRequest(String name, String phone) {}

    record GuardianAlarmSettingRequest(Boolean hrAlert, Boolean bpAlert, Boolean fallAlert, Boolean bedAlert) {}

    record FeedbackRequest(@NotBlank String type, @NotBlank String content) {}

    record EmergencyRequest(Long memberId, String note) {}

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // Helpers
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    private String fmt(Instant instant) {
        return instant == null ? null : DT_FMT.format(instant);
    }

    private Integer calcAge(Instant birthday) {
        if (birthday == null) return null;
        LocalDate bd = birthday.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        return Period.between(bd, LocalDate.now(ZoneId.of("Asia/Shanghai"))).getYears();
    }

    private String alarmTypeLabel(AlarmType t) {
        if (t == null) return "未知";
        return switch (t) {
            case HEART_RATE -> "心率报警";
            case BREATH_RATE -> "呼吸频率报警";
            case FALL -> "跌倒报警";
            case DEVICE_OFFLINE -> "设备离线报警";
            case EMERGENCY -> "紧急求助";
        };
    }

    private String alarmStatusStr(AlarmHandleStatus s) {
        if (s == null) return "unhandled";
        return switch (s) {
            case HANDLED -> "handled";
            case IGNORED -> "ignored";
            default -> "unhandled";
        };
    }

    private AlarmHandleStatus parseAlarmStatus(String s) {
        if (s == null) return null;
        return switch (s) {
            case "handled" -> AlarmHandleStatus.HANDLED;
            case "ignored" -> AlarmHandleStatus.IGNORED;
            default -> AlarmHandleStatus.UNHANDLED;
        };
    }

    private String apptStatusStr(ServiceOrderStatus s) {
        if (s == null) return "pending";
        return switch (s) {
            case ACCEPTED -> "accepted";
            case COMPLETED -> "completed";
            case CANCELED -> "cancelled";
            default -> "pending";
        };
    }

    /** JWT uid for GUARDIAN/CAREGIVER is client_user.id directly. */
    private Optional<ClientUser> currentClientUser() {
        Long uid = SecurityUtil.currentUserId();
        if (uid == null) return Optional.empty();
        return clientUserRepo.findById(uid);
    }

    private Long parseLongParam(String value) {
        if (value == null || value.isBlank() || "undefined".equals(value) || "null".equals(value)) return null;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return null; }
    }

    private ClientUser requireCurrentClientUser() {
        return currentClientUser().orElseThrow(() -> new BizException(401, "用户未登录"));
    }

    private Family primaryFamilyOfGuardian(Long guardianId) {
        return familyRepo.findByGuardiansId(guardianId).stream().findFirst().orElse(null);
    }

    private String resolveUserDisplayName(Long id) {
        if (id == null) return null;
        return clientUserRepo.findById(id)
                .map(ClientUser::getName)
                .or(() -> userRepo.findById(id).map(UserAccount::getUsername))
                .orElse(null);
    }

    private String resolveUserPhone(Long id) {
        if (id == null) return null;
        return clientUserRepo.findById(id)
                .map(ClientUser::getMobile)
                .or(() -> userRepo.findById(id).map(UserAccount::getPhone))
                .orElse(null);
    }

    private boolean shouldRestrictAlarmAccess() {
        String role = SecurityUtil.currentRole();
        return "GUARDIAN".equals(role)
                || "CAREGIVER".equals(role)
                || "INSTITUTION".equals(role)
                || "NURSE".equals(role)
                || "DOCTOR".equals(role);
    }

    private Set<Long> accessibleMemberIds() {
        String role = SecurityUtil.currentRole();
        if ("GUARDIAN".equals(role)) {
            return currentClientUser()
                    .map(cu -> wardRepo.findByDeviceGuardianId(cu.getId()).stream()
                            .map(Ward::getMemberId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()))
                    .orElseGet(Set::of);
        }

        if ("CAREGIVER".equals(role) || "INSTITUTION".equals(role)) {
            return allowedFamilies().stream()
                    .flatMap(f -> deviceRepo.findByFamilyId(f.getId()).stream())
                    .flatMap(d -> d.getWards() != null
                            ? d.getWards().stream()
                            : java.util.stream.Stream.<Ward>empty())
                    .map(Ward::getMemberId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        if ("NURSE".equals(role) || "DOCTOR".equals(role)) {
            Long orgId = SecurityUtil.currentOrgId();
            if (orgId == null) return Set.of();
            return wardRepo.findByDeviceFamilyOrgId(orgId).stream()
                    .map(Ward::getMemberId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    private Alarm requireAccessibleAlarm(Long id) {
        Alarm alarm = alarmRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "报警记录不存在"));
        if (shouldRestrictAlarmAccess()) {
            Long targetId = alarm.getTargetId();
            Set<Long> memberIds = accessibleMemberIds();
            if (targetId == null || !memberIds.contains(targetId)) {
                throw new BizException(4003, "无权访问该报警记录");
            }
        }
        return alarm;
    }

    private AlarmVo toAlarmVo(Alarm a) {
        Long targetId = a.getTargetId();
        Ward ward = targetId != null ? wardRepo.findById(targetId).orElse(null) : null;
        Device device = ward != null ? ward.getDevice() : null;
        Family family = device != null && device.getFamilyId() != null
                ? familyRepo.findById(device.getFamilyId()).orElse(null) : null;
        ClientUser guardian = device != null ? device.getGuardian() : null;

        FamilyRef fRef = family != null
                ? new FamilyRef(family.getId(), family.getAddress(), family.getName())
                : new FamilyRef(null, "未知地址", "未知社区");
        MemberRef mRef = ward != null
                ? new MemberRef(ward.getName(), calcAge(ward.getBirthday()))
                : new MemberRef("未知成员", null);
        GuardianRef gRef = guardian != null
                ? new GuardianRef(guardian.getName(), guardian.getMobile())
                : new GuardianRef("未知监护人", null);

        String handleNurse = resolveUserDisplayName(a.getHandledBy());
        return new AlarmVo(a.getId(), fmt(a.getOccurredAt()), alarmTypeLabel(a.getAlarmType()),
                alarmStatusStr(a.getHandleStatus()), fRef, mRef, gRef,
                fmt(a.getHandledAt()), handleNurse, a.getHandleRemark());
    }

    private ApptVo toApptVo(ServiceOrder o) {
        Ward ward = o.getMemberId() != null ? wardRepo.findById(o.getMemberId()).orElse(null) : null;
        Family family = o.getFamilyId() != null ? familyRepo.findById(o.getFamilyId()).orElse(null) : null;
        ClientUser guardian = o.getGuardianId() != null
                ? clientUserRepo.findById(o.getGuardianId()).orElse(null) : null;

        FamilyRef fRef = family != null
                ? new FamilyRef(family.getId(), family.getAddress(), family.getName())
                : new FamilyRef(null, null, null);
        MemberRef mRef = ward != null
                ? new MemberRef(ward.getName(), calcAge(ward.getBirthday()))
                : new MemberRef(null, null);
        GuardianRef gRef = guardian != null
                ? new GuardianRef(guardian.getName(), guardian.getMobile())
                : new GuardianRef(null, null);

        String doctorName = resolveUserDisplayName(o.getCreatedById());
        String typeLabel = o.getDisplayType() != null ? o.getDisplayType()
                : (o.getOrderType() != null ? o.getOrderType().name() : null);

        return new ApptVo(
                o.getId(), typeLabel, apptStatusStr(o.getStatus()),
                doctorName, o.getNurseName(),
                fRef, mRef, gRef,
                fmt(o.getAppointmentTime()), o.getRequirement(),
                o.getPayAmount(), o.getPayStatus(),
                fmt(o.getVisitTime()), o.getVisitRemark(),
                o.getStatus() == ServiceOrderStatus.COMPLETED ? fmt(o.getVisitTime()) : null,
                o.getDispatchedBy(),
                o.getContactName(), o.getContactPhone(),
                o.getServiceAddress(), o.getMedicineList());
    }

    private MemberVo toMemberVo(Ward w) {
        String deviceId = null;
        String deviceStatus = null;
        if (w.getDevice() != null) {
            deviceId = w.getDevice().getDeviceId();
            deviceStatus = w.getDevice().getStatus() != null
                    ? w.getDevice().getStatus().name().toLowerCase() : null;
        }
        return new MemberVo(w.getMemberId(), w.getName(), calcAge(w.getBirthday()),
                w.getGender() != null ? w.getGender().name() : null,
                w.getMobile(), w.getChronicDisease(), w.getRemark(), deviceId, deviceStatus);
    }

    private DeviceVo toDeviceVo(Device d) {
        List<MemberVo> members = d.getWards() != null
                ? d.getWards().stream().map(this::toMemberVo).collect(Collectors.toList())
                : List.of();
        return new DeviceVo(d.getDeviceId(), d.getDeviceId(),
                d.getHomeLocation(), d.getAddress(),
                d.getStatus() != null ? d.getStatus().name().toLowerCase() : "unknown",
                fmt(d.getBindTime()), members);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // ALARMS
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadMiniappImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(4000, "Image file is required");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(StringUtils.cleanPath(originalName));
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

            String configuredBaseUrl = appProperties.getUpload().getPublicBaseUrl();
            String baseUrl = StringUtils.hasText(configuredBaseUrl)
                    ? configuredBaseUrl.replaceAll("/+$", "")
                    : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            String publicPath = appProperties.getUpload().getPublicPath();
            String normalizedPublicPath = (publicPath == null || publicPath.isBlank()) ? "/uploads" : publicPath;
            if (!normalizedPublicPath.startsWith("/")) {
                normalizedPublicPath = "/" + normalizedPublicPath;
            }
            normalizedPublicPath = normalizedPublicPath.replaceAll("/+$", "");

            return ApiResponse.ok(baseUrl + normalizedPublicPath + "/miniapp/" + day + "/" + filename);
        } catch (IOException ex) {
            throw new BizException(5000, "Failed to store uploaded image");
        }
    }

    @GetMapping("/alarms")
    public ApiResponse<List<AlarmVo>> listAlarms(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String memberId) {

        Long memberIdLong = parseLongParam(memberId);
        List<Alarm> alarms;
        if (shouldRestrictAlarmAccess()) {
            Set<Long> memberIds = accessibleMemberIds();
            if (memberIdLong != null) {
                if (!memberIds.contains(memberIdLong)) {
                    return ApiResponse.ok(List.of());
                }
                alarms = alarmRepo.findByTargetId(memberIdLong);
            } else {
                alarms = memberIds.isEmpty()
                        ? List.of()
                        : alarmRepo.findByTargetIdIn(new ArrayList<>(memberIds));
            }
        } else {
            alarms = memberIdLong != null
                    ? alarmRepo.findByTargetId(memberIdLong)
                    : alarmRepo.findAllByOrderByOccurredAtDesc();
        }

        if (status != null && !status.isBlank()) {
            AlarmHandleStatus hs = parseAlarmStatus(status);
            alarms = alarms.stream().filter(a -> a.getHandleStatus() == hs).collect(Collectors.toList());
        }
        if (startDate != null && !startDate.isBlank()) {
            Instant s = LocalDate.parse(startDate).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            alarms = alarms.stream().filter(a -> a.getOccurredAt() != null && !a.getOccurredAt().isBefore(s)).collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isBlank()) {
            Instant e = LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            alarms = alarms.stream().filter(a -> a.getOccurredAt() != null && a.getOccurredAt().isBefore(e)).collect(Collectors.toList());
        }

        List<AlarmVo> vos = alarms.stream().map(this::toAlarmVo).collect(Collectors.toList());

        if (keyword != null && !keyword.isBlank()) {
            vos = vos.stream().filter(v ->
                    (v.member().name() != null && v.member().name().contains(keyword)) ||
                    (v.guardian().name() != null && v.guardian().name().contains(keyword)) ||
                    (v.family().community() != null && v.family().community().contains(keyword)) ||
                    (v.family().address() != null && v.family().address().contains(keyword))
            ).collect(Collectors.toList());
        }
        return ApiResponse.ok(vos);
    }

    @GetMapping("/alarms/{id}")
    public ApiResponse<AlarmVo> getAlarm(@PathVariable Long id) {
        return ApiResponse.ok(toAlarmVo(requireAccessibleAlarm(id)));
    }

    @PostMapping("/alarms/{id}/handle")
    public ApiResponse<Void> handleAlarm(@PathVariable Long id,
                                          @RequestBody HandleAlarmRequest body) {
        Alarm alarm = requireAccessibleAlarm(id);
        alarm.setHandleStatus(AlarmHandleStatus.HANDLED);
        alarm.setHandledBy(SecurityUtil.currentUserId());
        alarm.setHandledAt(Instant.now());
        if (body.remark() != null) alarm.setHandleRemark(body.remark());
        alarmRepo.save(alarm);
        return ApiResponse.ok(null);
    }

    @PostMapping("/alarms/{id}/ignore")
    public ApiResponse<Void> ignoreAlarm(@PathVariable Long id) {
        Alarm alarm = requireAccessibleAlarm(id);
        alarm.setHandleStatus(AlarmHandleStatus.IGNORED);
        alarm.setHandledBy(SecurityUtil.currentUserId());
        alarm.setHandledAt(Instant.now());
        alarmRepo.save(alarm);
        return ApiResponse.ok(null);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // APPOINTMENTS
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/appointments")
    public ApiResponse<List<ApptVo>> listAppointments(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Long uid = SecurityUtil.currentUserId();
        String role = SecurityUtil.currentRole();
        List<ServiceOrder> orders;

        if ("GUARDIAN".equals(role)) {
            Optional<ClientUser> cu = currentClientUser();
            if (cu.isEmpty()) return ApiResponse.ok(List.of());
            Long guardianClientId = cu.get().getId();
            orders = orderRepo.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                    .filter(o -> guardianClientId.equals(o.getGuardianId()))
                    .collect(Collectors.toList());
        } else if ("CAREGIVER".equals(role) || "NURSE".equals(role) || "DOCTOR".equals(role)) {
            // 閸栫粯濮㈢粩顖ょ窗閸欘亞婀呴崚鍡涘帳缂佹瑨鍤滃杈╂畱妫板嫮瀹抽敍鍫滅瑝閸?pending 瀵板懎顦╅悶鍡礆
            // 已派给自己的订单 + 同机构未派单的待接单订单
            Long staffOrgId = SecurityUtil.currentOrgId();
            List<ServiceOrder> myOrders = orderRepo.findByNurseIdAndDeletedFalseOrderByCreatedAtDesc(uid);
            List<ServiceOrder> unassigned = staffOrgId != null
                    ? orderRepo.findByOrgIdAndNurseIdIsNullAndDeletedFalseOrderByCreatedAtDesc(staffOrgId)
                    : List.of();
            Set<Long> seen = new HashSet<>();
            orders = new ArrayList<>();
            for (ServiceOrder so : myOrders) { if (seen.add(so.getId())) orders.add(so); }
            for (ServiceOrder so : unassigned) { if (seen.add(so.getId())) orders.add(so); }
            orders.sort(Comparator.comparing(ServiceOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("INSTITUTION".equals(role)) {
            Long orgId = SecurityUtil.currentOrgId();
            orders = orgId != null
                    ? orderRepo.findByOrgIdAndDeletedFalseOrderByCreatedAtDesc(orgId)
                    : List.of();
        } else {
            orders = orderRepo.findAllByDeletedFalseOrderByCreatedAtDesc();
        }

        if (!"all".equals(status)) {
            ServiceOrderStatus sos = switch (status) {
                case "accepted" -> ServiceOrderStatus.ACCEPTED;
                case "completed" -> ServiceOrderStatus.COMPLETED;
                case "cancelled" -> ServiceOrderStatus.CANCELED;
                default -> ServiceOrderStatus.PENDING;
            };
            orders = orders.stream().filter(o -> o.getStatus() == sos).collect(Collectors.toList());
        }
        if (startDate != null && !startDate.isBlank()) {
            try {
                Instant start = LocalDate.parse(startDate).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
                orders = orders.stream()
                        .filter(o -> o.getAppointmentTime() == null || !o.getAppointmentTime().isBefore(start))
                        .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
            Instant end = LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            orders = orders.stream()
                    .filter(o -> o.getAppointmentTime() == null || o.getAppointmentTime().isBefore(end))
                    .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }

        List<ApptVo> vos = orders.stream().map(this::toApptVo).collect(Collectors.toList());
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase(Locale.ROOT);
            vos = vos.stream().filter(v ->
                    (v.type() != null && v.type().toLowerCase(Locale.ROOT).contains(lower)) ||
                    (v.requirement() != null && v.requirement().toLowerCase(Locale.ROOT).contains(lower)) ||
                    (v.member() != null && v.member().name() != null && v.member().name().toLowerCase(Locale.ROOT).contains(lower)) ||
                    (v.guardian() != null && v.guardian().name() != null && v.guardian().name().toLowerCase(Locale.ROOT).contains(lower)) ||
                    (v.family() != null && v.family().address() != null && v.family().address().toLowerCase(Locale.ROOT).contains(lower)) ||
                    (v.acceptNurse() != null && v.acceptNurse().toLowerCase(Locale.ROOT).contains(lower))
            ).collect(Collectors.toList());
        }
        return ApiResponse.ok(vos);
    }

    @PostMapping("/appointments")
    public ApiResponse<ApptVo> createAppointment(@Valid @RequestBody CreateApptRequest body) {
        ServiceOrder o = new ServiceOrder();
        o.setDisplayType(body.type());
        String role = SecurityUtil.currentRole();
        Long currentUid = SecurityUtil.currentUserId();

        Long familyId = body.familyId();
        Long guardianId = body.guardianId();
        Long memberId = body.memberId();

        if ("GUARDIAN".equals(role)) {
            guardianId = currentUid;
            if (familyId == null) {
                Family family = primaryFamilyOfGuardian(currentUid);
                familyId = family != null ? family.getId() : null;
            }
            if (memberId == null) {
                List<Ward> wards = wardRepo.findByDeviceGuardianId(currentUid);
                if (!wards.isEmpty()) {
                    memberId = wards.get(0).getMemberId();
                }
            }
        }

        o.setMemberId(memberId);
        o.setFamilyId(familyId);
        o.setGuardianId(guardianId);
        o.setCreatedById(SecurityUtil.currentUserId());

        if (familyId != null) {
            familyRepo.findById(familyId).ifPresent(f -> o.setOrgId(f.getOrgId()));
        }
        if (o.getOrgId() == null && guardianId != null) {
            familyRepo.findByGuardiansId(guardianId).stream()
                    .filter(f -> f.getOrgId() != null)
                    .findFirst()
                    .ifPresent(f -> o.setOrgId(f.getOrgId()));
        }
        if (body.appointTime() != null) {
            try {
                o.setAppointmentTime(LocalDateTime.parse(body.appointTime(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ignored) {}
        }
        o.setRequirement(body.requirement());
        if (body.contactName() != null && !body.contactName().isBlank())
            o.setContactName(body.contactName().trim());
        if (body.contactPhone() != null && !body.contactPhone().isBlank())
            o.setContactPhone(body.contactPhone().trim());
        orderRepo.save(o);
        return ApiResponse.ok(toApptVo(o));
    }

    @GetMapping("/appointments/{id}")
    public ApiResponse<ApptVo> getAppointment(@PathVariable Long id) {
        ServiceOrder o = orderRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        return ApiResponse.ok(toApptVo(o));
    }

    @PutMapping("/appointments/{id}/accept")
    public ApiResponse<Void> acceptAppointment(@PathVariable Long id) {
        ServiceOrder o = orderRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        Long uid = SecurityUtil.currentUserId();
        String nurseName = resolveUserDisplayName(uid);
        String nursePhone = resolveUserPhone(uid);
        o.setStatus(ServiceOrderStatus.ACCEPTED);
        o.setNurseId(uid);
        o.setNurseName(nurseName);
        o.setNursePhone(nursePhone);
        o.setAcceptTime(Instant.now());
        orderRepo.save(o);
        return ApiResponse.ok(null);
    }

    @PutMapping("/appointments/{id}/dispatch")
    public ApiResponse<Void> dispatchAppointment(@PathVariable Long id,
                                                  @RequestBody DispatchRequest body) {
        ServiceOrder o = orderRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        ClientUser nurse = body.nurseId() != null ? clientUserRepo.findById(body.nurseId()).orElse(null) : null;
        Long orgId = SecurityUtil.currentOrgId();
        String orgName = orgId != null
                ? orgRepo.findById(orgId).map(Organization::getName).orElse("机构") : "机构";
        o.setStatus(ServiceOrderStatus.PENDING);
        o.setNurseId(body.nurseId());
        o.setNurseName(body.nurseName() != null ? body.nurseName() : (nurse != null ? nurse.getName() : null));
        o.setNursePhone(nurse != null ? nurse.getMobile() : null);
        o.setAcceptTime(null);
        o.setDispatchedBy(orgName);
        orderRepo.save(o);
        return ApiResponse.ok(null);
    }

    @PostMapping("/appointments/{id}/visit-record")
    public ApiResponse<Void> submitVisitRecord(@PathVariable Long id,
                                                @RequestBody VisitRecordRequest body) {
        ServiceOrder o = orderRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        if (body.visitTime() != null) {
            try {
                o.setVisitTime(LocalDateTime.parse(body.visitTime(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ignored) {
                o.setVisitTime(Instant.now());
            }
        } else {
            o.setVisitTime(Instant.now());
        }
        o.setPayAmount(body.payAmount());
        o.setPayStatus(body.payStatus());
        o.setVisitRemark(body.remark());
        o.setStatus(ServiceOrderStatus.COMPLETED);
        orderRepo.save(o);
        return ApiResponse.ok(null);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // INSTITUTION (閺堢儤鐎粻锛勬倞缁旑垯绗撶仦鐐村复閸?
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    /** GET /api/mini/institution/nurses 閳?閺堫剚婧€閺嬪嫬灏伴幎銈勬眽閸涙ê鍨悰顭掔礄ClientUser role=CAREGIVER閿?*/
    @GetMapping("/institution/nurses")
    public ApiResponse<List<NurseListItem>> institutionNurses() {
        Long orgId = SecurityUtil.currentOrgId();
        if (orgId == null) return ApiResponse.ok(List.of());
        return ApiResponse.ok(
                clientUserRepo.findByOrgId(orgId).stream()
                        .filter(u -> u.getRole() == ClientUserRole.CAREGIVER)
                        .map(u -> new NurseListItem(u.getId(), u.getName(), u.getMobile(), "caregiver", true))
                        .collect(Collectors.toList())
        );
    }

    /** GET /api/mini/institution/families 閳?閺堫剚婧€閺嬪嫮绮︾€规艾顔嶆惔顓炲灙鐞?*/
    @GetMapping("/institution/families")
    public ApiResponse<List<FamilyVo>> institutionFamilies() {
        Long orgId = SecurityUtil.currentOrgId();
        if (orgId == null) return ApiResponse.ok(List.of());
        return ApiResponse.ok(
                familyRepo.findByOrgId(orgId).stream()
                        .map(f -> buildFamilyVo(f, false))
                        .collect(Collectors.toList())
        );
    }

    @GetMapping("/nurse-list")
    public ApiResponse<List<NurseListItem>> nurseList(
            @RequestParam(required = false) Long orgId) {
        Long ctxOrgId = orgId != null ? orgId : SecurityUtil.currentOrgId();
        return ApiResponse.ok(clientUserRepo.findAll().stream()
                .filter(u -> u.getRole() == ClientUserRole.CAREGIVER
                        && (ctxOrgId == null || ctxOrgId.equals(u.getOrgId())))
                .map(u -> new NurseListItem(u.getId(), u.getName(), u.getMobile(),
                        "caregiver", true))
                .collect(Collectors.toList()));
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // DEVICES
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @PostMapping("/device/bind")
    public ApiResponse<DeviceVo> bindDevice(@Valid @RequestBody BindDeviceRequest body) {
        Device device = deviceRepo.findById(body.deviceCode()).orElseGet(() -> {
            Device d = new Device();
            d.setDeviceId(body.deviceCode());
            d.setCreatedAt(Instant.now());
            return d;
        });
        if (device.getGuardian() != null) {
            throw new BizException(4001, "设备已被绑定");
        }
        if (body.address() != null) device.setAddress(body.address());
        if (body.location() != null) device.setHomeLocation(body.location());
        if (body.latitude() != null) device.setLatitude(body.latitude());
        if (body.longitude() != null) device.setLongitude(body.longitude());
        device.setBindTime(Instant.now());

        Optional<ClientUser> cuOpt = currentClientUser();
        cuOpt.ifPresent(cu -> {
            device.setGuardian(cu);
            List<Family> families = familyRepo.findByGuardiansId(cu.getId());
            if (!families.isEmpty()) {
                Family family = families.get(0);
                device.setFamilyId(family.getId());
                if (body.address() != null && family.getAddress() == null) {
                    family.setAddress(body.address());
                    familyRepo.save(family);
                }
            }
        });
        deviceRepo.save(device);
        return ApiResponse.ok(toDeviceVo(device));
    }

    @PostMapping("/device/unbind")
    public ApiResponse<Void> unbindDevice(@RequestBody UnbindDeviceRequest body) {
        if (body.deviceId() == null) throw new BizException(4000, "deviceId不能为空");
        Device device = deviceRepo.findById(body.deviceId())
                .orElseThrow(() -> new BizException(4004, "设备不存在"));
        device.setGuardian(null);
        device.setBindTime(null);
        deviceRepo.save(device);
        return ApiResponse.ok(null);
    }

    @GetMapping("/device/list")
    public ApiResponse<List<DeviceVo>> deviceList() {
        String role = SecurityUtil.currentRole();
        List<Device> devices;
        if ("GUARDIAN".equals(role)) {
            Optional<ClientUser> cu = currentClientUser();
            devices = cu.isEmpty() ? List.of() : deviceRepo.findByGuardianId(cu.get().getId());
        } else {
            Long orgId = SecurityUtil.currentOrgId();
            String orgName = orgId != null
                    ? orgRepo.findById(orgId).map(Organization::getName).orElse(null) : null;
            devices = orgId != null
                    ? deviceRepo.findByOrgIdOrOrgName(orgId, orgName != null ? orgName : "")
                    : deviceRepo.findAll();
        }
        return ApiResponse.ok(devices.stream().map(this::toDeviceVo).collect(Collectors.toList()));
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // FAMILIES
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    /** 鏉╂柨娲栬ぐ鎾冲閻劍鍩涢張澶嬫綀閺屻儳婀呴惃鍕啀鎼搭厼鍨悰?     *  - CAREGIVER: 閸欘亞婀呯紒鎴濈暰閸掓媽鍤滃鎲嬬礄caregiverId = currentUserId閿涘娈戠€硅泛娑?     *  - INSTITUTION: 閻婀伴張鐑樼€?orgId 閻ㄥ嫬鍙忛柈銊ヮ啀鎼?     *  - 閸忔湹绮? 閸忋劑鍎?     */
    private List<Family> allowedFamilies() {
        String role = SecurityUtil.currentRole();
        if ("GUARDIAN".equals(role)) {
            return currentClientUser()
                    .map(cu -> familyRepo.findByGuardiansId(cu.getId()))
                    .orElse(List.of());
        }
        if ("CAREGIVER".equals(role)) {
            Long uid = SecurityUtil.currentUserId();
            return uid != null ? familyRepo.findByCaregiverId(uid) : List.of();
        }
        if ("INSTITUTION".equals(role)) {
            Long orgId = SecurityUtil.currentOrgId();
            return orgId != null ? familyRepo.findByOrgId(orgId) : List.of();
        }
        return familyRepo.findAll();
    }

    @GetMapping("/family/map-list")
    public ApiResponse<List<FamilyVo>> familyMapList() {
        return ApiResponse.ok(allowedFamilies().stream()
                .map(f -> buildFamilyVo(f, true))
                .collect(Collectors.toList()));
    }

    @GetMapping("/family/list")
    public ApiResponse<List<FamilyVo>> familyList(
            @RequestParam(defaultValue = "") String keyword) {
        List<FamilyVo> vos = allowedFamilies().stream()
                .map(f -> buildFamilyVo(f, false))
                .collect(Collectors.toList());
        if (!keyword.isBlank()) {
            vos = vos.stream().filter(f ->
                    (f.address() != null && f.address().contains(keyword)) ||
                    (f.community() != null && f.community().contains(keyword)) ||
                    (f.guardian() != null && f.guardian().name() != null && f.guardian().name().contains(keyword)) ||
                    f.members().stream().anyMatch(m -> m.name() != null && m.name().contains(keyword))
            ).collect(Collectors.toList());
        }
        return ApiResponse.ok(vos);
    }

    @GetMapping("/family/{id}")
    public ApiResponse<FamilyVo> familyDetail(@PathVariable Long id) {
        Family f = familyRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "家庭档案不存在"));
        return ApiResponse.ok(buildFamilyVo(f, false));
    }

    private FamilyVo buildFamilyVo(Family f, boolean checkAlarm) {
        List<Device> devices = deviceRepo.findByFamilyId(f.getId());
        List<Ward> wards = devices.stream()
                .filter(d -> d.getWards() != null)
                .flatMap(d -> d.getWards().stream())
                .collect(Collectors.toList());

        Double lat = devices.stream().map(Device::getLatitude).filter(Objects::nonNull).findFirst().orElse(null);
        Double lng = devices.stream().map(Device::getLongitude).filter(Objects::nonNull).findFirst().orElse(null);

        boolean hasAlarm = false;
        if (checkAlarm && !wards.isEmpty()) {
            List<Long> memberIds = wards.stream().map(Ward::getMemberId).collect(Collectors.toList());
            hasAlarm = !alarmRepo.findByHandleStatusAndTargetIdIn(AlarmHandleStatus.UNHANDLED, memberIds).isEmpty();
        }

        ClientUser guardian = f.getGuardians() != null && !f.getGuardians().isEmpty()
                ? f.getGuardians().get(0) : null;
        GuardianRef gRef = guardian != null
                ? new GuardianRef(guardian.getName(), guardian.getMobile()) : null;
        List<MemberVo> members = wards.stream().map(this::toMemberVo).collect(Collectors.toList());

        return new FamilyVo(f.getId(), f.getName(), f.getAddress(), f.getName(),
                lat, lng, hasAlarm,
                hasAlarm ? "重点关注" : "一般关注",
                hasAlarm ? "alarm" : "normal",
                gRef, members);
    }

    @GetMapping("/family/{familyId}/member/{memberId}/history")
    public ApiResponse<List<HistoryPoint>> memberHistory(
            @PathVariable Long familyId, @PathVariable Long memberId,
            @RequestParam(defaultValue = "heart_rate") String type) {

        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(24));
        List<HealthData> records = healthDataRepo
                .findByMemberIdAndRecordTimeBetweenOrderByRecordTimeAsc(memberId, start, end);

        return ApiResponse.ok(records.stream().map(h -> {
            String time = fmt(h.getRecordTime());
            double value = switch (type) {
                case "breath" -> h.getRespRateAvg() != null ? h.getRespRateAvg() : 0.0;
                case "activity" -> (h.getFallStatus() != null && h.getFallStatus()) ? 0.0 : 50.0;
                default -> h.getHeartRateAvg() != null ? h.getHeartRateAvg() : 0.0;
            };
            return new HistoryPoint(time, value);
        }).collect(Collectors.toList()));
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // MEMBERS (Wards)
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/member/list")
    public ApiResponse<List<MemberVo>> memberList() {
        String role = SecurityUtil.currentRole();
        List<Ward> wards;
        if ("GUARDIAN".equals(role)) {
            Optional<ClientUser> cu = currentClientUser();
            wards = cu.map(c -> wardRepo.findByDeviceGuardianId(c.getId())).orElse(List.of());
        } else {
            Long orgId = SecurityUtil.currentOrgId();
            wards = orgId != null ? wardRepo.findByDeviceFamilyOrgId(orgId) : wardRepo.findAll();
        }
        return ApiResponse.ok(wards.stream().map(this::toMemberVo).collect(Collectors.toList()));
    }

    @PostMapping("/member/create")
    public synchronized ApiResponse<Map<String, Object>> createMember(@RequestBody CreateMemberRequest body) {
        long nextId = wardRepo.findTopByOrderByMemberIdDesc()
                .map(ward -> ward.getMemberId() + 1).orElse(1L);
        Ward w = new Ward();
        w.setMemberId(nextId);
        if (body.name() != null) w.setName(body.name());
        if (body.mobile() != null) w.setMobile(body.mobile());
        if (body.gender() != null) {
            try { w.setGender(Gender.valueOf(body.gender().toUpperCase())); } catch (Exception ignored) {}
        }
        if (body.birthday() != null) {
            try {
                w.setBirthday(LocalDate.parse(body.birthday())
                        .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ignored) {}
        }
        if (body.height() != null) w.setHeight(body.height());
        if (body.weight() != null) w.setWeight(body.weight());
        if (body.chronicDisease() != null) w.setChronicDisease(body.chronicDisease());
        if (body.remark() != null) w.setRemark(body.remark());
        if (body.emergencyPhone() != null) w.setEmergencyPhone(body.emergencyPhone());
        if (body.deviceId() != null) {
            deviceRepo.findById(body.deviceId()).ifPresent(w::setDevice);
        } else if ("GUARDIAN".equals(SecurityUtil.currentRole())) {
            currentClientUser().ifPresent(cu -> {
                List<Device> bound = deviceRepo.findByGuardianId(cu.getId());
                if (!bound.isEmpty()) w.setDevice(bound.get(0));
            });
        }
        wardRepo.save(w);
        return ApiResponse.ok(Map.of("success", true, "id", nextId));
    }

    @PostMapping("/member/{id}/update")
    public ApiResponse<Void> updateMember(@PathVariable Long id,
                                           @RequestBody CreateMemberRequest body) {
        Ward w = wardRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "成员不存在"));
        if (body.name() != null) w.setName(body.name());
        if (body.mobile() != null) w.setMobile(body.mobile());
        if (body.gender() != null) {
            try { w.setGender(Gender.valueOf(body.gender().toUpperCase())); } catch (Exception ignored) {}
        }
        if (body.birthday() != null) {
            try {
                w.setBirthday(LocalDate.parse(body.birthday())
                        .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ignored) {}
        }
        if (body.height() != null) w.setHeight(body.height());
        if (body.weight() != null) w.setWeight(body.weight());
        if (body.chronicDisease() != null) w.setChronicDisease(body.chronicDisease());
        if (body.remark() != null) w.setRemark(body.remark());
        if (body.emergencyPhone() != null) w.setEmergencyPhone(body.emergencyPhone());
        wardRepo.save(w);
        return ApiResponse.ok(null);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // MONITOR
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/monitor/realtime")
    public ApiResponse<MonitorVo> realtimeMonitor(@RequestParam(required = false) String memberId) {
        Long id = parseLongParam(memberId);
        if (id == null) return ApiResponse.ok(null);
        Ward ward = wardRepo.findById(id).orElse(null);
        if (ward == null || ward.getDevice() == null) return ApiResponse.ok(null);
        String deviceId = ward.getDevice().getDeviceId();
        Map<String, Object> row = tdengineService.queryLatestRecord("health_monitor", deviceId);
        if (row == null) return ApiResponse.ok(null);
        Integer heartRate = row.get("heart_rate") != null ? ((Number) row.get("heart_rate")).intValue() : null;
        Integer breathRate = row.get("breath_rate") != null ? ((Number) row.get("breath_rate")).intValue() : null;
        Boolean isFall = row.get("is_fall") != null ? (Boolean) row.get("is_fall") : null;
        String ts = row.get("ts") != null ? row.get("ts").toString() : null;
        return ApiResponse.ok(new MonitorVo(
                heartRate, breathRate,
                isFall, null,
                null, ts,
                heartStatus(heartRate != null ? heartRate.doubleValue() : null),
                breathStatus(breathRate != null ? breathRate.doubleValue() : null),
                Boolean.TRUE.equals(isFall) ? "异常" : "正常"));
    }

    @GetMapping("/monitor/history")
    public ApiResponse<HistoryDataVo> historyMonitor(
            @RequestParam Long memberId,
            @RequestParam(defaultValue = "heartRate") String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Instant end = endDate != null
                ? LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()
                : Instant.now();
        Instant start = startDate != null
                ? LocalDate.parse(startDate).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()
                : end.minus(Duration.ofHours(24));

        Ward ward = wardRepo.findById(memberId).orElse(null);
        if (ward == null || ward.getDevice() == null) {
            return ApiResponse.ok(new HistoryDataVo(List.of(), List.of(), "次/分"));
        }
        String deviceId = ward.getDevice().getDeviceId();
        List<Map<String, Object>> rows = tdengineService.queryDeviceData("health_monitor", deviceId, start, end);
        List<String> hours = rows.stream()
                .map(r -> r.get("ts") != null ? r.get("ts").toString() : "")
                .collect(Collectors.toList());
        List<Double> values = rows.stream().map(r -> {
            Object v = "breathRate".equals(type) ? r.get("breath_rate") : r.get("heart_rate");
            return v != null ? ((Number) v).doubleValue() : 0.0;
        }).collect(Collectors.toList());
        return ApiResponse.ok(new HistoryDataVo(hours, values, "次/分"));
    }

    private String heartStatus(Double v) {
        if (v == null) return "未知";
        return (v < 60 || v > 100) ? "异常" : "正常";
    }

    private String breathStatus(Double v) {
        if (v == null) return "未知";
        return (v < 12 || v > 20) ? "异常" : "正常";
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // STAFF PROFILE
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/guardian/profile")
    public ApiResponse<Map<String, Object>> guardianProfile() {
        ClientUser cu = requireCurrentClientUser();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", cu.getId());
        info.put("name", cu.getName());
        info.put("phone", cu.getMobile());
        info.put("role", "guardian");
        return ApiResponse.ok(info);
    }

    @PutMapping("/guardian/profile")
    public ApiResponse<Map<String, Object>> updateGuardianProfile(@RequestBody GuardianProfileUpdateRequest body) {
        ClientUser cu = requireCurrentClientUser();
        if (body.name() != null && !body.name().isBlank()) {
            cu.setName(body.name().trim());
        }
        if (body.phone() != null && !body.phone().isBlank()) {
            String phone = body.phone().trim();
            if (!phone.equals(cu.getMobile())) {
                clientUserRepo.findByMobile(phone)
                        .filter(existing -> !existing.getId().equals(cu.getId()))
                        .ifPresent(existing -> {
                            throw new BizException(4001, "手机号已被其他账号使用");
                        });
                cu.setMobile(phone);
            }
        }
        clientUserRepo.save(cu);
        return guardianProfile();
    }

    @GetMapping("/staff/profile")
    public ApiResponse<Map<String, Object>> staffProfile() {
        Long uid = SecurityUtil.currentUserId();
        String role = SecurityUtil.currentRole();

        if ("CAREGIVER".equals(role) || "INSTITUTION".equals(role)) {
            ClientUser cu = clientUserRepo.findById(uid)
                    .orElseThrow(() -> new BizException(4004, "用户不存在"));
            String orgName = resolveOrgName(cu.getOrgId());
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", cu.getId());
            info.put("name", cu.getName());
            info.put("phone", cu.getMobile());
            info.put("department", cu.getDepartment());
            info.put("title", cu.getTitle());
            info.put("idCard", cu.getIdCard());
            info.put("orgName", orgName);
            info.put("orgId", cu.getOrgId());
            info.put("role", "INSTITUTION".equals(role) ? "机构管理员" : "医护人员");
            return ApiResponse.ok(info);
        }

        UserAccount user = userRepo.findById(uid)
                .orElseThrow(() -> new BizException(4004, "用户不存在"));
        Long orgId = SecurityUtil.currentOrgId();
        String orgName = orgId != null
                ? orgRepo.findById(orgId).map(Organization::getName).orElse("") : "";

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("name", user.getUsername());
        info.put("phone", user.getPhone());
        info.put("department", null);
        info.put("title", null);
        info.put("idCard", null);
        info.put("orgName", orgName);
        info.put("orgId", orgId);
        info.put("role", resolveStaffRoleLabel(user.getRole()));
        return ApiResponse.ok(info);
    }

    @PutMapping("/staff/profile")
    public ApiResponse<Map<String, Object>> updateStaffProfile(@RequestBody StaffProfileUpdateRequest body) {
        ClientUser cu = requireCurrentClientUser();
        if (body.name() != null && !body.name().isBlank()) {
            cu.setName(body.name().trim());
        }
        if (body.department() != null) {
            cu.setDepartment(body.department().trim());
        }
        if (body.title() != null) {
            cu.setTitle(body.title().trim());
        }
        if (body.idCard() != null) {
            cu.setIdCard(body.idCard().trim());
        }
        clientUserRepo.save(cu);
        return staffProfile();
    }

    @GetMapping("/staff/org-info")
    public ApiResponse<Map<String, Object>> staffOrgInfo() {
        Long orgId = SecurityUtil.currentOrgId();
        if (orgId == null) {
            orgId = currentClientUser().map(ClientUser::getOrgId).orElse(null);
        }
        Organization org = orgId != null ? orgRepo.findById(orgId).orElse(null) : null;
        if (org == null) {
            return ApiResponse.ok(Map.of());
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", org.getName());
        info.put("type", org.getType() != null ? org.getType().name() : null);
        info.put("region", org.getRegion());
        info.put("address", org.getAddress() != null ? org.getAddress() : org.getRegion());
        info.put("phone", org.getContactPhone());
        info.put("license", org.getLicenseNo());
        return ApiResponse.ok(info);
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) return null;
        return orgRepo.findById(orgId).map(Organization::getName).orElse(null);
    }

    private String resolveStaffRoleLabel(UserRole role) {
        if (role == null) {
            return "医护人员";
        }
        return switch (role) {
            case DOCTOR -> "医生";
            case NURSE -> "护士";
            case ADMIN -> "管理员";
            case INSTITUTION -> "机构管理员";
            case GUARDIAN -> "家属";
        };
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // SUMMARY
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/summary/nurse")
    public ApiResponse<SummaryVo> nurseSummary() {
        List<Family> families = allowedFamilies();
        Set<Long> familyIds = families.stream().map(Family::getId).collect(Collectors.toSet());
        Set<Long> memberIds = families.stream()
                .flatMap(f -> deviceRepo.findByFamilyId(f.getId()).stream())
                .flatMap(d -> d.getWards() != null ? d.getWards().stream() : java.util.stream.Stream.<Ward>empty())
                .map(Ward::getMemberId)
                .collect(Collectors.toSet());

        List<ServiceOrder> orders;
        String role = SecurityUtil.currentRole();
        Long uid = SecurityUtil.currentUserId();
        if ("INSTITUTION".equals(role)) {
            Long orgId = SecurityUtil.currentOrgId();
            orders = orgId != null ? orderRepo.findByOrgIdAndDeletedFalseOrderByCreatedAtDesc(orgId) : List.of();
        } else if ("CAREGIVER".equals(role)) {
            orders = uid != null ? orderRepo.findByNurseIdAndDeletedFalseOrderByCreatedAtDesc(uid) : List.of();
        } else {
            orders = orderRepo.findAllByDeletedFalseOrderByCreatedAtDesc().stream()
                    .filter(o -> o.getFamilyId() != null && familyIds.contains(o.getFamilyId()))
                    .collect(Collectors.toList());
        }

        List<Alarm> alarms = memberIds.isEmpty() ? List.of() : alarmRepo.findByTargetIdIn(new ArrayList<>(memberIds));
        int handledAlarms = (int) alarms.stream()
                .filter(a -> a.getHandleStatus() == AlarmHandleStatus.HANDLED)
                .count();
        List<AlarmTypeBar> bars = alarms.stream()
                .collect(Collectors.groupingBy(Alarm::getAlarmType, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new AlarmTypeBar(alarmTypeLabel(e.getKey()), e.getValue().intValue()))
                .collect(Collectors.toList());

        int totalAppt = orders.size();
        int completedAppt = (int) orders.stream()
                .filter(o -> o.getStatus() == ServiceOrderStatus.COMPLETED)
                .count();
        return ApiResponse.ok(new SummaryVo(families.size(), totalAppt, completedAppt, handledAlarms, bars));
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // SERVICE (guardian-facing tab)
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/service/doctor")
    public ApiResponse<Map<String, Object>> doctorInfo() {
        ClientUser guardian = currentClientUser().orElse(null);
        if (guardian == null) {
            return ApiResponse.ok(null);
        }
        Device device = deviceRepo.findByGuardianId(guardian.getId()).stream().findFirst().orElse(null);
        if (device != null && device.getDoctorId() != null) {
            Doctor doctor = doctorRepo.findById(device.getDoctorId()).orElse(null);
            if (doctor != null) {
                Organization org = doctor.getOrgId() != null
                        ? orgRepo.findById(doctor.getOrgId()).orElse(null) : null;
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", doctor.getId());
                info.put("name", doctor.getName());
                info.put("phone", doctor.getMobile());
                info.put("institution", org != null ? org.getName() : null);
                info.put("title", doctor.getTitle());
                info.put("avatar", null);
                info.put("serviceFamilies", 0);
                return ApiResponse.ok(info);
            }
        }

        Family family = null;
        if (device != null && device.getFamilyId() != null) {
            family = familyRepo.findById(device.getFamilyId()).orElse(null);
        }
        if (family == null) {
            List<Family> families = familyRepo.findByGuardiansId(guardian.getId());
            if (!families.isEmpty()) {
                family = families.get(0);
            }
        }
        if (family == null || family.getOrgId() == null) {
            return ApiResponse.ok(null);
        }

        final Long orgId = family.getOrgId();
        ClientUser caregiver = clientUserRepo.findByOrgId(orgId).stream()
                .filter(u -> u.getRole() == ClientUserRole.CAREGIVER)
                .findFirst().orElse(null);
        if (caregiver == null) {
            return ApiResponse.ok(null);
        }
        Organization org = orgRepo.findById(orgId).orElse(null);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", caregiver.getId());
        info.put("name", caregiver.getName());
        info.put("phone", caregiver.getMobile());
        info.put("institution", org != null ? org.getName() : null);
        info.put("title", caregiver.getTitle() != null ? caregiver.getTitle() : "医护人员");
        info.put("avatar", null);
        info.put("serviceFamilies", 0);
        return ApiResponse.ok(info);
    }

    @GetMapping("/service/center")
    public ApiResponse<Map<String, Object>> serviceCenterInfo() {
        Long orgId = SecurityUtil.currentOrgId();
        Organization org = orgId != null ? orgRepo.findById(orgId).orElse(null) : null;
        if (org == null) {
            List<Organization> all = orgRepo.findAll();
            org = all.isEmpty() ? null : all.get(0);
        }
        if (org == null) return ApiResponse.ok(Map.of());
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", org.getId());
        info.put("name", org.getName());
        info.put("address", org.getAddress() != null ? org.getAddress() : org.getRegion());
        info.put("phone", org.getContactPhone());
        info.put("type", org.getType() != null ? org.getType().name() : null);
        return ApiResponse.ok(info);
    }

    @GetMapping("/guardian/alarm-settings")
    public ApiResponse<Map<String, Object>> guardianAlarmSettings() {
        Long guardianId = SecurityUtil.currentUserId();
        GuardianAlarmSetting setting = guardianAlarmSettingRepo.findById(guardianId).orElseGet(() -> {
            GuardianAlarmSetting created = new GuardianAlarmSetting();
            created.setGuardianId(guardianId);
            return guardianAlarmSettingRepo.save(created);
        });
        return ApiResponse.ok(Map.of(
                "hrAlert", setting.isHrAlert(),
                "bpAlert", setting.isBpAlert(),
                "fallAlert", setting.isFallAlert(),
                "bedAlert", setting.isBedAlert()
        ));
    }

    @PutMapping("/guardian/alarm-settings")
    public ApiResponse<Map<String, Object>> updateGuardianAlarmSettings(@RequestBody GuardianAlarmSettingRequest body) {
        Long guardianId = SecurityUtil.currentUserId();
        GuardianAlarmSetting setting = guardianAlarmSettingRepo.findById(guardianId).orElseGet(() -> {
            GuardianAlarmSetting created = new GuardianAlarmSetting();
            created.setGuardianId(guardianId);
            return created;
        });
        if (body.hrAlert() != null) setting.setHrAlert(body.hrAlert());
        if (body.bpAlert() != null) setting.setBpAlert(body.bpAlert());
        if (body.fallAlert() != null) setting.setFallAlert(body.fallAlert());
        if (body.bedAlert() != null) setting.setBedAlert(body.bedAlert());
        guardianAlarmSettingRepo.save(setting);
        return guardianAlarmSettings();
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> submitFeedback(@Valid @RequestBody FeedbackRequest body) {
        FeedbackSubmission feedback = new FeedbackSubmission();
        feedback.setSubmitterId(SecurityUtil.currentUserId());
        feedback.setSubmitterRole(SecurityUtil.currentRole());
        feedback.setType(body.type().trim());
        feedback.setContent(body.content().trim());
        feedbackRepo.save(feedback);
        return ApiResponse.ok(Map.of("success", true, "id", feedback.getId()));
    }

    @PostMapping("/guardian/emergency/call")
    public ApiResponse<Map<String, Object>> emergencyCall(@RequestBody(required = false) EmergencyRequest body) {
        ClientUser guardian = requireCurrentClientUser();
        Long memberId = body != null ? body.memberId() : null;
        Ward ward = memberId != null ? wardRepo.findById(memberId).orElse(null) : null;
        if (ward == null) {
            List<Ward> wards = wardRepo.findByDeviceGuardianId(guardian.getId());
            ward = wards.isEmpty() ? null : wards.get(0);
        }
        Device device = ward != null ? ward.getDevice() : deviceRepo.findByGuardianId(guardian.getId()).stream().findFirst().orElse(null);
        Alarm alarm = alarmService.createManualAlarm(
                ward != null ? ward.getMemberId() : null,
                device != null ? device.getDeviceId() : "SOS-" + guardian.getId(),
                AlarmType.EMERGENCY,
                AlarmLevel.EMERGENCY,
                body != null && body.note() != null ? body.note() : "SOS"
        );
        return ApiResponse.ok(Map.of("alarmId", alarm.getId()));
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // DOCTOR DETAIL (mini-app facing)
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    @GetMapping("/doctor/{id}")
    public ApiResponse<Map<String, Object>> getDoctorDetail(@PathVariable Long id) {
        Doctor doctor = doctorRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "医生不存在"));
        Organization org = doctor.getOrgId() != null
                ? orgRepo.findById(doctor.getOrgId()).orElse(null) : null;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", doctor.getId());
        info.put("name", doctor.getName());
        info.put("phone", doctor.getMobile());
        info.put("title", doctor.getTitle());
        info.put("hospital", org != null ? org.getName() : null);
        info.put("specialty", null);
        info.put("experience", null);
        info.put("patients", null);
        info.put("rating", null);
        info.put("price", null);
        return ApiResponse.ok(info);
    }

    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?    // MEDICINE ORDER (mini-app facing)
    // 閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅查埡鎰ㄦ櫜閳烘劏鏅?
    record MedicineOrderRequest(List<Map<String, Object>> medicines, String address,
                                String contactName, String contactPhone, String deliverAt, String notes) {}

    @PostMapping("/medicine/order")
    public ApiResponse<Map<String, Object>> createMedicineOrder(@RequestBody MedicineOrderRequest body) {
        ServiceOrder o = new ServiceOrder();
        o.setOrderType(ServiceOrderType.MEDICINE_DELIVERY);
        o.setDisplayType("送药服务");

        // Store structured fields
        if (body.medicines() != null && !body.medicines().isEmpty()) {
            String names = body.medicines().stream()
                    .map(m -> (String) m.get("name"))
                    .filter(n -> n != null && !n.isBlank())
                    .collect(Collectors.joining("、"));
            o.setMedicineList(names);
        }
        if (body.address() != null && !body.address().isBlank())
            o.setServiceAddress(body.address().trim());
        if (body.contactName() != null && !body.contactName().isBlank())
            o.setContactName(body.contactName().trim());
        if (body.contactPhone() != null && !body.contactPhone().isBlank())
            o.setContactPhone(body.contactPhone().trim());
        o.setRequirement(body.notes() != null ? body.notes().trim() : null);

        // Set delivery time as appointmentTime
        if (body.deliverAt() != null && !body.deliverAt().isBlank()) {
            try {
                o.setAppointmentTime(LocalDateTime.parse(body.deliverAt(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ignored) {}
        }

        currentClientUser().ifPresent(cu -> {
            o.setGuardianId(cu.getId());
            Family family = primaryFamilyOfGuardian(cu.getId());
            if (family != null) {
                o.setFamilyId(family.getId());
                o.setOrgId(family.getOrgId());
            }
            // Auto-assign bound doctor as nurse
            Device device = deviceRepo.findByGuardianId(cu.getId()).stream().findFirst().orElse(null);
            if (device != null && device.getDoctorId() != null) {
                doctorRepo.findById(device.getDoctorId()).ifPresent(doc -> {
                    o.setNurseName(doc.getName());
                    o.setNursePhone(doc.getMobile());
                });
            }
        });
        o.setCreatedById(SecurityUtil.currentUserId());
        orderRepo.save(o);
        return ApiResponse.ok(Map.of("success", true, "orderId", o.getId()));
    }
}
