
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = 'http://host.docker.internal:8080';
const TOTAL_ORDERS = 200;

const cacheMiss = new Trend('cache_miss_ms', true);
const cacheHit = new Trend('cache_hit_ms', true);

export const options = {
    scenarios: {
        cache_comparison: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: TOTAL_ORDERS,
            maxDuration: '2m',
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
    },
};

// Runs once before virtual users start.
// POST creation does not populate our GET cache.
export function setup() {
    const orderIds = [];

    for (let i = 0; i < TOTAL_ORDERS; i++) {
        const response = http.post(
            `${BASE_URL}/api/v1/orders`,
            JSON.stringify({
                productId: `cache-test-${i}`,
                quantity: 1,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'Idempotency-Key': crypto.randomUUID(),
                },
            }
        );

        if (response.status !== 201) {
            throw new Error(`Order creation failed: ${response.status}`);
        }

        orderIds.push(response.json('id'));
    }

    return orderIds;
}

export default function (orderIds) {
    // Each iteration gets a different order.
    const index = exec.scenario.iterationInTest;
    const orderId = orderIds[index];

    const url = `${BASE_URL}/api/v1/orders/${orderId}`;

    // First GET: Redis cache miss.
    const miss = http.get(url);
    cacheMiss.add(miss.timings.duration);

    check(miss, {
        'Cache miss returns 200': (r) => r.status === 200,
    });

    // Second GET: Redis cache hit.
    const hit = http.get(url);
    cacheHit.add(hit.timings.duration);

    check(hit, {
        'Cache hit returns 200': (r) => r.status === 200,
    });
}
