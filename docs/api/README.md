# API 명세

## 버전 관리

- 모든 API는 요청 헤더 기반 버전 관리를 사용합니다.
- 요청 헤더에 `X-API-Version: v1` 값을 포함해야 합니다.

---

## 인증 API

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/token/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

---

## Course API

- `GET /api/courses`
- `GET /api/courses/{courseId}`
- `POST /api/courses`
- `POST /api/courses/{courseId}/open`
- `POST /api/courses/{courseId}/close`
- `POST /api/courses/{courseId}/enrollments`

---

## Enrollment API

- `POST /api/enrollments/{enrollmentId}/confirm`
- `POST /api/enrollments/{enrollmentId}/cancel`
- `GET /api/enrollments/me`

---

## 참고 사항

- `CREATOR`는 강의를 관리할 수 있습니다.
- `CLASSMATE`는 모집 중인 강의에 수강 신청할 수 있습니다.
- `CREATOR`는 role hierarchy를 통해 `CLASSMATE` 권한도 함께 가집니다.

