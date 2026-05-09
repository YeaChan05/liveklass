# Member 도메인

## 개요

- `Member`는 서비스 사용자를 나타냅니다.
- `Member`는 다음 역할을 가집니다.

  - `CREATOR`
  - `CLASSMATE`
  - `ADMIN`
- `Member`는 다음 상태를 가집니다.

  - `ACTIVE`
  - `DELETED`

---

## 책임

- `CREATOR`는 강의를 생성하고 강의 상태를 변경할 수 있습니다.
- `CLASSMATE`는 모집 중인 강의에 수강 신청할 수 있습니다.
- `ADMIN`은 보안 정책을 통해 `CREATOR`와 `CLASSMATE`의 모든 기능에 접근할 수 있습니다.

---

## 모델 필드

- `memberId`
- `email`
- `passwordHash`
- `name`
- `role`
- `status`

---

## 행위
- `isActive()`

