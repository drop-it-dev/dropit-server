import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://dropit-list-gateway:8080';
const testRun = __ENV.TEST_RUN || 'drop-list-million-soak';
const rate = Number(__ENV.REQUEST_RATE || 1000);
const duration = __ENV.DURATION || '17m';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const maxVUs = Number(__ENV.MAX_VUS || 1000);

if (![rate, preAllocatedVUs, maxVUs].every((value) => Number.isInteger(value) && value > 0)
    || maxVUs < preAllocatedVUs) {
    throw new Error('REQUEST_RATE and VU counts must be positive integers; MAX_VUS >= PRE_ALLOCATED_VUS.');
}

export const options = {
    setupTimeout: '3m',
    discardResponseBodies: true,
    scenarios: {
        sustainedListViews: {
            executor: 'constant-arrival-rate',
            rate,
            timeUnit: '1s',
            duration,
            preAllocatedVUs,
            maxVUs,
            gracefulStop: '30s',
        },
    },
    tags: { test_run: testRun, scenario_type: 'drop-list-million-soak' },
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
    check(response, { 'Drop list HTTP 200': (result) => result.status === 200 });
}

export function handleSummary(data) {
    return { [`/results/${testRun}.json`]: JSON.stringify(data, null, 2) };
}
