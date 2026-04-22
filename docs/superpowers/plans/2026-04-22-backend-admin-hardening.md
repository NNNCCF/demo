# Backend Admin Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move admin login and registration captcha handling to the backend, enforce first-login password change for the seeded admin user, and complete backend/admin CRUD flows for service orders, feedback submissions, news posts, and organization action layout.

**Architecture:** The backend will own captcha session generation, cooldown control, and first-login password enforcement using Redis plus the existing JWT stack. Admin management endpoints will be separated from public endpoints where needed, and the Vue admin app will consume the new auth/admin APIs while adding missing edit/detail/delete flows.

**Tech Stack:** Spring Boot 3.3, Spring Security, Spring Data JPA, Redis, MySQL, Vue 3, Vite, TypeScript, Element Plus

---

### File Structure

**Files:**
- Create: `demo/src/main/java/com/ncf/demo/service/AuthCaptchaService.java`
- Create: `demo/src/main/java/com/ncf/demo/security/ForcePasswordChangeFilter.java`
- Create: `demo/src/main/java/com/ncf/demo/web/AdminNewsController.java`
- Create: `demo/src/main/java/com/ncf/demo/web/AdminServiceOrderController.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/AdminCreateUserRequest.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/AuthCaptchaResponse.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/ChangePasswordRequest.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/FeedbackAdminUpsertRequest.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/PublicRegisterRequest.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/ServiceOrderAdminUpsertRequest.java`
- Create: `demo/src/test/java/com/ncf/demo/service/AuthServiceTest.java`
- Create: `demo_ui/src/views/ChangePasswordView.vue`
- Modify: `demo/src/main/java/com/ncf/demo/config/DataInitializer.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/FeedbackSubmission.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/NewsPost.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/UserAccount.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/FeedbackSubmissionRepository.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/NewsPostRepository.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/ServiceOrderRepository.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/AuthService.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/NewsPostService.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/ServiceOrderService.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/AdminUserController.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/AuthController.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/FeedbackAdminController.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/NewsPostController.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/dto/LoginRequest.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/dto/LoginResponse.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/dto/RegisterRequest.java`
- Modify: `demo_ui/src/api/modules.ts`
- Modify: `demo_ui/src/router/index.ts`
- Modify: `demo_ui/src/stores/auth.ts`
- Modify: `demo_ui/src/types/index.ts`
- Modify: `demo_ui/src/views/FeedbackView.vue`
- Modify: `demo_ui/src/views/LoginView.vue`
- Modify: `demo_ui/src/views/NewsView.vue`
- Modify: `demo_ui/src/views/OrganizationView.vue`
- Modify: `demo_ui/src/views/ServiceOrderView.vue`
- Modify: `demo_ui/src/views/SystemView.vue`

### Task 1: Add Backend Auth Safety Model

**Files:**
- Modify: `demo/src/main/java/com/ncf/demo/entity/UserAccount.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/DataInitializer.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/AuthService.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/dto/LoginResponse.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/ChangePasswordRequest.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/AuthController.java`
- Test: `demo/src/test/java/com/ncf/demo/service/AuthServiceTest.java`

- [ ] **Step 1: Write the failing test for first-login password enforcement**

```java
@Test
void loginReturnsForcePasswordChangeForSeededAdmin() {
    UserAccount user = new UserAccount();
    user.setId(1000L);
    user.setUsername("admin");
    user.setPasswordHash(passwordEncoder.encode("200502"));
    user.setRole(UserRole.ADMIN);
    user.setStatus(UserStatus.ENABLED);
    user.setForcePasswordChange(true);

    when(userRepository.findAllByUsernameOrderByIdAsc("admin")).thenReturn(List.of(user));

    LoginResponse response = authService.login(new LoginRequest("admin", "200502", "token", "abcd"));

    assertThat(response.forcePasswordChange()).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.ncf.demo.service.AuthServiceTest`

Expected: FAIL because `UserAccount` and `LoginResponse` do not expose `forcePasswordChange`

- [ ] **Step 3: Implement the persistence fields and seeded admin defaults**

```java
@Column(name = "force_password_change", nullable = false)
private boolean forcePasswordChange;

@Column(name = "password_changed_at")
private Instant passwordChangedAt;
```

```java
jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN force_password_change BIT NOT NULL DEFAULT b'0'");
jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN password_changed_at DATETIME NULL");
```

```java
jdbcTemplate.update(
    "INSERT INTO sys_user (id, username, password_hash, role, region, phone, status, force_password_change, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
    1000L, "admin", passwordEncoder.encode("200502"), "ADMIN", "HQ", "13800138000", "ENABLED", true
);
```

- [ ] **Step 4: Add first-login response and password change method**

```java
public record LoginResponse(
        Long userId,
        String username,
        String role,
        Long orgId,
        String orgType,
        String token,
        Long expireSeconds,
        boolean forcePasswordChange
) {}
```

```java
public void changePassword(Long userId, String oldPassword, String newPassword) {
    UserAccount user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException(4001, "用户不存在"));
    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
        throw new BizException(4003, "原密码错误");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setForcePasswordChange(false);
    user.setPasswordChangedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
}
```

- [ ] **Step 5: Expose the password change endpoint**

```java
@PostMapping("/account/change-password")
public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
    authService.changePassword(SecurityUtil.requireCurrentUserId(), request.oldPassword(), request.newPassword());
    return ApiResponse.ok(null);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests com.ncf.demo.service.AuthServiceTest`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ncf/demo/entity/UserAccount.java src/main/java/com/ncf/demo/config/DataInitializer.java src/main/java/com/ncf/demo/service/AuthService.java src/main/java/com/ncf/demo/web/AuthController.java src/main/java/com/ncf/demo/web/dto/LoginResponse.java src/main/java/com/ncf/demo/web/dto/ChangePasswordRequest.java src/test/java/com/ncf/demo/service/AuthServiceTest.java
git commit -m "feat: enforce first login password change"
```

### Task 2: Move Captcha to Backend With Cooldown Control

**Files:**
- Create: `demo/src/main/java/com/ncf/demo/service/AuthCaptchaService.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/AuthCaptchaResponse.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/dto/LoginRequest.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/dto/PublicRegisterRequest.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/AuthController.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/AuthService.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`
- Test: `demo/src/test/java/com/ncf/demo/service/AuthServiceTest.java`

- [ ] **Step 1: Write the failing test for captcha cooldown**

```java
@Test
void verifyCaptchaFailureStartsCooldown() {
    when(captchaService.verifyOrThrow("LOGIN", "client-key", "token", "bad")).thenThrow(new BizException(4291, "验证码错误，请60秒后重试"));

    assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "200502", "token", "bad"), "client-key"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("60秒后重试");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.ncf.demo.service.AuthServiceTest`

Expected: FAIL because captcha verification is not part of the auth flow

- [ ] **Step 3: Implement Redis-backed captcha session handling**

```java
public AuthCaptchaResponse issueCaptcha(String scene, String clientKey) {
    String cooldownKey = "auth:captcha:cooldown:" + scene + ":" + clientKey;
    Long ttl = stringRedisTemplate.getExpire(cooldownKey);
    if (ttl != null && ttl > 0) {
        throw new BizException(4290, "当前操作过于频繁，请" + ttl + "秒后重试");
    }
    String token = UUID.randomUUID().toString().replace("-", "");
    CaptchaApiResponse response = captchaGateway.createCaptcha();
    saveSession(token, scene, clientKey, response.id());
    return new AuthCaptchaResponse(token, "/api/auth/captcha/" + token + "/image", 300, 0);
}
```

```java
public void verifyOrThrow(String scene, String clientKey, String captchaToken, String captchaCode) {
    CaptchaSession session = loadSession(captchaToken, scene, clientKey);
    boolean verified = captchaGateway.verify(session.thirdPartyId(), captchaCode);
    if (!verified) {
        stringRedisTemplate.opsForValue().set(cooldownKey(scene, clientKey), "1", Duration.ofMinutes(1));
        stringRedisTemplate.delete(sessionKey(captchaToken));
        throw new BizException(4291, "验证码错误，请60秒后重试");
    }
    stringRedisTemplate.delete(sessionKey(captchaToken));
}
```

- [ ] **Step 4: Update auth endpoints to use captcha**

```java
@GetMapping("/auth/captcha")
public ApiResponse<AuthCaptchaResponse> getCaptcha(@RequestParam String scene, HttpServletRequest request) {
    return ApiResponse.ok(authCaptchaService.issueCaptcha(scene, authCaptchaService.buildClientKey(request)));
}
```

```java
@PostMapping("/login")
public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest) {
    String clientKey = authCaptchaService.buildClientKey(servletRequest);
    return ApiResponse.ok(authService.login(request, clientKey));
}
```

- [ ] **Step 5: Run backend tests**

Run: `./gradlew test --tests com.ncf.demo.service.AuthServiceTest`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ncf/demo/service/AuthCaptchaService.java src/main/java/com/ncf/demo/web/dto/AuthCaptchaResponse.java src/main/java/com/ncf/demo/web/dto/LoginRequest.java src/main/java/com/ncf/demo/web/dto/PublicRegisterRequest.java src/main/java/com/ncf/demo/web/AuthController.java src/main/java/com/ncf/demo/service/AuthService.java src/main/java/com/ncf/demo/config/SecurityConfig.java src/test/java/com/ncf/demo/service/AuthServiceTest.java
git commit -m "feat: move admin captcha flow to backend"
```

### Task 3: Gate Admin Traffic Until Password Is Changed

**Files:**
- Create: `demo/src/main/java/com/ncf/demo/security/ForcePasswordChangeFilter.java`
- Modify: `demo/src/main/java/com/ncf/demo/config/SecurityConfig.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/UserRepository.java`
- Test: `demo/src/test/java/com/ncf/demo/DemoApplicationTests.java`

- [ ] **Step 1: Write the failing integration test**

```java
@Test
void forcedPasswordChangeUserCannotOpenAdminPage() throws Exception {
    mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + forcedPasswordChangeToken))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.ncf.demo.DemoApplicationTests`

Expected: FAIL because admin requests are still allowed after login

- [ ] **Step 3: Implement the filter**

```java
if (request.getRequestURI().startsWith("/api/account/change-password")) {
    filterChain.doFilter(request, response);
    return;
}
if (currentUser != null && currentUser.isForcePasswordChange()) {
    response.setStatus(403);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":403,\"message\":\"Please change your password before continuing\"}");
    return;
}
```

- [ ] **Step 4: Register the filter after JWT authentication**

```java
.addFilterAfter(forcePasswordChangeFilter, JwtAuthFilter.class);
```

- [ ] **Step 5: Run the integration test**

Run: `./gradlew test --tests com.ncf.demo.DemoApplicationTests`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ncf/demo/security/ForcePasswordChangeFilter.java src/main/java/com/ncf/demo/config/SecurityConfig.java src/main/java/com/ncf/demo/repository/UserRepository.java src/test/java/com/ncf/demo/DemoApplicationTests.java
git commit -m "feat: block admin routes until password is changed"
```

### Task 4: Complete Admin CRUD APIs for Service Orders, Feedback, and News

**Files:**
- Create: `demo/src/main/java/com/ncf/demo/web/AdminServiceOrderController.java`
- Create: `demo/src/main/java/com/ncf/demo/web/AdminNewsController.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/ServiceOrderAdminUpsertRequest.java`
- Create: `demo/src/main/java/com/ncf/demo/web/dto/FeedbackAdminUpsertRequest.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/FeedbackSubmission.java`
- Modify: `demo/src/main/java/com/ncf/demo/entity/NewsPost.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/ServiceOrderRepository.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/FeedbackSubmissionRepository.java`
- Modify: `demo/src/main/java/com/ncf/demo/repository/NewsPostRepository.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/ServiceOrderService.java`
- Modify: `demo/src/main/java/com/ncf/demo/service/NewsPostService.java`
- Modify: `demo/src/main/java/com/ncf/demo/web/FeedbackAdminController.java`
- Test: `demo/src/test/java/com/ncf/demo/service/AuthServiceTest.java`

- [ ] **Step 1: Write failing service-layer tests for logical delete behavior**

```java
@Test
void deletedServiceOrdersAreExcludedFromAdminList() {
    ServiceOrder order = new ServiceOrder();
    order.setDeleted(true);
    when(serviceOrderRepository.findAllByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of());

    assertThat(serviceOrderService.listAdminOrders()).isEmpty();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests com.ncf.demo.service.AuthServiceTest`

Expected: FAIL because the new repository methods and delete semantics do not exist

- [ ] **Step 3: Add the entity and repository fields**

```java
@Column(name = "deleted", nullable = false)
private boolean deleted;

@Column(name = "deleted_at")
private Instant deletedAt;

@Column(name = "deleted_by")
private Long deletedBy;
```

```java
List<ServiceOrder> findAllByDeletedFalseOrderByCreatedAtDesc();
List<FeedbackSubmission> findAllByDeletedFalseOrderByCreatedAtDesc();
```

- [ ] **Step 4: Implement admin CRUD services and controllers**

```java
@DeleteMapping("/{id}")
public ApiResponse<Void> delete(@PathVariable Long id) {
    serviceOrderService.softDelete(id, SecurityUtil.requireCurrentUserId());
    return ApiResponse.ok(null);
}
```

```java
@PutMapping("/{id}")
public ApiResponse<FeedbackListItem> update(@PathVariable Long id, @RequestBody @Valid FeedbackAdminUpsertRequest request) {
    return ApiResponse.ok(toListItem(updateFeedback(id, request)));
}
```

```java
@GetMapping("/{id}")
public ApiResponse<NewsPost> getById(@PathVariable Long id) {
    return ApiResponse.ok(newsPostService.getAdminById(id));
}
```

- [ ] **Step 5: Run backend tests and compilation**

Run: `./gradlew test`

Expected: PASS with `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ncf/demo/web/AdminServiceOrderController.java src/main/java/com/ncf/demo/web/AdminNewsController.java src/main/java/com/ncf/demo/web/dto/ServiceOrderAdminUpsertRequest.java src/main/java/com/ncf/demo/web/dto/FeedbackAdminUpsertRequest.java src/main/java/com/ncf/demo/entity/ServiceOrder.java src/main/java/com/ncf/demo/entity/FeedbackSubmission.java src/main/java/com/ncf/demo/entity/NewsPost.java src/main/java/com/ncf/demo/repository/ServiceOrderRepository.java src/main/java/com/ncf/demo/repository/FeedbackSubmissionRepository.java src/main/java/com/ncf/demo/repository/NewsPostRepository.java src/main/java/com/ncf/demo/service/ServiceOrderService.java src/main/java/com/ncf/demo/service/NewsPostService.java src/main/java/com/ncf/demo/web/FeedbackAdminController.java
git commit -m "feat: complete admin crud flows"
```

### Task 5: Update the Vue Admin App for the New Auth Flow

**Files:**
- Modify: `demo_ui/src/api/modules.ts`
- Modify: `demo_ui/src/stores/auth.ts`
- Modify: `demo_ui/src/types/index.ts`
- Modify: `demo_ui/src/router/index.ts`
- Modify: `demo_ui/src/views/LoginView.vue`
- Create: `demo_ui/src/views/ChangePasswordView.vue`

- [ ] **Step 1: Write the API and store types**

```ts
export interface AuthCaptcha {
  captchaToken: string
  imageUrl: string
  expireSeconds: number
  cooldownSeconds: number
}

export interface LoginResult {
  userId: number
  username: string
  role: UserRole
  orgId?: number
  orgType?: string
  token: string
  expireSeconds: number
  forcePasswordChange: boolean
}
```

```ts
interface UserInfo {
  userId: number
  role: UserRole
  username?: string
  orgId?: number | null
  orgType?: string | null
  forcePasswordChange?: boolean
}
```

- [ ] **Step 2: Run the front-end build to verify the old code fails**

Run: `npm run build`

Expected: FAIL after types are introduced but screens are not updated yet

- [ ] **Step 3: Update login, registration, and route guard behavior**

```ts
if (authStore.userInfo?.forcePasswordChange && to.path !== '/change-password') {
  next('/change-password')
  return
}
```

```ts
const captcha = await authApi.getCaptcha('LOGIN')
captchaToken.value = captcha.captchaToken
captchaUrl.value = captcha.imageUrl
cooldownSeconds.value = captcha.cooldownSeconds
```

```ts
await authApi.login({
  username: form.username,
  password: form.password,
  captchaToken: captchaToken.value,
  captchaCode: form.captchaInput,
})
```

- [ ] **Step 4: Add the forced password change screen**

```vue
<el-form :model="form" @submit.prevent="submit">
  <el-form-item label="原密码">
    <el-input v-model="form.oldPassword" type="password" show-password />
  </el-form-item>
  <el-form-item label="新密码">
    <el-input v-model="form.newPassword" type="password" show-password />
  </el-form-item>
</el-form>
```

- [ ] **Step 5: Run the front-end build**

Run: `npm run build`

Expected: PASS with `vite build` success output

- [ ] **Step 6: Commit**

```bash
git add src/api/modules.ts src/stores/auth.ts src/types/index.ts src/router/index.ts src/views/LoginView.vue src/views/ChangePasswordView.vue
git commit -m "feat: update admin auth experience"
```

### Task 6: Complete Vue CRUD Screens and Organization Layout

**Files:**
- Modify: `demo_ui/src/api/modules.ts`
- Modify: `demo_ui/src/views/ServiceOrderView.vue`
- Modify: `demo_ui/src/views/FeedbackView.vue`
- Modify: `demo_ui/src/views/NewsView.vue`
- Modify: `demo_ui/src/views/OrganizationView.vue`
- Modify: `demo_ui/src/views/SystemView.vue`

- [ ] **Step 1: Extend the API layer**

```ts
export const serviceOrderAdminApi = {
  list: (params?: Record<string, unknown>) => get<ServiceOrder[]>('/admin/service-orders', { params }),
  detail: (id: number) => get<ServiceOrder>(`/admin/service-orders/${id}`),
  create: (payload: ServiceOrderUpsertPayload) => post<ServiceOrder>('/admin/service-orders', payload),
  update: (id: number, payload: ServiceOrderUpsertPayload) => put<ServiceOrder>(`/admin/service-orders/${id}`, payload),
  delete: (id: number) => del<void>(`/admin/service-orders/${id}`),
}
```

- [ ] **Step 2: Add service-order detail/edit/delete UI**

```vue
<el-button link type="primary" @click="openDetail(row)">查看</el-button>
<el-button link @click="openEdit(row)">编辑</el-button>
<el-button link type="danger" @click="removeOrder(row)">删除</el-button>
```

- [ ] **Step 3: Add feedback create/edit/delete UI**

```vue
<el-button type="primary" @click="openCreate">新增反馈</el-button>
<el-button link @click="openEdit(row)">编辑</el-button>
<el-button link type="danger" @click="removeFeedback(row)">删除</el-button>
```

- [ ] **Step 4: Add news detail/edit UI**

```vue
<el-button link type="primary" @click="openDetail(row)">查看</el-button>
<el-button link @click="openEdit(row)">编辑</el-button>
<el-button link type="danger" @click="deletePost(row.id)">删除</el-button>
```

- [ ] **Step 5: Keep organization action buttons on a single row**

```css
.org-action-row {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  white-space: nowrap;
}
```

- [ ] **Step 6: Run the front-end build**

Run: `npm run build`

Expected: PASS with `vite build` success output

- [ ] **Step 7: Commit**

```bash
git add src/api/modules.ts src/views/ServiceOrderView.vue src/views/FeedbackView.vue src/views/NewsView.vue src/views/OrganizationView.vue src/views/SystemView.vue
git commit -m "feat: complete admin crud screens"
```

### Self-Review

Spec coverage:
- Auth captcha backend ownership: Task 2
- 60-second cooldown: Task 2
- Seeded admin default password and forced change: Task 1 and Task 3
- Remove public admin registration: Task 2 and Task 5
- Admin CRUD for service orders and feedback with soft delete: Task 4 and Task 6
- Admin news detail/edit with hard delete: Task 4 and Task 6
- Organization action row single-line layout: Task 6
- Run tests/build after each change: every task includes an explicit verification command

Placeholder scan:
- No `TODO`, `TBD`, or “implement later” markers remain.

Type consistency:
- `forcePasswordChange`, `captchaToken`, and `captchaCode` names are used consistently across service, DTO, store, and UI tasks.
