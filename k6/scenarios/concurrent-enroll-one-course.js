import http, { expectedStatuses } from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const TOKEN_PATH = __ENV.TOKEN_PATH || '../tokens.json';

const applicantTokens = new SharedArray('applicant tokens', function () {
  return JSON.parse(open(TOKEN_PATH));
});

http.setResponseCallback(expectedStatuses(200, 201, 202, 409));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_VERSION = __ENV.API_VERSION || 'v1';
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '5m';
const TEARDOWN_TIMEOUT = __ENV.TEARDOWN_TIMEOUT || '1m';
const SCENARIO_MAX_DURATION = __ENV.SCENARIO_MAX_DURATION || '2m';
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '30s';
const COURSE_CAPACITY = Number(__ENV.COURSE_CAPACITY || 100);
const APPLICANT_COUNT = Number(__ENV.APPLICANT_COUNT || 1000);
const VUS = Number(__ENV.VUS || 100);

const enrollSuccess = new Counter('enroll_success');
const enrollFailed = new Counter('enroll_failed');

const headers = {
  'Content-Type': 'application/json',
  'X-API-Version': API_VERSION,
};

export const options = {
  setupTimeout: SETUP_TIMEOUT,
  teardownTimeout: TEARDOWN_TIMEOUT,

  scenarios: {
    concurrent_enroll_to_one_course: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: APPLICANT_COUNT,
      maxDuration: SCENARIO_MAX_DURATION,
      gracefulStop: GRACEFUL_STOP,
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    enroll_success: [`count==${APPLICANT_COUNT}`],
    enroll_failed: ['count==0'],
  },
};

export function setup() {
  const testId = Date.now();

  if (applicantTokens.length < APPLICANT_COUNT) {
    fail(`tokens are not enough. tokenCount=${applicantTokens.length}, applicantCount=${APPLICANT_COUNT}`);
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
    title: `동시 신청 테스트 강의-${testId}`,
    description: 'k6 동시 수강 신청 테스트용 강의입니다.',
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

  return {
    courseId,
    creatorToken,
  };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  const token = applicantTokens[index];

  if (!token) {
    enrollFailed.add(1);
    fail(`applicant token is missing. index=${index}`);
  }

  const res = http.post(
      `${BASE_URL}/api/courses/${data.courseId}/enrollments`,
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

  const body = parseJsonOrNull(res);
  const responseBody = body ?? {};
  const success = res.status === 200 || res.status === 201;

  if (success) {
    enrollSuccess.add(1);
  } else {
    enrollFailed.add(1);

    if (index < 5) {
      console.error(`unexpected enroll response. index=${index}`);
      console.error(`status=${res.status}`);
      console.error(`body=${res.body}`);
    }
  }

  check(responseBody, {
    '수강 신청 200 응답': (enrollment) =>
        success &&
        enrollment != null &&
        enrollment.courseId != null &&
        enrollment.memberId != null &&
        (enrollment.status === 'PENDING' || enrollment.status === 'WAITLISTED'),
  });
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

function parseJsonOrNull(res) {
  if (!res.body || res.body.trim() === '') {
    return null;
  }

  try {
    return res.json();
  } catch (e) {
    return null;
  }
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

function extractCourseIdAsString(body) {
  const matched = body.match(/"courseId"\s*:\s*"?(\d+)"?/);

  if (!matched) {
    return null;
  }

  return matched[1];
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
