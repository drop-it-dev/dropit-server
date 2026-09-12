import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const testRun = __ENV.TEST_RUN || 'drop-detail-concurrent-before';
const authToken = __ENV.AUTH_TOKEN;
const dropId = __ENV.DROP_ID;
const concurrentUsers = Number(__ENV.CONCURRENT_USERS || 10000);
const warmCache = (__ENV.WARM_CACHE || 'true').toLowerCase() === 'true';

if (!authToken) {
    throw new Error('AUTH_TOKEN is required.');
}

if (!dropId) {
    throw new Error('DROP_ID is required.');
}

if (!Number.isInteger(concurrentUsers) || concurrentUsers <= 0) {
    throw new Error('CONCURRENT_USERS must be a positive integer.');
}

export const options = {
    discardResponseBodies: false,
    scenarios: {
        openingBurst: {
            executor: 'per-vu-iterations',
            vus: concurrentUsers,
            iterations: 1,
            maxDuration: '3m',
        },
    },
    thresholds: {
        'http_req_failed{endpoint:drop-detail}': ['rate<0.01'],
        'http_req_duration{endpoint:drop-detail}': ['p(95)<2000'],
        checks: ['rate>0.99'],
    },
};

export function setup() {
    if (!warmCache) {
        return;
    }

    const response = http.get(`${baseUrl}/drops/${dropId}`, {
        headers: {
            Authorization: `Bearer ${authToken}`,
        },
        tags: {
            endpoint: 'cache-warmup',
            test_run: testRun,
        },
    });

    if (response.status !== 200) {
        throw new Error(`Cache warm-up failed with HTTP ${response.status}.`);
    }
}

export default function () {
    const response = http.get(`${baseUrl}/drops/${dropId}`, {
        headers: {
            Authorization: `Bearer ${authToken}`,
        },
        tags: {
            endpoint: 'drop-detail',
            test_run: testRun,
        },
    });

    check(response, {
        'Drop 상세 조회 응답이 200이다': (result) => result.status === 200,
        '요청한 Drop ID가 반환된다': (result) => {
            if (result.status !== 200) {
                return false;
            }

            try {
                return String(result.json('id')) === String(dropId);
            } catch (error) {
                return false;
            }
        },
    });
}

export function handleSummary(data) {
    return {
        [`/results/${testRun}.json`]: JSON.stringify(data, null, 2),
    };
}
