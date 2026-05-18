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
- 사용자의 수강 신청 내역 조회에는 `CONFIRMED`, `CANCELLED` 상태만 노출합니다.
- `PENDING`은 결제 대기 중인 현재 처리 상태이고, `EXPIRED`는 내부 만료 처리 상태이므로 수강 신청 내역에 노출하지 않습니다.
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
- 좌석 반환 시 대기자가 있으면 신규 신청자보다 대기열 선두 회원을 먼저 `PENDING`으로 승격합니다.
- 대기자가 남아 있으면 waitlist mode를 유지하고, 대기열이 비었을 때만 waitlist mode를 해제합니다.
- 대기열 승격은 Redis pop 선행 방식이 아니라 `peek → DB 승격 성공 → Redis remove` 순서로 처리합니다.
- DB 승격 중 예외가 발생하면 해당 대기자는 Redis에서 제거하지 않습니다.
- 대기열에 유효하지 않은 데이터가 있으면 제거하고 다음 대기자 승격을 시도합니다.

---

## 행위

- `confirm()`
- `confirmPayment()`
- `cancel()`
- `expirePaymentPending()`
- `isPaymentPendingExpired()`
- `isSeatOccupied()`
- `isVisibleInMyEnrollmentHistory()`

---

## 저장 설계

- 수강 신청은 `enrollments` 테이블에 저장합니다.
- 확정된 신청도 별도 테이블이 아니라 같은 `enrollments` row의 `status = CONFIRMED`로 저장합니다.
- 사용자 취소는 `status = CANCELLED`로 저장합니다.
- 같은 회원과 강의 조합은 `course_id`, `member_id`로 식별하며 한 row만 유지합니다.
- 테이블 간 FK는 만들지 않고 `course_id`, `member_id` 식별자만 저장합니다.
- 사용자 수강 신청 내역 조회는 `member_id`, `status` 기준으로 `CONFIRMED`, `CANCELLED`만 조회합니다.

---

## 대기열

- 대기열은 강의별 Redis `ZSET`으로 관리합니다.
- 대기열의 값은 회원 ID이고, score는 대기열 등록 시각입니다.
- 대기열에는 TTL을 설정합니다.
- 강의별 waitlist mode 상태를 Redis에 저장하여 반복적인 DB 좌석 확보 시도를 줄입니다.
- 수강 신청 취소나 결제 대기 만료로 좌석이 반환되면 대기열 선두 회원을 먼저 승격합니다.
- 승격 후에도 대기자가 남아 있으면 waitlist mode를 유지합니다.
- 승격 후 대기열이 비었거나 반환 좌석을 받을 대기자가 없을 때만 waitlist mode를 해제합니다.
- 대기열 승격 중 DB 처리에 실패하면 Redis 항목을 제거하지 않고 다음 실행에서 다시 처리할 수 있게 둡니다.
