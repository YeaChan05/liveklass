# Course 도메인

## 개요

- `Course`는 강의를 나타냅니다.
- `Course`는 다음 상태를 가집니다.

  - `DRAFT`
  - `OPEN`
  - `CLOSED`

---

## 모델 필드

- `courseId`
- `creatorId`
- `title`
- `description`
- `price`
- `capacity`
- `seatLeftCount`
- `periodStart`
- `periodEnd`
- `status`

---

## 규칙

- `Course`가 생성될 때 `seatLeftCount == capacity` 상태로 시작합니다.
- `DRAFT -> OPEN -> CLOSED` 방향만 허용됩니다.
- `OPEN` 상태의 `Course`만 수강 신청을 받을 수 있습니다.
- `seatLeftCount`는 음수가 될 수 없습니다.
- `seatLeftCount`는 `capacity`보다 클 수 없습니다.

---

## 행위

- `open()`
- `close()`
- `reserveSeat()`
- `releaseSeat()`
- `requestEnrollment(memberId, currentEnrollmentCount)`

