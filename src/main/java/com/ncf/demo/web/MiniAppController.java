package com.ncf.demo.web;

import com.ncf.demo.common.BizException;
import com.ncf.demo.domain.*;
import com.ncf.demo.entity.*;
import com.ncf.demo.repository.*;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.service.TdengineService;
import com.ncf.demo.web.dto.mini.NurseListItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mini-app business endpoints — all routes require authentication.
 * All endpoints live under /api/mini/ to avoid collisions with existing admin routes.
 */
@RestController
@RequestMapping("/api/mini")
public class MiniAppController {

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
    private final TdengineService tdengineService;

    public MiniAppController(AlarmRepository alarmRepo, WardRepository wardRepo,
                             DeviceRepository deviceRepo, FamilyRepository familyRepo,
                             ClientUserRepository clientUserRepo, UserRepository userRepo,
                             DoctorRepository doctorRepo, ServiceOrderRepository orderRepo,
                             HealthDataRepository healthDataRepo, OrganizationRepository orgRepo,
                             NewsPostRepository newsRepo, TdengineService tdengineService) {
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
        this.tdengineService = tdengineService;
    }

    // ══════════════════════════════════════════════════════════════════
    // Inner VO / request record types
    // ══════════════════════════════════════════════════════════════════

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
                  String completeTime, String dispatchedBy) {}

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

    record SummaryVo(int serviceFamily, int guardianCount, int todayAlarm,
                     int nurseCount, int totalAppt, int todayVisit) {}

    record HandleAlarmRequest(Boolean calledGuardian, Boolean memberDanger,
                               Boolean handled, String remark) {}

    record CreateApptRequest(@NotBlank String type, Long memberId, Long familyId,
                              Long guardianId, String appointTime, String requirement) {}

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

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

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
            case DEVICE_OFFLINE -> "停止活动报警";
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

        String handleNurse = null;
        if (a.getHandledBy() != null) {
            handleNurse = userRepo.findById(a.getHandledBy())
                    .map(UserAccount::getUsername).orElse(null);
        }
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

        String doctorName = o.getCreatedById() != null
                ? userRepo.findById(o.getCreatedById()).map(UserAccount::getUsername).orElse(null) : null;
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
                o.getDispatchedBy());
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

    // ══════════════════════════════════════════════════════════════════
    // ALARMS
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/alarms")
    public ApiResponse<List<AlarmVo>> listAlarms(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String memberId) {

        Long memberIdLong = parseLongParam(memberId);
        List<Alarm> alarms = memberIdLong != null
                ? alarmRepo.findByTargetId(memberIdLong)
                : alarmRepo.findAllByOrderByOccurredAtDesc();

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
        Alarm alarm = alarmRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "报警记录不存在"));
        return ApiResponse.ok(toAlarmVo(alarm));
    }

    @PostMapping("/alarms/{id}/handle")
    public ApiResponse<Void> handleAlarm(@PathVariable Long id,
                                          @RequestBody HandleAlarmRequest body) {
        Alarm alarm = alarmRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "报警记录不存在"));
        alarm.setHandleStatus(AlarmHandleStatus.HANDLED);
        alarm.setHandledBy(SecurityUtil.currentUserId());
        alarm.setHandledAt(Instant.now());
        if (body.remark() != null) alarm.setHandleRemark(body.remark());
        alarmRepo.save(alarm);
        return ApiResponse.ok(null);
    }

    @PostMapping("/alarms/{id}/ignore")
    public ApiResponse<Void> ignoreAlarm(@PathVariable Long id) {
        Alarm alarm = alarmRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "报警记录不存在"));
        alarm.setHandleStatus(AlarmHandleStatus.IGNORED);
        alarm.setHandledBy(SecurityUtil.currentUserId());
        alarm.setHandledAt(Instant.now());
        alarmRepo.save(alarm);
        return ApiResponse.ok(null);
    }

    // ══════════════════════════════════════════════════════════════════
    // APPOINTMENTS
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/appointments")
    public ApiResponse<List<ApptVo>> listAppointments(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String doctorName) {

        Long uid = SecurityUtil.currentUserId();
        String role = SecurityUtil.currentRole();
        List<ServiceOrder> orders;

        if ("GUARDIAN".equals(role)) {
            Optional<ClientUser> cu = currentClientUser();
            if (cu.isEmpty()) return ApiResponse.ok(List.of());
            Long guardianClientId = cu.get().getId();
            orders = orderRepo.findAllByOrderByCreatedAtDesc().stream()
                    .filter(o -> guardianClientId.equals(o.getGuardianId()))
                    .collect(Collectors.toList());
        } else if ("CAREGIVER".equals(role) || "NURSE".equals(role) || "DOCTOR".equals(role)) {
            // 医护端：只看分配给自己的预约（不含 pending 待处理）
            orders = orderRepo.findByNurseIdOrderByCreatedAtDesc(uid);
        } else if ("INSTITUTION".equals(role)) {
            // 机构端：只看本机构的预约（按 orgId 过滤）
            Long orgId = SecurityUtil.currentOrgId();
            orders = orgId != null
                    ? orderRepo.findByOrgIdOrderByCreatedAtDesc(orgId)
                    : List.of();
        } else {
            orders = orderRepo.findAllByOrderByCreatedAtDesc();
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
        return ApiResponse.ok(orders.stream().map(this::toApptVo).collect(Collectors.toList()));
    }

    @PostMapping("/appointments")
    public ApiResponse<ApptVo> createAppointment(@Valid @RequestBody CreateApptRequest body) {
        ServiceOrder o = new ServiceOrder();
        o.setDisplayType(body.type());
        o.setMemberId(body.memberId());
        o.setFamilyId(body.familyId());
        o.setGuardianId(body.guardianId());
        o.setCreatedById(SecurityUtil.currentUserId());
        // 自动填充 orgId：通过 familyId 查 Family.orgId
        if (body.familyId() != null) {
            familyRepo.findById(body.familyId()).ifPresent(f -> o.setOrgId(f.getOrgId()));
        }
        if (o.getOrgId() == null && body.guardianId() != null) {
            // 备选：通过 guardianId 找家庭
            familyRepo.findByGuardiansId(body.guardianId()).stream()
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
        orderRepo.save(o);
        return ApiResponse.ok(toApptVo(o));
    }

    @GetMapping("/appointments/{id}")
    public ApiResponse<ApptVo> getAppointment(@PathVariable Long id) {
        ServiceOrder o = orderRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        return ApiResponse.ok(toApptVo(o));
    }

    @PutMapping("/appointments/{id}/accept")
    public ApiResponse<Void> acceptAppointment(@PathVariable Long id) {
        ServiceOrder o = orderRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        Long uid = SecurityUtil.currentUserId();
        UserAccount nurse = uid != null ? userRepo.findById(uid).orElse(null) : null;
        o.setStatus(ServiceOrderStatus.ACCEPTED);
        o.setNurseId(uid);
        o.setNurseName(nurse != null ? nurse.getUsername() : null);
        o.setNursePhone(nurse != null ? nurse.getPhone() : null);
        o.setAcceptTime(Instant.now());
        orderRepo.save(o);
        return ApiResponse.ok(null);
    }

    @PutMapping("/appointments/{id}/dispatch")
    public ApiResponse<Void> dispatchAppointment(@PathVariable Long id,
                                                  @RequestBody DispatchRequest body) {
        ServiceOrder o = orderRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        UserAccount nurse = body.nurseId() != null ? userRepo.findById(body.nurseId()).orElse(null) : null;
        Long orgId = SecurityUtil.currentOrgId();
        String orgName = orgId != null
                ? orgRepo.findById(orgId).map(Organization::getName).orElse("机构") : "机构";
        o.setStatus(ServiceOrderStatus.ACCEPTED);
        o.setNurseId(body.nurseId());
        o.setNurseName(body.nurseName() != null ? body.nurseName()
                : (nurse != null ? nurse.getUsername() : null));
        o.setNursePhone(nurse != null ? nurse.getPhone() : null);
        o.setAcceptTime(Instant.now());
        o.setDispatchedBy(orgName);
        orderRepo.save(o);
        return ApiResponse.ok(null);
    }

    @PostMapping("/appointments/{id}/visit-record")
    public ApiResponse<Void> submitVisitRecord(@PathVariable Long id,
                                                @RequestBody VisitRecordRequest body) {
        ServiceOrder o = orderRepo.findById(id)
                .orElseThrow(() -> new BizException(4004, "预约记录不存在"));
        if (body.visitTime() != null) {
            try {
                o.setVisitTime(LocalDateTime.parse(body.visitTime(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ignored) { o.setVisitTime(Instant.now()); }
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

    // ══════════════════════════════════════════════════════════════════
    // INSTITUTION (机构管理端专属接口)
    // ══════════════════════════════════════════════════════════════════

    /** GET /api/mini/institution/nurses — 本机构医护人员列表（ClientUser role=CAREGIVER） */
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

    /** GET /api/mini/institution/families — 本机构绑定家庭列表 */
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
        return ApiResponse.ok(userRepo.findAll().stream()
                .filter(u -> {
                    UserRole r = u.getRole();
                    return (r == UserRole.NURSE || r == UserRole.DOCTOR)
                            && u.getStatus() == UserStatus.ENABLED
                            && (ctxOrgId == null || ctxOrgId.equals(u.getOrgId()));
                })
                .map(u -> new NurseListItem(u.getId(), u.getUsername(), u.getPhone(),
                        u.getRole().name(), true))
                .collect(Collectors.toList()));
    }

    // ══════════════════════════════════════════════════════════════════
    // DEVICES
    // ══════════════════════════════════════════════════════════════════

    @PostMapping("/device/bind")
    public ApiResponse<DeviceVo> bindDevice(@Valid @RequestBody BindDeviceRequest body) {
        // 设备不存在时自动创建（设备码由小程序扫码/手动输入）
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

    // ══════════════════════════════════════════════════════════════════
    // FAMILIES
    // ══════════════════════════════════════════════════════════════════

    /** 返回当前用户有权查看的家庭列表
     *  - CAREGIVER: 只看绑定到自己（caregiverId = currentUserId）的家庭
     *  - INSTITUTION: 看本机构 orgId 的全部家庭
     *  - 其他: 全部
     */
    private List<Family> allowedFamilies() {
        String role = SecurityUtil.currentRole();
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
                hasAlarm ? "重点关注" : "一般",
                hasAlarm ? "报警中" : "正常",
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

    // ══════════════════════════════════════════════════════════════════
    // MEMBERS (Wards)
    // ══════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════
    // MONITOR
    // ══════════════════════════════════════════════════════════════════

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
                "正常"));
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

    // ══════════════════════════════════════════════════════════════════
    // STAFF PROFILE
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/guardian/profile")
    public ApiResponse<Map<String, Object>> guardianProfile() {
        Long uid = SecurityUtil.currentUserId();
        ClientUser cu = clientUserRepo.findById(uid)
                .orElseThrow(() -> new BizException(4004, "用户不存在"));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", cu.getName());
        info.put("phone", cu.getMobile());
        info.put("role", "guardian");
        return ApiResponse.ok(info);
    }

    @GetMapping("/staff/profile")
    public ApiResponse<Map<String, Object>> staffProfile() {
        Long uid = SecurityUtil.currentUserId();
        String role = SecurityUtil.currentRole();

        // CAREGIVER / INSTITUTION 角色 → 从 client_user 表查
        if ("CAREGIVER".equals(role) || "INSTITUTION".equals(role)) {
            ClientUser cu = clientUserRepo.findById(uid)
                    .orElseThrow(() -> new BizException(4004, "用户不存在"));
            String orgName = resolveOrgName(cu.getOrgId());
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", cu.getName());
            info.put("phone", cu.getMobile());
            info.put("orgName", orgName);
            info.put("orgId", cu.getOrgId());
            info.put("role", "INSTITUTION".equals(role) ? "机构管理员" : "医护人员");
            return ApiResponse.ok(info);
        }

        // NURSE / DOCTOR / ADMIN → 从 user_account 表查
        UserAccount user = userRepo.findById(uid)
                .orElseThrow(() -> new BizException(4004, "用户不存在"));
        Long orgId = SecurityUtil.currentOrgId();
        String orgName = orgId != null
                ? orgRepo.findById(orgId).map(Organization::getName).orElse("") : "";
        String roleName = user.getRole() != null ? switch (user.getRole()) {
            case DOCTOR -> "医生";
            case NURSE  -> "护士";
            case ADMIN  -> "管理员";
            default     -> "医护人员";
        } : "医护人员";

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", user.getUsername());
        info.put("phone", user.getPhone());
        info.put("orgName", orgName);
        info.put("orgId", orgId);
        info.put("role", roleName);
        return ApiResponse.ok(info);
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) return null;
        return orgRepo.findById(orgId).map(Organization::getName).orElse(null);
    }

    // ══════════════════════════════════════════════════════════════════
    // SUMMARY
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/summary/nurse")
    public ApiResponse<SummaryVo> nurseSummary() {
        int serviceFamily = (int) familyRepo.count();
        int guardianCount = (int) userRepo.findAll().stream()
                .filter(u -> u.getRole() == UserRole.GUARDIAN).count();
        Instant todayStart = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        int todayAlarm = alarmRepo.findByOccurredAtBetween(todayStart, Instant.now()).stream()
                .filter(a -> a.getHandleStatus() == AlarmHandleStatus.UNHANDLED)
                .mapToInt(a -> 1).sum();
        int nurseCount = (int) userRepo.findAll().stream()
                .filter(u -> u.getRole() == UserRole.NURSE || u.getRole() == UserRole.DOCTOR).count();
        int totalAppt = (int) orderRepo.count();
        int todayVisit = (int) orderRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() == ServiceOrderStatus.COMPLETED
                        && o.getVisitTime() != null
                        && o.getVisitTime().isAfter(todayStart))
                .count();
        return ApiResponse.ok(new SummaryVo(serviceFamily, guardianCount, todayAlarm,
                nurseCount, totalAppt, todayVisit));
    }

    // ══════════════════════════════════════════════════════════════════
    // SERVICE (guardian-facing tab)
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/service/doctor")
    public ApiResponse<Map<String, Object>> doctorInfo() {
        Optional<ClientUser> cu = currentClientUser();
        if (cu.isEmpty()) return ApiResponse.ok(null);
        Device device = deviceRepo.findByGuardianId(cu.get().getId()).stream().findFirst().orElse(null);

        // ── 优先：device.doctorId → Doctor 实体 ──
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

        // ── 降级：通过家庭 orgId → 找机构下的 CAREGIVER 展示 ──
        // 先确定家庭：device.familyId 优先；若为 null，通过 guardianId 查家庭
        Optional<ClientUser> cuOpt = currentClientUser();
        if (cuOpt.isEmpty()) return ApiResponse.ok(null);

        Family family = null;
        if (device != null && device.getFamilyId() != null) {
            family = familyRepo.findById(device.getFamilyId()).orElse(null);
        }
        if (family == null) {
            // 通过监护人 ID 查找关联家庭（注册时自动创建）
            List<Family> families = familyRepo.findByGuardiansId(cuOpt.get().getId());
            if (!families.isEmpty()) family = families.get(0);
        }
        if (family == null || family.getOrgId() == null) return ApiResponse.ok(null);

        final Long orgId = family.getOrgId();
        ClientUser caregiver = clientUserRepo.findByOrgId(orgId).stream()
                .filter(u -> u.getRole() == ClientUserRole.CAREGIVER)
                .findFirst().orElse(null);
        if (caregiver == null) return ApiResponse.ok(null);
        Organization org = orgRepo.findById(orgId).orElse(null);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", caregiver.getId());
        info.put("name", caregiver.getName());
        info.put("phone", caregiver.getMobile());
        info.put("institution", org != null ? org.getName() : null);
        info.put("title", "护理人员");
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
        info.put("address", org.getRegion());
        info.put("phone", org.getContactPhone());
        info.put("type", org.getType() != null ? org.getType().name() : null);
        return ApiResponse.ok(info);
    }

    // ══════════════════════════════════════════════════════════════════
    // DOCTOR DETAIL (mini-app facing)
    // ══════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════
    // MEDICINE ORDER (mini-app facing)
    // ══════════════════════════════════════════════════════════════════

    record MedicineOrderRequest(List<Map<String, Object>> medicines, String address, String notes) {}

    @PostMapping("/medicine/order")
    public ApiResponse<Map<String, Object>> createMedicineOrder(@RequestBody MedicineOrderRequest body) {
        ServiceOrder o = new ServiceOrder();
        o.setDisplayType("送药上门");
        StringBuilder req = new StringBuilder();
        if (body.address() != null) req.append("配送地址：").append(body.address());
        if (body.notes() != null && !body.notes().isBlank()) req.append(" 备注：").append(body.notes());
        o.setRequirement(req.toString());
        currentClientUser().ifPresent(cu -> o.setGuardianId(cu.getId()));
        o.setCreatedById(SecurityUtil.currentUserId());
        orderRepo.save(o);
        return ApiResponse.ok(Map.of("success", true, "orderId", o.getId()));
    }
}
