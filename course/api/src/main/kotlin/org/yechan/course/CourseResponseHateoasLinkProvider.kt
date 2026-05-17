package org.yechan.course

import org.springframework.hateoas.Link
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.yechan.ApiHateoasLinkProvider

class CourseResponseHateoasLinkProvider : ApiHateoasLinkProvider {
    override fun supports(body: Any): Boolean = body is CourseResponse

    override fun links(body: Any): Iterable<Link> = (body as CourseResponse).courseLinks()
}

private fun CourseResponse.courseLinks(): List<Link> = buildList {
    add(courseLink(courseId).withSelfRel())
    add(linkTo(CourseController::class.java).slash("courses").withRel("courses"))

    when (status) {
        CourseStatus.DRAFT -> add(
            courseLink(courseId).slash("open").withRel("open"),
        )

        CourseStatus.OPEN -> {
            add(courseLink(courseId).slash("close").withRel("close"))
            add(courseLink(courseId).slash("enrollments").withRel("enroll"))
        }

        CourseStatus.CLOSED -> Unit
    }
}

private fun courseLink(courseId: Long) = linkTo(CourseController::class.java)
    .slash("courses")
    .slash(courseId)
