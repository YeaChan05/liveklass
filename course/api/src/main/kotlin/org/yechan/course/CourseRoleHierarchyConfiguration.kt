package org.yechan.course

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl

@AutoConfiguration
class CourseRoleHierarchyConfiguration :
    BeanRegistrarDsl({
        registerBean<RoleHierarchy> {
            RoleHierarchyImpl.fromHierarchy("ROLE_CREATOR > ROLE_CLASSMATE")
        }
    })
