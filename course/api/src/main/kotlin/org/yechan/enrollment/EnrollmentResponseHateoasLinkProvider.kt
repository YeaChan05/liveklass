package org.yechan.enrollment

import org.springframework.hateoas.Link
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.yechan.ApiHateoasLinkProvider

class EnrollmentResponseHateoasLinkProvider : ApiHateoasLinkProvider {
    override fun supports(body: Any): Boolean = body is EnrollmentResponse

    override fun links(body: Any): Iterable<Link> = (body as EnrollmentResponse).enrollmentLinks()
}

private fun EnrollmentResponse.enrollmentLinks(): List<Link> = buildList {
    add(courseLink(courseId).withRel("course"))

    when (status) {
        EnrollmentResponseStatus.PENDING -> {
            add(enrollmentLink(requireNotNull(enrollmentId)).slash("confirm").withRel("confirm"))
            add(enrollmentLink(enrollmentId).slash("cancel").withRel("cancel"))
        }

        EnrollmentResponseStatus.CONFIRMED -> {
            add(enrollmentLink(requireNotNull(enrollmentId)).slash("cancel").withRel("cancel"))
        }

        EnrollmentResponseStatus.CANCELLED,
        EnrollmentResponseStatus.EXPIRED,
        -> {
            add(courseLink(courseId).slash("enrollments").withRel("enroll"))
        }

        EnrollmentResponseStatus.WAITLISTED -> {
            add(enrollmentRootLink().slash("waitlist").slash("me").withRel("waitlist-events"))
            add(enrollmentRootLink().slash("waitlist").slash(courseId).withRel("cancel-waitlist"))
        }
    }
}

private fun courseLink(courseId: Long) = linkTo(EnrollmentController::class.java)
    .slash("courses")
    .slash(courseId)

private fun enrollmentLink(enrollmentId: Long) = enrollmentRootLink().slash(enrollmentId)

private fun enrollmentRootLink() = linkTo(EnrollmentController::class.java).slash("enrollments")
