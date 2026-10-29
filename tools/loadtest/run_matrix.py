#!/usr/bin/env python3
"""按 10 / 50 / 200 三档并发依次跑 locust，并记录每档的 QPS、延迟分位与缓存命中率。

前置条件：
  1. mock LLM 已在 18081 监听
  2. 应用已在 18082 启动，且 --agentscope.dashscope.base-url 指向 mock

用法：
    python tools/loadtest/run_matrix.py --host http://127.0.0.1:18082 --duration 60s
    SESSION_MODE=shared python tools/loadtest/run_matrix.py --tag shared
"""
import argparse
import csv
import json
import os
import subprocess
import sys
import time
import urllib.request

TIERS = [10, 50, 200]


def cache_counter(host, result):
    """读 Caffeine 的命中/未命中计数器。"""
    url = (f"{host}/actuator/metrics/cache.gets"
           f"?tag=cache:bookRecommendation&tag=result:{result}")
    try:
        with urllib.request.urlopen(url, timeout=5) as resp:
            data = json.load(resp)
        for m in data["measurements"]:
            if m["statistic"] == "COUNT":
                return int(m["value"])
    except Exception as exc:  # noqa: BLE001 - 指标缺失不该中断压测
        print(f"  [warn] 读缓存指标失败: {exc}", file=sys.stderr)
    return None


def snapshot_cache(host):
    hit = cache_counter(host, "hit")
    miss = cache_counter(host, "miss")
    if hit is None or miss is None:
        return {"hit": hit, "miss": miss, "hit_rate": None}
    total = hit + miss
    return {"hit": hit, "miss": miss, "hit_rate": round(hit / total, 4) if total else None}


def read_percentiles(csv_path):
    """locust 的 _stats.csv 里每个请求一行，取汇总行（Name=Aggregated）。"""
    with open(csv_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row.get("Name") == "Aggregated":
                return row
    return {}


def run_tier(host, users, duration, out_prefix, session_mode, rate):
    env = dict(os.environ, SESSION_MODE=session_mode, PYTHONIOENCODING="utf-8",
               PYTHONUTF8="1")
    cmd = [sys.executable, "-m", "locust", "-f", "tools/loadtest/locustfile.py",
           "--headless", "-u", str(users), "-r", str(rate), "-t", duration,
           "--host", host, "--csv", out_prefix, "--csv-full-history", "--only-summary"]
    print(f"  locust -u {users} -t {duration} -> {out_prefix}_stats.csv", flush=True)
    # 有失败时 locust 退出码非 0，但 CSV 仍然完整，不能因此中断整个矩阵。
    subprocess.run(cmd, check=False, env=env)
    return read_percentiles(out_prefix + "_stats.csv")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="http://127.0.0.1:18082")
    ap.add_argument("--duration", default="60s")
    ap.add_argument("--tag", default="unique", help="结果文件名后缀，如 unique / shared")
    ap.add_argument("--session-mode", default=os.getenv("SESSION_MODE", "unique"))
    ap.add_argument("--tiers", default=",".join(str(t) for t in TIERS))
    ap.add_argument("--out-dir", default="results")
    args = ap.parse_args()

    tiers = [int(t) for t in args.tiers.split(",")]
    os.makedirs(args.out_dir, exist_ok=True)
    summary = {"host": args.host, "duration": args.duration,
               "session_mode": args.session_mode, "tiers": []}

    for users in tiers:
        before = snapshot_cache(args.host)
        prefix = os.path.join(args.out_dir, f"loadtest-{args.tag}-u{users}")
        started = time.time()
        # 渐进拉起：瞬时拉起会在首秒制造连接风暴，被压的其实是 mock 的 accept 队列，
        # 与应用自身容量无关。10 用户/秒 是 locust 常见的默认量级。
        rate = max(1, users // 10)
        row = run_tier(args.host, users, args.duration, prefix, args.session_mode, rate)
        elapsed = time.time() - started
        after = snapshot_cache(args.host)

        window = {}
        if None not in (before["hit"], after["hit"]):
            hits = after["hit"] - before["hit"]
            misses = after["miss"] - before["miss"]
            window = {"hits": hits, "misses": misses,
                      "hit_rate": round(hits / (hits + misses), 4) if hits + misses else None}
        else:
            window = {"hits": None, "misses": None, "hit_rate": None}

        entry = {
            "users": users,
            "wall_s": round(elapsed, 1),
            "requests": int(row.get("Request Count", 0) or 0),
            "failures": int(row.get("Failure Count", 0) or 0),
            "qps": round(float(row.get("Requests/s", 0) or 0), 2),
            "p50_ms": round(float(row.get("50%", 0) or 0), 1),
            "p95_ms": round(float(row.get("95%", 0) or 0), 1),
            "p99_ms": round(float(row.get("99%", 0) or 0), 1),
            "max_ms": round(float(row.get("100%", 0) or 0), 1),
            "avg_ms": round(float(row.get("Average Response Time", 0) or 0), 1),
            "cache_window": window,
        }
        print(f"  -> QPS {entry['qps']}, P50 {entry['p50_ms']}ms, "
              f"P95 {entry['p95_ms']}ms, P99 {entry['p99_ms']}ms, "
              f"失败 {entry['failures']}, 缓存命中率 {window['hit_rate']}", flush=True)
        summary["tiers"].append(entry)

    path = os.path.join(args.out_dir, f"loadtest-summary-{args.tag}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"结果写入 {path}")


if __name__ == "__main__":
    main()
