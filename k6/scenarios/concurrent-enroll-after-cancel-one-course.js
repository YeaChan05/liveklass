import http, { expectedStatuses } from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

http.setResponseCallback(expectedStatuses(200, 201, 202, 400, 409));

const TOKEN_PATH = __ENV.TOKEN_PATH || '../tokens.json';

const applicantTokens = new SharedArray('applicant tokens', function () {
  return JSON.parse(open(TOKEN_PATH));
});

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_VERSION = __ENV.API_VERSION || 'v1';
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '5m';
const TEARDOWN_TIMEOUT = __ENV.TEARDOWN_TIMEOUT || '1m';
const SCENARIO_MAX_DURATION = __ENV.SCENARIO_MAX_DURATION || '2m';
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '30s';

const COURSE_CAPACITY = Number(__ENV.COURSE_CAPACITY || 100);
const CANCEL_COUNT = Number(__ENV.CANCEL_COUNT || 10);
const REFILL_APPLICANT_COUNT = Number(__ENV.REFILL_APPLICANT_COUNT || 4900);
const VUS = Number(__ENV.VUS || 100);

const refillSuccess = new Counter('refill_success');
const refillFailed = new Counter('refill_failed');

const headers = {
  'Content-Type': 'application/json',
  'X-API-Version': API_VERSION,
};

export const options = {
  setupTimeout: SETUP_TIMEOUT,
  teardownTimeout: TEARDOWN_TIMEOUT,

  scenarios: {
    concurrent_refill_after_cancel: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: REFILL_APPLICANT_COUNT,
      maxDuration: SCENARIO_MAX_DURATION,
      gracefulStop: GRACEFUL_STOP,
    },
  },

  thresholds: {
    refill_success: [`count==${REFILL_APPLICANT_COUNT}`],
    refill_failed: ['count==0'],
    'http_req_duration{expected_response:true}': ['p(95)<20000'],
  },
};

export function setup() {
  const testId = Date.now();

  if (applicantTokens.length < COURSE_CAPACITY + REFILL_APPLICANT_COUNT) {
    fail(
        `tokens are not enough. tokenCount=${applicantTokens.length}, required=${COURSE_CAPACITY + REFILL_APPLICANT_COUNT}`,
    );
  }

  assertApplicantTokenUsable(applicantTokens[0]);

  const creatorEmail = `creator-${testId}@test.com`;
  const creatorPassword = 'password1234';

  signup({
    email: creatorEmail,
    password: creatorPassword,
    name: '테스트 크리에이터',
    role: 'CREATOR',
  });

  const creatorToken = login(creatorEmail, creatorPassword);

  const periodStart = new Date(Date.now() + 60 * 60 * 1000);
  const periodEnd = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

  const course = createCourse(creatorToken, {
    title: `취소 후 재신청 테스트 강의-${testId}`,
    description: 'k6 취소 후 재신청 동시성 테스트용 강의입니다.',
    price: 10000,
    capacity: COURSE_CAPACITY,
    periodStart: toLocalDateTimeString(periodStart),
    periodEnd: toLocalDateTimeString(periodEnd),
  });

  const courseId = course.courseId;

  if (!courseId) {
    fail(`courseId is missing. createCourse response=${JSON.stringify(course)}`);
  }

  openCourse(creatorToken, courseId);

  const enrollmentIds = [];

  for (let i = 0; i < COURSE_CAPACITY; i += 1) {
    const res = enroll(applicantTokens[i], courseId);

    const success = res.status === 200 || res.status === 201;

    if (!success) {
      console.error(`prefill enroll failed. index=${i}`);
      console.error(`status=${res.status}`);
      console.error(`body=${res.body}`);

      fail('prefill enroll failed');
    }

    const enrollmentId = extractEnrollmentIdAsString(res.body);

    if (!enrollmentId) {
      console.error(`enrollmentId is missing. body=${res.body}`);
      fail('enrollmentId is missing');
    }

    enrollmentIds.push(enrollmentId);
  }

  for (let i = 0; i < CANCEL_COUNT; i += 1) {
    const res = cancelEnrollment(applicantTokens[i], enrollmentIds[i]);

    const success = res.status === 200 || res.status === 204;

    if (!success) {
      console.error(`cancel failed. index=${i}`);
      console.error(`enrollmentId=${enrollmentIds[i]}`);
      console.error(`status=${res.status}`);
      console.error(`body=${res.body}`);

      fail('cancel failed');
    }
  }

  return {
    courseId,
    creatorToken,
  };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  const tokenIndex = COURSE_CAPACITY + index;
  const token = applicantTokens[tokenIndex];

  if (!token) {
    refillFailed.add(1);
    fail(`applicant token is missing. tokenIndex=${tokenIndex}`);
  }

  const res = enroll(token, data.courseId);

  const success = res.status === 200 || res.status === 201;

  if (success) {
    refillSuccess.add(1);
  } else {
    refillFailed.add(1);
  }

  check(res, {
    '취소된 좌석 재신청 성공 또는 정원 초과 처리': (r) =>
        r.status === 200 ||
        r.status === 201 ||
        r.status === 202 ||
        r.status === 400 ||
        r.status === 409,
  });
}

export function teardown(data) {
  const res = http.get(`${BASE_URL}/api/courses/${data.courseId}`, {
    headers: {
      ...headers,
      Authorization: `Bearer ${data.creatorToken}`,
    },
    tags: {
      name: 'GET /api/courses/{courseId}',
    },
  });

  const success = res.status === 200;

  check(res, {
    '강의 상세 조회 성공': () => success,
  });

  if (!success) {
    console.error('get course failed in teardown');
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);
    return;
  }

  const body = parseJsonOrFail(res, 'get course in teardown');

  check(body, {
    '남은 좌석은 0': (course) => course.seatLeftCount === 0,
    '현재 신청 인원은 capacity와 같다': (course) =>
        course.currentEnrollmentCount === COURSE_CAPACITY,
  });
}

function signup(body) {
  const res = http.post(
      `${BASE_URL}/api/auth/signup`,
      JSON.stringify(body),
      { headers },
  );

  const success = res.status === 200 || res.status === 201;

  check(res, {
    '회원가입 성공': () => success,
  });

  if (!success) {
    console.error('signup failed');
    console.error(`request=${JSON.stringify(body)}`);
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);

    fail('signup failed');
  }

  return res;
}

function login(email, password) {
  const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password }),
      { headers },
  );

  const success = res.status === 200;

  check(res, {
    '로그인 성공': () => success,
  });

  if (!success) {
    console.error('login failed');
    console.error(`email=${email}`);
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);

    fail('login failed');
  }

  const body = parseJsonOrFail(res, 'login');

  const token = body.accessToken || body.token || body.access_token;

  if (!token) {
    console.error(`login response=${JSON.stringify(body)}`);
    fail('access token is missing');
  }

  return token;
}

function createCourse(token, body) {
  const res = http.post(
      `${BASE_URL}/api/courses`,
      JSON.stringify(body),
      {
        headers: {
          ...headers,
          Authorization: `Bearer ${token}`,
        },
        tags: {
          name: 'POST /api/courses',
        },
      },
  );

  const success = res.status === 200 || res.status === 201;

  check(res, {
    '강의 생성 성공': () => success,
  });

  if (!success) {
    console.error('create course failed');
    console.error(`request=${JSON.stringify(body)}`);
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);

    fail('course creation failed');
  }

  const courseId = extractCourseIdAsString(res.body);

  if (!courseId) {
    console.error(`create course raw response=${res.body}`);
    fail('courseId is missing in create course response');
  }

  return {
    courseId,
    rawBody: res.body,
  };
}

function openCourse(token, courseId) {
  if (!courseId) {
    fail(`invalid courseId=${courseId}`);
  }

  const url = `${BASE_URL}/api/courses/${courseId}/open`;

  const res = http.post(
      url,
      null,
      {
        headers: {
          ...headers,
          Authorization: `Bearer ${token}`,
        },
        tags: {
          name: 'POST /api/courses/{courseId}/open',
        },
      },
  );

  const success = res.status === 200;

  check(res, {
    '강의 모집 시작 성공': () => success,
  });

  if (!success) {
    console.error('open course failed');
    console.error(`url=${url}`);
    console.error(`courseId=${courseId}`);
    console.error(`status=${res.status}`);
    console.error(`content-type=${res.headers['Content-Type']}`);
    console.error(`body=${res.body}`);

    fail('open course failed');
  }

  return parseJsonOrFail(res, 'open course');
}

function enroll(token, courseId) {
  return http.post(
      `${BASE_URL}/api/courses/${courseId}/enrollments`,
      null,
      {
        headers: {
          ...headers,
          Authorization: `Bearer ${token}`,
        },
        tags: {
          name: 'POST /api/courses/{courseId}/enrollments',
        },
      },
  );
}

function cancelEnrollment(token, enrollmentId) {
  return http.post(
      `${BASE_URL}/api/enrollments/${enrollmentId}/cancel`,
      null,
      {
        headers: {
          ...headers,
          Authorization: `Bearer ${token}`,
        },
        tags: {
          name: 'POST /api/enrollments/{enrollmentId}/cancel',
        },
      },
  );
}

function extractCourseIdAsString(body) {
  const matched = body.match(/"courseId"\s*:\s*"?(\d+)"?/);

  if (!matched) {
    return null;
  }

  return matched[1];
}

function extractEnrollmentIdAsString(body) {
  const matched = body.match(/"enrollmentId"\s*:\s*"?(\d+)"?/);

  if (!matched) {
    return null;
  }

  return matched[1];
}

function assertApplicantTokenUsable(token) {
  if (!token) {
    fail('first applicant token is missing');
  }

  const res = http.get(`${BASE_URL}/api/enrollments/me`, {
    headers: {
      ...headers,
      Authorization: `Bearer ${token}`,
    },
    tags: {
      name: 'GET /api/enrollments/me',
    },
  });

  const success = res.status === 200;

  check(res, {
    '수강생 토큰 사전 검증 성공': () => success,
  });

  if (!success) {
    console.error('applicant token preflight failed');
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);

    fail(
        'applicant token is not accepted by the running application. ' +
        'Regenerate k6/tokens.json from the same application/database before running this scenario.',
    );
  }
}

function parseJsonOrFail(res, context) {
  if (!res.body || res.body.trim() === '') {
    fail(`${context} response body is empty`);
  }

  try {
    return res.json();
  } catch (e) {
    console.error(`${context} response is not json`);
    console.error(`status=${res.status}`);
    console.error(`content-type=${res.headers['Content-Type']}`);
    console.error(`body=${res.body}`);

    fail(`${context} response json parse failed`);
  }
}

function toLocalDateTimeString(date) {
  const pad = (n) => String(n).padStart(2, '0');

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
