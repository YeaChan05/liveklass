import http, { expectedStatuses } from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

http.setResponseCallback(expectedStatuses(200, 201, 202, 204, 400, 409));

const TOKEN_PATH = __ENV.TOKEN_PATH || '../tokens.json';

const applicantTokens = new SharedArray('applicant tokens', function () {
  return JSON.parse(open(TOKEN_PATH));
});

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_VERSION = __ENV.API_VERSION || 'v1';
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '5m';
const TEARDOWN_TIMEOUT = __ENV.TEARDOWN_TIMEOUT || '1m';
const SCENARIO_MAX_DURATION = __ENV.SCENARIO_MAX_DURATION || '12m';
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '30s';

const COURSE_CAPACITY = Number(__ENV.COURSE_CAPACITY || 100);
const APPLICANT_COUNT = Number(__ENV.APPLICANT_COUNT || 10000);
const INITIAL_CONFIRM_COUNT = Number(__ENV.INITIAL_CONFIRM_COUNT || 50);
const EXPIRE_PENDING_COUNT = Number(__ENV.EXPIRE_PENDING_COUNT || 50);
const FIRST_WAVE_APPLICANT_COUNT = Number(__ENV.FIRST_WAVE_APPLICANT_COUNT || 100);
const SECOND_WAVE_APPLICANT_COUNT = Number(__ENV.SECOND_WAVE_APPLICANT_COUNT || 100);
const THIRD_WAVE_APPLICANT_COUNT = Number(__ENV.THIRD_WAVE_APPLICANT_COUNT || 100);
const VUS = Number(__ENV.VUS || 150);

const WAIT_BEFORE_FIRST_REFILL_SECONDS = Number(__ENV.WAIT_BEFORE_FIRST_REFILL_SECONDS || 70);
const WAIT_BETWEEN_WAVE1_AND_WAVE2_SECONDS = Number(__ENV.WAIT_BETWEEN_WAVE1_AND_WAVE2_SECONDS || 140);
const WAIT_BETWEEN_WAVE2_AND_WAVE3_SECONDS = Number(__ENV.WAIT_BETWEEN_WAVE2_AND_WAVE3_SECONDS || 140);

const EXPECTED_FINAL_SEAT_LEFT_COUNT = EXPIRE_PENDING_COUNT;
const EXPECTED_FINAL_CURRENT_ENROLLMENT_COUNT = INITIAL_CONFIRM_COUNT;

const initialPending = new Counter('initial_pending');
const initialWaitlisted = new Counter('initial_waitlisted');
const initialConfirmed = new Counter('initial_confirmed');

const firstWaveWaitlisted = new Counter('first_wave_waitlisted');
const secondWaveWaitlisted = new Counter('second_wave_waitlisted');
const thirdWaveWaitlisted = new Counter('third_wave_waitlisted');

const finalSeatLeftMatched = new Counter('final_seat_left_matched');
const finalCurrentEnrollmentCountMatched = new Counter('final_current_enrollment_count_matched');

const scenarioFailed = new Counter('scenario_failed');

const headers = {
  'Content-Type': 'application/json',
  'X-API-Version': API_VERSION,
};

export const options = {
  setupTimeout: SETUP_TIMEOUT,
  teardownTimeout: TEARDOWN_TIMEOUT,

  scenarios: {
    first_wave: {
      executor: 'shared-iterations',
      vus: Math.min(VUS, FIRST_WAVE_APPLICANT_COUNT),
      iterations: FIRST_WAVE_APPLICANT_COUNT,
      startTime: '0s',
      exec: 'firstWave',
      maxDuration: SCENARIO_MAX_DURATION,
      gracefulStop: GRACEFUL_STOP,
    },

    second_wave: {
      executor: 'shared-iterations',
      vus: Math.min(VUS, SECOND_WAVE_APPLICANT_COUNT),
      iterations: SECOND_WAVE_APPLICANT_COUNT,
      startTime: `${WAIT_BETWEEN_WAVE1_AND_WAVE2_SECONDS}s`,
      exec: 'secondWave',
      maxDuration: SCENARIO_MAX_DURATION,
      gracefulStop: GRACEFUL_STOP,
    },

    third_wave: {
      executor: 'shared-iterations',
      vus: Math.min(VUS, THIRD_WAVE_APPLICANT_COUNT),
      iterations: THIRD_WAVE_APPLICANT_COUNT,
      startTime: `${WAIT_BETWEEN_WAVE1_AND_WAVE2_SECONDS + WAIT_BETWEEN_WAVE2_AND_WAVE3_SECONDS}s`,
      exec: 'thirdWave',
      maxDuration: SCENARIO_MAX_DURATION,
      gracefulStop: GRACEFUL_STOP,
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{expected_response:true}': ['p(95)<20000'],

    initial_pending: [`count==${COURSE_CAPACITY}`],
    initial_waitlisted: [`count==${APPLICANT_COUNT - COURSE_CAPACITY}`],
    initial_confirmed: [`count==${INITIAL_CONFIRM_COUNT}`],

    first_wave_waitlisted: [`count==${FIRST_WAVE_APPLICANT_COUNT}`],
    second_wave_waitlisted: [`count==${SECOND_WAVE_APPLICANT_COUNT}`],
    third_wave_waitlisted: [`count==${THIRD_WAVE_APPLICANT_COUNT}`],

    final_seat_left_matched: ['count==1'],
    final_current_enrollment_count_matched: ['count==1'],

    scenario_failed: ['count==0'],
  },
};

export function setup() {
  validateConfiguration();

  const testId = Date.now();

  if (applicantTokens.length < APPLICANT_COUNT) {
    fail(`tokens are not enough. tokenCount=${applicantTokens.length}, applicantCount=${APPLICANT_COUNT}`);
  }

  assertApplicantTokensValidLongEnough();
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

  const periodStart = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const periodEnd = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

  const course = createCourse(creatorToken, {
    title: `결제 만료 후 좌석 반환 테스트 강의-${testId}`,
    description: 'k6 결제 만료 후 좌석 반환 및 대기열 유지 검증용 강의입니다.',
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

  const pendingEnrollmentIds = [];

  for (let i = 0; i < COURSE_CAPACITY; i += 1) {
    const res = enroll(applicantTokens[i], courseId);
    const body = parseJsonOrFail(res, `initial pending enroll index=${i}`);

    if (isPendingEnrollment(res, body)) {
      initialPending.add(1);
      pendingEnrollmentIds.push(extractEnrollmentIdAsString(res.body));
    } else {
      scenarioFailed.add(1);
      console.error(`unexpected initial pending enroll response. index=${i}`);
      console.error(`status=${res.status}`);
      console.error(`body=${res.body}`);
      fail('unexpected initial pending enroll response');
    }
  }

  if (pendingEnrollmentIds.length !== COURSE_CAPACITY) {
    scenarioFailed.add(1);
    fail(`pending enrollment count mismatch. actual=${pendingEnrollmentIds.length}, expected=${COURSE_CAPACITY}`);
  }

  for (let i = 0; i < INITIAL_CONFIRM_COUNT; i += 1) {
    const res = confirmEnrollment(applicantTokens[i], pendingEnrollmentIds[i]);
    const body = parseJsonOrFail(res, `initial confirm index=${i}`);
    const success = isConfirmedEnrollment(res, body);

    check(res, {
      '초기 결제 확정 성공': () => success,
    });

    if (!success) {
      scenarioFailed.add(1);
      console.error(`initial confirm failed. index=${i}`);
      console.error(`enrollmentId=${pendingEnrollmentIds[i]}`);
      console.error(`status=${res.status}`);
      console.error(`body=${res.body}`);
      fail('initial confirm failed');
    }

    initialConfirmed.add(1);
  }

  for (let i = COURSE_CAPACITY; i < APPLICANT_COUNT; i += 1) {
    const res = enroll(applicantTokens[i], courseId);
    const body = parseJsonOrFail(res, `initial waitlist enroll index=${i}`);

    if (isWaitlisted(res, body)) {
      initialWaitlisted.add(1);
    } else {
      scenarioFailed.add(1);
      console.error(`unexpected initial waitlist enroll response. index=${i}`);
      console.error(`status=${res.status}`);
      console.error(`body=${res.body}`);

      if (isPendingEnrollment(res, body)) {
        console.error(
            'initial waitlist setup overlapped with payment expiration. ' +
            'Restart the application with a longer ENROLLMENT_PAYMENT_PENDING_EXPIRES_IN.',
        );
      }

      fail('unexpected initial waitlist enroll response');
    }
  }

  console.log(
      `waiting before first wave. seconds=${WAIT_BEFORE_FIRST_REFILL_SECONDS}, ` +
      `expiredPendingTarget=${EXPIRE_PENDING_COUNT}, ` +
      `firstWaveApplicants=${FIRST_WAVE_APPLICANT_COUNT}, ` +
      `secondWaveApplicants=${SECOND_WAVE_APPLICANT_COUNT}, ` +
      `thirdWaveApplicants=${THIRD_WAVE_APPLICANT_COUNT}`,
  );

  sleep(WAIT_BEFORE_FIRST_REFILL_SECONDS);

  return {
    courseId,
    creatorToken,
  };
}

export function firstWave(data) {
  const index = exec.scenario.iterationInTest;

  verifyWaitlistMaintained({
    courseId: data.courseId,
    waveName: 'first',
    waveIndex: index,
    tokenIndex: COURSE_CAPACITY + index,
    counter: firstWaveWaitlisted,
  });
}

export function secondWave(data) {
  const index = exec.scenario.iterationInTest;

  verifyWaitlistMaintained({
    courseId: data.courseId,
    waveName: 'second',
    waveIndex: index,
    tokenIndex: COURSE_CAPACITY + FIRST_WAVE_APPLICANT_COUNT + index,
    counter: secondWaveWaitlisted,
  });
}

export function thirdWave(data) {
  const index = exec.scenario.iterationInTest;

  verifyWaitlistMaintained({
    courseId: data.courseId,
    waveName: 'third',
    waveIndex: index,
    tokenIndex: COURSE_CAPACITY + FIRST_WAVE_APPLICANT_COUNT + SECOND_WAVE_APPLICANT_COUNT + index,
    counter: thirdWaveWaitlisted,
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
    scenarioFailed.add(1);
    console.error('get course failed in teardown');
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);
    return;
  }

  const body = parseJsonOrFail(res, 'get course in teardown');

  const seatLeftMatched = body.seatLeftCount === EXPECTED_FINAL_SEAT_LEFT_COUNT;
  const currentEnrollmentCountMatched =
      body.currentEnrollmentCount === EXPECTED_FINAL_CURRENT_ENROLLMENT_COUNT;

  check(body, {
    '남은 좌석은 만료된 결제 대기 수와 같다': () => seatLeftMatched,
    '현재 신청 인원은 초기 확정 수와 같다': () => currentEnrollmentCountMatched,
  });

  if (seatLeftMatched) {
    finalSeatLeftMatched.add(1);
  } else {
    scenarioFailed.add(1);
    console.error('seatLeftCount mismatch in teardown');
    console.error(`expected=${EXPECTED_FINAL_SEAT_LEFT_COUNT}`);
    console.error(`actual=${body.seatLeftCount}`);
    console.error(`course=${JSON.stringify(body)}`);
  }

  if (currentEnrollmentCountMatched) {
    finalCurrentEnrollmentCountMatched.add(1);
  } else {
    scenarioFailed.add(1);
    console.error('currentEnrollmentCount mismatch in teardown');
    console.error(`expected=${EXPECTED_FINAL_CURRENT_ENROLLMENT_COUNT}`);
    console.error(`actual=${body.currentEnrollmentCount}`);
    console.error(`course=${JSON.stringify(body)}`);
  }
}

function verifyWaitlistMaintained({ courseId, waveName, waveIndex, tokenIndex, counter }) {
  const token = applicantTokens[tokenIndex];

  if (!token) {
    scenarioFailed.add(1);
    fail(`${waveName} wave applicant token is missing. tokenIndex=${tokenIndex}`);
  }

  const beforePending = findMyPendingEnrollment(token, courseId);

  if (beforePending.enrollmentId) {
    scenarioFailed.add(1);
    console.error(`${waveName} wave applicant unexpectedly has pending enrollment before retry. index=${waveIndex}`);
    console.error(`courseId=${courseId}`);
    console.error(`tokenIndex=${tokenIndex}`);
    console.error(`body=${beforePending.rawBody}`);
    fail(`${waveName} wave applicant unexpectedly has pending enrollment before retry`);
  }

  const res = enroll(token, courseId);
  const body = parseJsonOrFail(res, `${waveName} wave waitlist retry index=${waveIndex}`);

  const waitlisted = isWaitlisted(res, body);

  const checks = {};
  checks[`${waveName} wave 대기열 유지`] = () => waitlisted;

  check(res, checks);

  if (!waitlisted) {
    scenarioFailed.add(1);

    console.error(`${waveName} wave applicant was not kept waitlisted. index=${waveIndex}`);
    console.error(`courseId=${courseId}`);
    console.error(`tokenIndex=${tokenIndex}`);
    console.error(`status=${res.status}`);
    console.error(`body=${res.body}`);

    if (isPendingEnrollment(res, body)) {
      console.error(
          `${waveName} wave applicant received PENDING enrollment. ` +
          'If this is intended behavior, use the auto-promotion scenario instead.',
      );
    }

    fail(`${waveName} wave applicant was not kept waitlisted`);
  }

  const afterPending = findMyPendingEnrollment(token, courseId);

  if (afterPending.enrollmentId) {
    scenarioFailed.add(1);
    console.error(`${waveName} wave applicant unexpectedly has pending enrollment after retry. index=${waveIndex}`);
    console.error(`courseId=${courseId}`);
    console.error(`tokenIndex=${tokenIndex}`);
    console.error(`body=${afterPending.rawBody}`);
    fail(`${waveName} wave applicant unexpectedly has pending enrollment after retry`);
  }

  counter.add(1);
}

function findMyPendingEnrollment(token, courseId) {
  const res = http.get(`${BASE_URL}/api/enrollments/me`, {
    headers: {
      ...headers,
      Authorization: `Bearer ${token}`,
    },
    tags: {
      name: 'GET /api/enrollments/me',
    },
  });

  if (res.status !== 200) {
    return {
      enrollmentId: null,
      rawBody: res.body,
    };
  }

  return {
    enrollmentId: extractPendingEnrollmentIdForCourse(res.body, courseId),
    rawBody: res.body,
  };
}

function extractPendingEnrollmentIdForCourse(body, courseId) {
  const objects = body.match(/\{[^{}]*\}/g) || [];

  for (const object of objects) {
    const objectCourseId = extractCourseIdAsString(object);
    const enrollmentId = extractEnrollmentIdAsString(object);

    if (objectCourseId === String(courseId) && enrollmentId && /"status"\s*:\s*"PENDING"/.test(object)) {
      return enrollmentId;
    }
  }

  return null;
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

function confirmEnrollment(token, enrollmentId) {
  return http.post(
      `${BASE_URL}/api/enrollments/${enrollmentId}/confirm`,
      null,
      {
        headers: {
          ...headers,
          Authorization: `Bearer ${token}`,
        },
        tags: {
          name: 'POST /api/enrollments/{enrollmentId}/confirm',
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

function assertApplicantTokensValidLongEnough() {
  const requiredTtlSeconds =
      WAIT_BEFORE_FIRST_REFILL_SECONDS +
      WAIT_BETWEEN_WAVE1_AND_WAVE2_SECONDS +
      WAIT_BETWEEN_WAVE2_AND_WAVE3_SECONDS +
      120;

  const tokenIndexes = [
    0,
    COURSE_CAPACITY - 1,
    COURSE_CAPACITY,
    COURSE_CAPACITY + FIRST_WAVE_APPLICANT_COUNT + SECOND_WAVE_APPLICANT_COUNT + THIRD_WAVE_APPLICANT_COUNT - 1,
  ];

  tokenIndexes.forEach((tokenIndex) => {
    const token = applicantTokens[tokenIndex];

    if (!token) {
      fail(`applicant token is missing. tokenIndex=${tokenIndex}`);
    }

    const exp = extractJwtExp(token);

    if (!exp) {
      fail(`applicant token exp claim is missing. tokenIndex=${tokenIndex}`);
    }

    const remainingTtlSeconds = exp - Math.floor(Date.now() / 1000);

    if (remainingTtlSeconds <= requiredTtlSeconds) {
      fail(
          `applicant token expires before scenario can finish. ` +
          `tokenIndex=${tokenIndex}, remainingTtlSeconds=${remainingTtlSeconds}, ` +
          `requiredTtlSeconds=${requiredTtlSeconds}. ` +
          `Restart the application with MEMBER_TOKEN_GENERATOR_ENABLED=true to regenerate k6/tokens.json before running this scenario.`,
      );
    }
  });
}

function extractJwtExp(token) {
  const parts = token.split('.');

  if (parts.length < 2) {
    return null;
  }

  const payload = decodeBase64Url(parts[1]);

  if (!payload) {
    return null;
  }

  try {
    const body = JSON.parse(payload);
    return Number(body.exp || 0);
  } catch (e) {
    return null;
  }
}

function decodeBase64Url(value) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  let buffer = 0;
  let bits = 0;
  let output = '';

  for (let i = 0; i < normalized.length; i += 1) {
    const char = normalized[i];

    if (char === '=') {
      break;
    }

    const index = alphabet.indexOf(char);

    if (index < 0) {
      return null;
    }

    buffer = (buffer << 6) | index;
    bits += 6;

    if (bits >= 8) {
      bits -= 8;
      output += String.fromCharCode((buffer >> bits) & 0xff);
    }
  }

  return output;
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

function isPendingEnrollment(res, body) {
  return (res.status === 200 || res.status === 201) &&
      body &&
      body.enrollmentId != null &&
      body.status === 'PENDING';
}

function isWaitlisted(res, body) {
  return (res.status === 200 || res.status === 201 || res.status === 202) &&
      body &&
      body.status === 'WAITLISTED';
}

function isConfirmedEnrollment(res, body) {
  return (res.status === 200 || res.status === 201) &&
      body &&
      body.enrollmentId != null &&
      body.status === 'CONFIRMED';
}

function validateConfiguration() {
  if (COURSE_CAPACITY <= 0) {
    fail(`COURSE_CAPACITY must be positive. COURSE_CAPACITY=${COURSE_CAPACITY}`);
  }

  if (APPLICANT_COUNT <= COURSE_CAPACITY) {
    fail(`APPLICANT_COUNT must be greater than COURSE_CAPACITY. applicantCount=${APPLICANT_COUNT}, courseCapacity=${COURSE_CAPACITY}`);
  }

  if (INITIAL_CONFIRM_COUNT + EXPIRE_PENDING_COUNT !== COURSE_CAPACITY) {
    fail(
        `INITIAL_CONFIRM_COUNT + EXPIRE_PENDING_COUNT must equal COURSE_CAPACITY. ` +
        `initialConfirm=${INITIAL_CONFIRM_COUNT}, expirePending=${EXPIRE_PENDING_COUNT}, courseCapacity=${COURSE_CAPACITY}`,
    );
  }

  const waveApplicantCount =
      FIRST_WAVE_APPLICANT_COUNT +
      SECOND_WAVE_APPLICANT_COUNT +
      THIRD_WAVE_APPLICANT_COUNT;

  const waitlistedCount = APPLICANT_COUNT - COURSE_CAPACITY;

  if (waveApplicantCount > waitlistedCount) {
    fail(
        `FIRST_WAVE_APPLICANT_COUNT + SECOND_WAVE_APPLICANT_COUNT + THIRD_WAVE_APPLICANT_COUNT ` +
        `must be less than or equal to waitlisted count. ` +
        `firstWaveApplicantCount=${FIRST_WAVE_APPLICANT_COUNT}, ` +
        `secondWaveApplicantCount=${SECOND_WAVE_APPLICANT_COUNT}, ` +
        `thirdWaveApplicantCount=${THIRD_WAVE_APPLICANT_COUNT}, ` +
        `waitlistedCount=${waitlistedCount}`,
    );
  }

  if (VUS <= 0) {
    fail(`VUS must be positive. VUS=${VUS}`);
  }
}

function toLocalDateTimeString(date) {
  const pad = (n) => String(n).padStart(2, '0');

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
