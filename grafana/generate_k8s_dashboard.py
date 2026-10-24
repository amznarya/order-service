
import json
from pathlib import Path

JOB = 'orderservice-k8s'

panels = [
    (
        "Pod Availability",
        f'up{{job="{JOB}"}}',
        "short",
    ),
    (
        "Requests per Second by Pod",
        f'sum by (pod) (rate(http_server_requests_seconds_count{{job="{JOB}"}}[2m]))',
        "reqps",
    ),
    (
        "P95 Latency by Pod",
        f'histogram_quantile(0.95, sum by (le, pod) (rate(http_server_requests_seconds_bucket{{job="{JOB}"}}[2m])))',
        "s",
    ),
    (
        "HTTP 5xx per Second by Pod",
        f'sum by (pod) (rate(http_server_requests_seconds_count{{job="{JOB}",status=~"5.."}}[2m]))',
        "reqps",
    ),
    (
        "JVM Heap Usage by Pod",
        f'sum by (pod) (jvm_memory_used_bytes{{job="{JOB}",area="heap"}})',
        "bytes",
    ),
    (
        "JVM Live Threads by Pod",
        f'jvm_threads_live_threads{{job="{JOB}"}}',
        "short",
    ),
]

dashboard_panels = []

for index, (title, query, unit) in enumerate(panels):
    dashboard_panels.append({
        "id": index + 1,
        "title": title,
        "type": "timeseries",
        "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
        },
        "gridPos": {
            "x": (index % 2) * 12,
            "y": (index // 2) * 8,
            "w": 12,
            "h": 8
        },
        "targets": [{
            "refId": "A",
            "expr": query,
            "legendFormat": "{{pod}}"
        }],
        "fieldConfig": {
            "defaults": {
                "unit": unit
            },
            "overrides": []
        }
    })

dashboard = {
    "uid": "orderservice-k8s",
    "title": "Order Service - Kubernetes",
    "tags": ["kubernetes", "spring-boot", "observability"],
    "schemaVersion": 41,
    "version": 1,
    "refresh": "10s",
    "time": {
        "from": "now-15m",
        "to": "now"
    },
    "panels": dashboard_panels
}

output = Path("grafana/dashboards/orderservice-k8s.json")
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(dashboard, indent=2))

print(f"Generated: {output}")
