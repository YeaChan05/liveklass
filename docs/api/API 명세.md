# API 명세

## 버전 관리

- 모든 API는 요청 헤더 기반 버전 관리를 사용합니다.
- 요청 헤더에 `X-API-Version: v1` 값을 포함해야 합니다.

공통 요청 헤더:

```bash
-H "X-API-Version: v1"
```

인증이 필요한 API는 다음 헤더를 함께 포함합니다.

```bash
-H "Authorization: Bearer {accessToken}"
```

---

## 인증 API

- [`POST /api/auth/signup`](/course/api/src/main/kotlin/org/yechan/member/MemberAuthController.kt)
    - 회원가입을 수행합니다.
    - 새로운 `Member`를 생성합니다.
    - 매핑: `MemberAuthController.signup()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/auth/signup" \
  -H "Content-Type: application/json" \
  -H "X-API-Version: v1" \
  -d '{
    "email": "creator@test.com",
    "password": "password1234",
    "name": "테스트 크리에이터",
    "role": "CREATOR"
  }'
```

```bash
curl -X POST "http://localhost:8080/api/auth/signup" \
  -H "Content-Type: application/json" \
  -H "X-API-Version: v1" \
  -d '{
    "email": "classmate@test.com",
    "password": "password1234",
    "name": "테스트 클래스메이트",
    "role": "CLASSMATE"
  }'
```

- [`POST /api/auth/login`](/course/api/src/main/kotlin/org/yechan/member/MemberAuthController.kt)
    - 이메일과 비밀번호로 로그인합니다.
    - 인증 토큰을 발급합니다.
    - 매핑: `MemberAuthController.login()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-API-Version: v1" \
  -d '{
    "email": "creator@test.com",
    "password": "password1234"
  }'
```

- [`POST /api/auth/token/refresh`](/course/api/src/main/kotlin/org/yechan/member/MemberAuthController.kt)
    - refresh token을 사용해 access token을 재발급합니다.
    - 매핑: `MemberAuthController.refresh()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/auth/token/refresh" \
  -H "Content-Type: application/json" \
  -H "X-API-Version: v1" \
  -d '{
    "refreshToken": "refresh-token-value"
  }'
```

- [`POST /api/auth/logout`](/course/api/src/main/kotlin/org/yechan/member/MemberAuthController.kt)
    - 현재 로그인 세션을 종료합니다.
    - refresh token을 만료 처리합니다.
    - 매핑: `MemberAuthController.logout()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/auth/logout" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

- [`GET /api/auth/me`](/course/api/src/main/kotlin/org/yechan/member/MemberAuthController.kt)
    - 현재 로그인한 회원 정보를 조회합니다.
    - 매핑: `MemberAuthController.me()`
    - 요청 예시:

```bash
curl -X GET "http://localhost:8080/api/auth/me" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

---

## Course API

- [`GET /api/courses`](/course/api/src/main/kotlin/org/yechan/course/CourseController.kt)
    - 강의 목록을 조회합니다.
    - `status` query parameter가 없으면 전체 강의를 조회합니다.
    - `status` query parameter가 있으면 해당 상태의 강의만 조회합니다.
    - 지원 상태: `DRAFT`, `OPEN`, `CLOSED`
    - 매핑: `CourseController.getCourses()`
    - 전체 조회 요청 예시:

```bash
curl -X GET "http://localhost:8080/api/courses" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

    - 상태 필터 조회 요청 예시:

```bash
curl -X GET "http://localhost:8080/api/courses?status=OPEN" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

```bash
curl -X GET "http://localhost:8080/api/courses?status=DRAFT" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

```bash
curl -X GET "http://localhost:8080/api/courses?status=CLOSED" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

- [`GET /api/courses/{courseId}`](/course/api/src/main/kotlin/org/yechan/course/CourseController.kt)
    - 강의 상세 정보를 조회합니다.
    - `capacity`, `seatLeftCount`, `currentEnrollmentCount`를 포함합니다.
    - 매핑: `CourseController.getCourse()`
    - 요청 예시:

```bash
curl -X GET "http://localhost:8080/api/courses/1" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer access-token-value"
```

- [`POST /api/courses`](/course/api/src/main/kotlin/org/yechan/course/CourseController.kt)
    - 새로운 강의를 등록합니다.
    - 등록된 강의는 `DRAFT` 상태로 생성됩니다.
    - `CREATOR` 역할만 호출할 수 있습니다.
    - 매핑: `CourseController.createCourse()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/courses" \
  -H "Content-Type: application/json" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer creator-access-token-value" \
  -d '{
    "title": "Spring Boot 실전 강의",
    "description": "Spring Boot 기반 백엔드 개발을 실습하는 강의입니다.",
    "price": 10000,
    "capacity": 100,
    "periodStart": "2026-06-01T10:00:00",
    "periodEnd": "2026-06-30T18:00:00"
  }'
```

- [`POST /api/courses/{courseId}/open`](/course/api/src/main/kotlin/org/yechan/course/CourseController.kt)
    - 강의를 모집 중 상태로 변경합니다.
    - `DRAFT -> OPEN` 상태 전이를 수행합니다.
    - `CREATOR` 역할만 호출할 수 있습니다.
    - 매핑: `CourseController.openCourse()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/courses/1/open" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer creator-access-token-value"
```

- [`POST /api/courses/{courseId}/close`](/course/api/src/main/kotlin/org/yechan/course/CourseController.kt)
    - 강의를 모집 마감 상태로 변경합니다.
    - `OPEN -> CLOSED` 상태 전이를 수행합니다.
    - `CREATOR` 역할만 호출할 수 있습니다.
    - 매핑: `CourseController.closeCourse()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/courses/1/close" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer creator-access-token-value"
```

- [`POST /api/courses/{courseId}/enrollments`](/course/api/src/main/kotlin/org/yechan/enrollment/EnrollmentController.kt)
    - 강의에 수강 신청합니다.
    - 모집 중인 `OPEN` 상태의 강의에만 신청할 수 있습니다.
    - 수강 신청 성공 시 `Enrollment`를 `PENDING` 상태로 생성합니다.
    - 수강 신청 성공 시 `seatLeftCount`를 감소시킵니다.
    - 정원이 가득 찬 경우 대기열에 등록하고 `WAITLISTED` 응답을 반환합니다.
    - `PENDING` 또는 `CONFIRMED` 신청이 이미 있으면 기존 신청 결과를 반환합니다.
    - `CANCELLED` 또는 `EXPIRED` 신청만 있으면 다시 신청할 수 있습니다.
    - 매핑: `EnrollmentController.enroll()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/courses/1/enrollments" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer classmate-access-token-value"
```

    - 신청 성공 응답 예시:

```json
{
  "enrollmentId": 1,
  "courseId": 1,
  "memberId": 2,
  "status": "PENDING"
}
```

    - 대기열 등록 응답 예시:

```json
{
  "enrollmentId": null,
  "courseId": 1,
  "memberId": 2,
  "status": "WAITLISTED"
}
```

---

## Enrollment API

- [`POST /api/enrollments/{enrollmentId}/confirm`](/course/api/src/main/kotlin/org/yechan/enrollment/EnrollmentController.kt)
    - 수강 신청 결제를 확정합니다.
    - `PENDING -> CONFIRMED` 상태 전이를 수행합니다.
    - 결제 확정은 좌석 수를 변경하지 않습니다.
    - 매핑: `EnrollmentController.confirmEnrollment()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/enrollments/1/confirm" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer classmate-access-token-value"
```

- [`POST /api/enrollments/{enrollmentId}/cancel`](/course/api/src/main/kotlin/org/yechan/enrollment/EnrollmentController.kt)
    - 수강 신청을 취소합니다.
    - 결제 대기 상태의 신청만 취소할 수 있습니다.
    - `PENDING -> CANCELLED` 상태 전이를 수행합니다.
    - 수강 취소 성공 시 `seatLeftCount`를 증가시킵니다.
    - 수강 취소 성공 시 해당 강의의 sold-out 상태를 해제합니다.
    - `CONFIRMED` 상태의 신청은 취소할 수 없습니다.
    - 매핑: `EnrollmentController.cancelEnrollment()`
    - 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/enrollments/1/cancel" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer classmate-access-token-value"
```

- [`GET /api/enrollments/me`](/course/api/src/main/kotlin/org/yechan/enrollment/EnrollmentController.kt)
    - 현재 로그인한 회원의 수강 신청 목록을 조회합니다.
    - 매핑: `EnrollmentController.getMyEnrollments()`
    - 요청 예시:

```bash
curl -X GET "http://localhost:8080/api/enrollments/me" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer classmate-access-token-value"
```

- [`GET /api/enrollments/waitlist/me`](/course/api/src/main/kotlin/org/yechan/enrollment/EnrollmentController.kt)
    - 현재 로그인한 회원의 대기열 상태를 SSE로 조회합니다.
    - 대기열 상태는 아직 좌석을 점유한 수강 신청이 아닙니다.
    - 매핑: `EnrollmentController.getMyWaitlist()`
    - 요청 예시:

```bash
curl -X GET "http://localhost:8080/api/enrollments/waitlist/me" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer classmate-access-token-value"
```

- [`DELETE /api/enrollments/waitlist/{courseId}`](/course/api/src/main/kotlin/org/yechan/enrollment/EnrollmentController.kt)
    - 현재 로그인한 회원의 해당 강의 대기열을 취소합니다.
    - 성공 시 `204 No Content`를 반환합니다.
    - 매핑: `EnrollmentController.cancelWaitlist()`
    - 요청 예시:

```bash
curl -X DELETE "http://localhost:8080/api/enrollments/waitlist/1" \
  -H "X-API-Version: v1" \
  -H "Authorization: Bearer classmate-access-token-value"
```

---

### 참고 사항

- `CREATOR`는 강의를 등록하고 모집 시작/마감 상태로 변경할 수 있습니다.
- `CLASSMATE`는 모집 중인 강의에 수강 신청할 수 있습니다.
- `CREATOR`는 role hierarchy를 통해 `CLASSMATE` 권한도 함께 가집니다.
- 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 포함해야 합니다.
- 모든 API 요청에는 `X-API-Version: v1` 헤더를 포함해야 합니다.
- 요청 body가 없는 API는 path variable과 인증 사용자 정보를 기준으로 처리합니다.
- 강의 목록 조회의 `status` query parameter는 선택 값입니다.
- `status` query parameter가 없으면 전체 강의를 조회합니다.
- 수강 신청은 성공 시 `PENDING` 상태로 생성됩니다.
- 수강 신청은 정원이 가득 차면 대기열 등록 응답을 반환합니다.
- 대기열 등록 응답의 `enrollmentId`는 `null`이고 `status`는 `WAITLISTED`입니다.
- `PENDING` 또는 `CONFIRMED` 신청이 있으면 중복 신청으로 보고 새 신청을 만들지 않습니다.
- `CANCELLED` 또는 `EXPIRED` 신청만 있으면 다시 신청할 수 있습니다.
- 대기열에 등록된 신청은 `DELETE /api/enrollments/waitlist/{courseId}`로 취소할 수 있습니다.
- 결제 확정은 `PENDING -> CONFIRMED` 상태 변경으로 처리합니다.
- 수강 취소는 `PENDING -> CANCELLED` 상태 변경으로 처리합니다.
- `CONFIRMED` 상태의 수강 신청은 취소할 수 없습니다.
