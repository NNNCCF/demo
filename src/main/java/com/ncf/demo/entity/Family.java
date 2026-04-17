package com.ncf.demo.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "family")
public class Family {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;

    @Column(name = "org_id")
    private Long orgId;

    /** 直接负责该家庭的护工 ClientUser.id */
    @Column(name = "caregiver_id")
    private Long caregiverId;

    private Instant createdAt;

    @ManyToMany
    @JoinTable(
        name = "family_guardian",
        joinColumns = @JoinColumn(name = "family_id"),
        inverseJoinColumns = @JoinColumn(name = "client_user_id")
    )
    private List<ClientUser> guardians = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getCaregiverId() { return caregiverId; }
    public void setCaregiverId(Long caregiverId) { this.caregiverId = caregiverId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<ClientUser> getGuardians() { return guardians; }
    public void setGuardians(List<ClientUser> guardians) { this.guardians = guardians; }
}
