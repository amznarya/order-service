import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://host.docker.internal:8080';
const ORDER_ID = __ENV.ORDER_ID;

if (!ORDER_ID) {
    throw new Error('ORDER_ID is required');
}

export const options = {
    vus: 10,
    duration: '30s',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<300'],
        checks: ['rate>0.99']
    }
};

export default function () {

    const response = http.get(
        `${BASE_URL}/api/v1/orders/${ORDER_ID}`
    );

    check(response, {
        'HTTP status is 200': (r) => r.status === 200
    });

    sleep(0.2);
}