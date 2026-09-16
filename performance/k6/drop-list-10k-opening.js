import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const openingDuration = new Trend('drop_list_opening_duration', true);

const baseUrl = __ENV.BASE_URL || 'http://dropit-list-gateway:8080';
const testRun = __ENV.TEST_RUN || 'drop-list-10k-opening';
const users = Number(__ENV.CONCURRENT_USERS || 10000);

if (!Number.isInteger(users) || users < 1) {
    throw new Error('CONCURRENT_USERS must be a positive integer.');
}

export const options = {
    setupTimeout: '3m',
    discardResponseBodies: false,
    scenarios: {
        opening: {
            executor: 'per-vu-iterations',
            vus: users,
            iterations: 1,
            maxDuration: '5m',
            gracefulStop: '30s',
        },
    },
    tags: { test_run: testRun, scenario_type: 'drop-list-opening' },
};

export function setup() {
    for (const app of ['dropit-list-app-1', 'dropit-list-app-2']) {
        const response = http.get(`http://${app}:8080/drops?sortType=LATEST&page=0&size=20`, {
            tags: { endpoint: 'warmup' },
            timeout: '90s',
        });
        if (response.status !== 200) {
            throw new Error(`${app} warm-up failed: HTTP ${response.status}`);
        }
    }
}

export default function () {
    const response = http.get(`${baseUrl}/drops?sortType=LATEST&page=0&size=20`, {
        tags: { endpoint: 'drop-list-first-page' },
        timeout: '40s',
    });
    openingDuration.add(response.timings.duration);
    check(response, { 'Drop list HTTP 200': (result) => result.status === 200 });
}

export function handleSummary(data) {
    return { [`/results/${testRun}.json`]: JSON.stringify(data, null, 2) };
}
