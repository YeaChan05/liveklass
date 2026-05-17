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
- `PENDING` 상태의 정기 만료 처리는 `EnrollmentExpirationProcessor`가 담당하고, 스케줄러가 이를 주기적으로 호출합니다.
- `CONFIRMED` 상태의 `Enrollment`는 취소되거나 만료될 수 없습니다.
- `CANCELLED` 상태의 `Enrollment`는 다시 변경될 수 없습니다.
- `EXPIRED` 상태의 `Enrollment`는 다시 변경될 수 없습니다.
- 같은 회원과 강의에 대해 `PENDING` 또는 `CONFIRMED` 신청이 있으면 중복 신청으로 봅니다.
- 같은 회원과 강의에 대해 `CANCELLED` 또는 `EXPIRED` 신청만 있으면 재신청할 수 있습니다.
- 정원이 가득 찬 강의에 신청하면 `Enrollment`를 만들지 않고 대기열에 등록합니다.
- 대기열 등록 결과는 수강 신청 성공이 아니며, API 응답 상태는 `WAITLISTED`입니다.
- 대기열 등록과 조회, 취소는 Redis 대기열을 통해 관리합니다.
- Redis 대기열 처리는 DB 트랜잭션 범위 밖에서 수행합니다.
- 대기열 승격은 후보를 모은 뒤 좌석 예약과 신청 저장을 bulk로 처리합니다.

---

## 행위

- `confirm()`
- `confirmPayment()`
- `cancel()`
- `expirePaymentPending()`
- `isPaymentPendingExpired()`
- `isSeatOccupied()`
- `isPendingOrConfirmed()`

---

## 대기열

- 대기열은 강의별 Redis `ZSET`으로 관리합니다.
- 대기열의 값은 회원 ID이고, score는 대기열 등록 시각입니다.
- 대기열에는 TTL을 설정합니다.
- 강의별 sold-out 상태를 Redis에 저장하여 반복적인 DB 좌석 확보 시도를 줄입니다.
- 수강 신청 취소나 결제 대기 만료로 좌석이 반환되면 해당 강의의 sold-out 상태를 해제합니다.
- 대기열 승격 중 DB 처리에 실패하면 이미 pop한 대기열 항목을 복구합니다.
