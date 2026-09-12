import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const testRun = __ENV.TEST_RUN || 'drop-detail-spike-before';
const authToken = __ENV.AUTH_TOKEN;
const dropId = __ENV.DROP_ID;

function numberFromEnvironment(name, defaultValue) {
    const value = Number(__ENV[name] || defaultValue);

    if (!Number.isFinite(value) || value <= 0) {
        throw new Error(`${name} must be a positive number.`);
    }

    return value;
}

if (!authToken) {
    throw new Error('AUTH_TOKEN is required.');
}

if (!dropId) {
    throw new Error('DROP_ID is required.');
}

const normalRps = numberFromEnvironment('NORMAL_RPS', 100);
const spikeRps = numberFromEnvironment('SPIKE_RPS', 1000);
const preAllocatedVUs = numberFromEnvironment('PRE_ALLOCATED_VUS', 200);
const maxVUs = numberFromEnvironment('MAX_VUS', 2000);

export const options = {
    discardResponseBodies: false,
    scenarios: {
        dropDetailAtOpeningTime: {
            executor: 'ramping-arrival-rate',
            startRate: normalRps,
            timeUnit: '1s',
            preAllocatedVUs,
            maxVUs,
            stages: [
                { duration: '30s', target: normalRps },
                { duration: '1s', target: spikeRps },
                { duration: '1m', target: spikeRps },
                { duration: '15s', target: 0 },
            ],
            gracefulStop: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
    },
};

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
