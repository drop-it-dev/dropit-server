import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const testRun = __ENV.TEST_RUN || 'drop-list-before-index';
const authToken = __ENV.AUTH_TOKEN;

if (!authToken) {
    throw new Error('AUTH_TOKEN is required.');
}

export const options = {
    scenarios: {
        openDropsClosingSoon: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 500,
            stages: [
                { duration: '15s', target: 20 },
                { duration: '15s', target: 50 },
                { duration: '15s', target: 100 },
                { duration: '15s', target: 200 },
                { duration: '15s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
    },
};

export default function () {
    const response = http.get(
        `${baseUrl}/drops?status=OPEN&sortType=CLOSING_SOON&page=0&size=20`,
        {
            headers: {
                Authorization: `Bearer ${authToken}`,
            },
            tags: {
                endpoint: 'drop-list-open-closing-soon',
                test_run: testRun,
            },
        }
    );

    check(response, {
        'Drop 목록 조회 응답이 200이다': (result) => result.status === 200,
        '한 페이지는 최대 20개다': (result) => {
            const content = result.json('content');
            return Array.isArray(content) && content.length <= 20;
        },
    });
}

export function handleSummary(data) {
    return {
        [`/results/${testRun}.json`]: JSON.stringify(data, null, 2),
    };
}
