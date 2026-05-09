# Enrollment 도메인

## 개요

- `Enrollment`는 회원이 강의에 수강 신청한 기록을 나타냅니다.
- `Enrollment`는 다음 상태를 가집니다.

  - `PENDING`
  - `CONFIRMED`
  - `CANCELLED`

---

## 모델 필드

- `enrollmentId`
- `courseId`
- `memberId`
- `status`

---

## 규칙

- 새로운 `Enrollment`는 `PENDING` 상태로 시작합니다.
- `PENDING` 상태는 `CONFIRMED` 상태로 변경될 수 있습니다.
- `PENDING` 또는 `CONFIRMED` 상태는 `CANCELLED` 상태로 변경될 수 있습니다.
- `CANCELLED` 상태의 `Enrollment`는 다시 변경될 수 없습니다.

---

## 행위

- `confirm()`
- `confirmPayment()`
- `cancel()`

