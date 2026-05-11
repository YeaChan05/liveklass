CREATE TABLE members
(
    id            BIGINT       NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(30)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NULL,
    updated_at    DATETIME(6)  NULL,
    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email)
);

CREATE TABLE courses
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    creator_id      BIGINT         NOT NULL,

    title           VARCHAR(100)   NOT NULL,
    description     TEXT           NOT NULL,
    price           DECIMAL(19, 2) NOT NULL,

    capacity        INT            NOT NULL,
    seat_left_count INT            NOT NULL,

    period_start    DATETIME(6)    NOT NULL,
    period_end      DATETIME(6)    NOT NULL,

    status          VARCHAR(20)    NOT NULL,

    created_at      DATETIME(6)    NULL,
    updated_at      DATETIME(6)    NULL,

    CONSTRAINT pk_courses PRIMARY KEY (id),

    INDEX idx_courses_creator_id (creator_id)
);

CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,

    payment_pending_started_at DATETIME(6) NOT NULL,
    payment_pending_expires_at DATETIME(6) NOT NULL,

    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,

    CONSTRAINT pk_enrollments
        PRIMARY KEY (id),

    CONSTRAINT uk_enrollments_course_member
        UNIQUE (course_id, member_id),

    INDEX idx_enrollments_member_id (member_id),
    INDEX idx_enrollments_course_status (course_id, status),
    INDEX idx_enrollments_status_payment_pending_expires_at (status, payment_pending_expires_at)
);


CREATE INDEX idx_courses_status
    ON courses (status);
