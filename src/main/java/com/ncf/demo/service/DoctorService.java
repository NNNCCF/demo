package com.ncf.demo.service;

import com.ncf.demo.common.BizException;
import com.ncf.demo.entity.Device;
import com.ncf.demo.entity.Doctor;
import com.ncf.demo.repository.DeviceRepository;
import com.ncf.demo.repository.DoctorRepository;
import com.ncf.demo.repository.OrganizationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final DeviceRepository deviceRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public DoctorService(DoctorRepository doctorRepository,
                         DeviceRepository deviceRepository,
                         OrganizationRepository organizationRepository,
                         PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.deviceRepository = deviceRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Doctor> listAll() {
        return doctorRepository.findAll();
    }

    public List<Doctor> listByOrg(Long orgId) {
        return doctorRepository.findByOrgId(orgId);
    }

    public Doctor getById(Long id) {
        return doctorRepository.findById(id).orElseThrow(() -> new BizException(404, "医生不存在"));
    }

    @Transactional
    public Doctor create(Doctor doctor) {
        if (doctorRepository.existsByMobile(doctor.getMobile())) {
            throw new BizException(400, "手机号已存在");
        }
        if (!organizationRepository.existsById(doctor.getOrgId())) {
            throw new BizException(400, "医疗机构不存在");
        }
        doctor.setPassword(passwordEncoder.encode(doctor.getPassword()));
        return doctorRepository.save(doctor);
    }

    @Transactional
    public Doctor update(Long id, Doctor patch) {
        Doctor doctor = getById(id);
        if (patch.getName() != null) doctor.setName(patch.getName());
        if (patch.getIntroduction() != null) doctor.setIntroduction(patch.getIntroduction());
        if (patch.getTitle() != null) doctor.setTitle(patch.getTitle());
        if (patch.getMobile() != null && !patch.getMobile().equals(doctor.getMobile())) {
            if (doctorRepository.existsByMobile(patch.getMobile())) {
                throw new BizException(400, "手机号已存在");
            }
            doctor.setMobile(patch.getMobile());
        }
        if (patch.getPassword() != null && !patch.getPassword().isBlank()) {
            doctor.setPassword(passwordEncoder.encode(patch.getPassword()));
        }
        return doctorRepository.save(doctor);
    }

    @Transactional
    public void delete(Long id) {
        Doctor doctor = getById(id);
        // unbind all devices first
        deviceRepository.findByDoctorId(id).forEach(d -> {
            d.setDoctorId(null);
            deviceRepository.save(d);
        });
        doctorRepository.delete(doctor);
    }

    @Transactional
    public void bindDevice(Long doctorId, String deviceId) {
        Doctor doctor = getById(doctorId);
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "设备不存在"));
        if (device.getDoctorId() != null && !device.getDoctorId().equals(doctorId)) {
            throw new BizException(400, "设备已绑定其他医生");
        }
        device.setDoctorId(doctorId);
        deviceRepository.save(device);
    }

    @Transactional
    public void unbindDevice(Long doctorId, String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BizException(404, "设备不存在"));
        if (!doctorId.equals(device.getDoctorId())) {
            throw new BizException(400, "设备未绑定该医生");
        }
        device.setDoctorId(null);
        deviceRepository.save(device);
    }

    public List<Device> listDevices(Long doctorId) {
        getById(doctorId); // existence check
        return deviceRepository.findByDoctorId(doctorId);
    }
}
