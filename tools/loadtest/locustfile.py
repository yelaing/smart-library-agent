"""压测脚本：压 POST /v1/chat/completions（非流式）。

后端 LLM 用 tools/loadtest/mock_llm.py 固定延迟替代，测的是服务端自身的并发能力，
不是阿里云的容量。

会话模式（环境变量 SESSION_MODE）：
  unique —— 每个虚拟用户独占一个 sessionId，测会话缓存的扩展性
  shared —— 所有虚拟用户共用同一个 sessionId，用来观察会话级串行化的代价

用法：
    SESSION_MODE=unique locust -f tools/loadtest/locustfile.py --headless \
        -u 50 -r 50 -t 60s --host http://localhost:18082 --csv results/loadtest-u50
"""
import os
import random
import uuid

from locust import HttpUser, task

QUERIES = [
    "想学并发编程，有什么书推荐",
    "推荐一本讲数据库性能优化的书",
    "有没有讲算法的书",
    "我想了解心理学",
    "机器学习入门看什么",
    "怎么做红烧肉",
    "中国古代历史有什么好书",
    "经济学入门读物",
    "给孩子的科普书",
    "操作系统原理推荐",
]

SESSION_MODE = os.getenv("SESSION_MODE", "unique")
SHARED_SESSION_ID = "loadtest-shared-session"


class LibraryUser(HttpUser):
    """一个虚拟用户 = 一个独立会话（unique 模式）。"""

    def wait_time(self):
        return 0.0

    def on_start(self):
        self.session_id = SHARED_SESSION_ID if SESSION_MODE == "shared" else uuid.uuid4().hex

    @task
    def chat(self):
        payload = {
            "model": "qwen-plus",
            "stream": False,
            "messages": [{"role": "user", "content": random.choice(QUERIES)}],
        }
        with self.client.post(
            "/v1/chat/completions",
            json=payload,
            headers={"X-Session-Id": self.session_id},
            name="POST /v1/chat/completions",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure(f"HTTP {response.status_code}: {response.text[:200]}")
            elif '"content"' not in response.text:
                response.failure("响应缺少 content 字段")
