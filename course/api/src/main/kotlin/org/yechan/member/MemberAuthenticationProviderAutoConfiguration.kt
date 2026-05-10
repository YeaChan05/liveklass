package org.yechan.member

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.AuthenticationProvider

@AutoConfiguration
@ConditionalOnBean(MemberAuthUseCase::class)
@Import(MemberAuthenticationProviderBeanRegistrar::class)
class MemberAuthenticationProviderAutoConfiguration

class MemberAuthenticationProviderBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<AuthenticationProvider> {
            bean<MemberAuthenticationProvider>()
        }
    })
