package org.yechan.course

import org.yechan.BusinessException
import org.yechan.Status

class CourseNotFoundException :
    BusinessException(
        Status.RESOURCE_NOT_FOUND,
        "강의를 찾을 수 없습니다.",
    )

class EnrollmentNotFoundException :
    BusinessException(
        Status.RESOURCE_NOT_FOUND,
        "수강 신청을 찾을 수 없습니다.",
    )

class CourseAccessDeniedException :
    BusinessException(
        Status.FORBIDDEN,
        "강의를 변경할 권한이 없습니다.",
    )

class CourseInvalidStateException(
    message: String,
) : BusinessException(
    Status.BAD_REQUEST,
    message,
)
