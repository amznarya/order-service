
import http from 'k6/http';
import { check } from 'k6';

const RATE = Number(__ENV.RATE || 50);
const ORDER_ID = __ENV.ORDER_ID;
const BASE_URL = 'http://host.docker.internal:8080';

if (!ORDER_ID || !Number.isInteger(RATE) || RATE <= 0) {
    throw new Error('Valid ORDER_ID and RATE are required');
}

export const options = {
    discardResponseBodies: true,

    summaryTrendStats: [
        'avg', 'min', 'med', 'max',
        'p(90)', 'p(95)', 'p(99)'
    ],

    scenarios: {
        cached_reads: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 60,
            maxVUs: 150,
            // Inside the cached_reads scenario:
            gracefulStop: '10s',
        }
    },

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<300'],
        checks: ['rate>0.99'],
        dropped_iterations: ['count==0']
    }
};

export default function () {
    const response = http.get(
        `${BASE_URL}/api/v1/orders/${ORDER_ID}`,
        { timeout: '5s' }
    );

    check(response, {
        'status is 200': (r) => r.status === 200
    });
}
