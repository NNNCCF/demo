package com.ncf.demo.web;

import com.ncf.demo.entity.Ward;
import com.ncf.demo.repository.WardRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wards")
public class WardController {
    private final WardRepository wardRepository;

    public WardController(WardRepository wardRepository) {
        this.wardRepository = wardRepository;
    }

    @GetMapping
    public ApiResponse<List<Ward>> list() {
        return ApiResponse.ok(wardRepository.findAll());
    }
}
