package org.yechan.member

import org.yechan.BusinessException
import org.yechan.Status

class DuplicateMemberEmailException :
    BusinessException(
        Status.CONFLICT,
        "이미 사용 중인 이메일입니다.",
    )

class MemberAuthenticationException :
    BusinessException(
        Status.AUTHENTICATION_FAILED,
        "이메일 또는 비밀번호가 올바르지 않습니다.",
    )

class InvalidRefreshTokenException :
    BusinessException(
        Status.AUTHENTICATION_FAILED,
        "유효하지 않은 Refresh Token입니다.",
    )

class MemberNotFoundException :
    BusinessException(
        Status.AUTHENTICATION_FAILED,
        "인증이 필요합니다.",
    )

class InactiveMemberException :
    BusinessException(
        Status.FORBIDDEN,
        "비활성화된 회원입니다.",
    )
