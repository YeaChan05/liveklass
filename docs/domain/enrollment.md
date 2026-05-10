# Enrollment 도메인

## 개요

- `Enrollment`는 회원이 강의에 수강 신청한 기록을 나타냅니다.
- `Enrollment`는 다음 상태를 가집니다.

  - `PENDING`
  - `CONFIRMED`
  - `CANCELLED`
  - `EXPIRED`

---

## 모델 필드

- `enrollmentId`
- `courseId`
- `memberId`
- `status`
- `paymentPendingStartedAt`
- `paymentPendingExpiresAt`

---

## 규칙

- 새로운 `Enrollment`는 `PENDING` 상태로 시작합니다.
- `PENDING` 상태는 좌석을 점유합니다.
- `CONFIRMED` 상태는 좌석을 점유합니다.
- `CANCELLED` 상태는 좌석을 점유하지 않습니다.
- `EXPIRED` 상태는 좌석을 점유하지 않습니다.
- `PENDING` 상태는 `CONFIRMED` 상태로 변경될 수 있습니다.
- `PENDING` 상태는 `CANCELLED` 상태로 변경될 수 있습니다.
- `PENDING` 상태는 결제 대기 시간이 지나면 `EXPIRED` 상태로 변경될 수 있습니다.
- `CONFIRMED` 상태의 `Enrollment`는 취소되거나 만료될 수 없습니다.
- `CANCELLED` 상태의 `Enrollment`는 다시 변경될 수 없습니다.
- `EXPIRED` 상태의 `Enrollment`는 다시 변경될 수 없습니다.

---

## 행위

- `confirm()`
- `confirmPayment()`
- `cancel()`
- `expirePaymentPending()`
- `isPaymentPendingExpired()`
- `isSeatOccupied()`
