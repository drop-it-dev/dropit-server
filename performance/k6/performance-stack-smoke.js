import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL;
const testRun = __ENV.TEST_RUN || 'performance-stack-smoke';

if (!baseUrl) {
    throw new Error('BASE_URL is required.');
}

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
    },
};

export default function () {
    const response = http.get(`${baseUrl}/ping`, {
        tags: {
            endpoint: 'performance-stack-ping',
            test_run: testRun,
        },
    });

    check(response, {
        'InfluxDB ping 응답이 204이다': (result) => result.status === 204,
    });
}

export function handleSummary(data) {
    return {
        [`/results/${testRun}.json`]: JSON.stringify(data, null, 2),
    };
}
