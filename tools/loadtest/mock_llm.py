#!/usr/bin/env python3
"""本地 mock：同时模拟 DashScope 的对话接口与 Embedding 接口，供压测使用。

为什么不用真实模型压测：
  1. 费用 —— 200 并发跑几轮就是真实 token 消耗；
  2. 干扰 —— 真实模型的延迟波动（1~5s）会盖住服务端自身的排队情况，
     测出来的是阿里云的容量，不是这个服务的并发能力；
  3. 可复现 —— 外部服务抖一下，两次压测结果就不可比。

mock 固定延迟（--delay-ms / --embed-delay-ms）模拟外部耗时，测的是服务端在
给定依赖延迟下的表现。

Embedding 返回的向量每维约为 1 加少量噪声，任意两条的余弦相似度≈0.98，
保证推荐结果不会因相似度阈值被全部丢弃（否则 Caffeine 缓存永远不落条目）。

用法：
    python tools/loadtest/mock_llm.py --port 18081 --delay-ms 50 --embed-delay-ms 180
    # 应用侧：
    #   --agentscope.dashscope.base-url=http://127.0.0.1:18081
    #   --library.embedding.base-url=http://127.0.0.1:18081/compatible-mode/v1/embeddings
"""
import argparse
import hashlib
import json
import math
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DELAY_MS = 50
EMBED_DELAY_MS = 180
EMBED_DIMS = 1024
REPLY = "这是压测用的固定回复。"
TOOL_CALL = True
_lock = threading.Lock()
_stats = {"requests": 0, "logged": 0, "chat": 0, "embeddings": 0, "embedding_items": 0,
          "tool_call_responses": 0, "text_responses": 0}


def _vector(text):
    """确定性伪向量：方向基本一致，彼此余弦≈0.98，且同一文本每次相同。"""
    seed = int.from_bytes(hashlib.sha1(text.encode("utf-8")).digest()[:8], "big")
    values = []
    x = seed or 1
    for _ in range(EMBED_DIMS):
        x = (x * 6364136223846793005 + 1442695040888963407) & ((1 << 64) - 1)
        noise = ((x >> 33) / float(1 << 31)) - 1.0  # [-1, 1)
        values.append(1.0 + 0.2 * noise)
    norm = math.sqrt(sum(v * v for v in values))
    return [v / norm for v in values]


def _messages(body):
    return (body.get("input") or {}).get("messages") or []


def _user_text(body):
    for m in reversed(_messages(body)):
        if m.get("role") == "user":
            content = m.get("content")
            if isinstance(content, str):
                return content
            if isinstance(content, list):
                return "".join(p.get("text", "") for p in content if isinstance(p, dict))
    return "推荐几本讲并发编程的书"


def _is_tool_result_present(body):
    """最后一条是工具返回，说明本轮 ReAct 已拿到检索结果，该给最终答案了。

    只看最后一条而不是整段历史：同一会话的后续轮次历史里本来就有旧的 tool 消息，
    若按整段历史判断，每个会话只有第一句话会走 RAG 路径，之后全是纯文本。
    """
    messages = _messages(body)
    return bool(messages) and messages[-1].get("role") == "tool"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _read_body(self):
        """应用侧 Embedding 走 JDK HttpClient，body 长度未知时用 chunked，需自行拼装。"""
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            chunks = []
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                size = int(line.split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length) if length else b"{}"

    def do_POST(self):  # noqa: N802
        raw = self._read_body()
        try:
            body = json.loads(raw) if raw else {}
        except ValueError:
            body = {}
        is_embedding = "embeddings" in self.path
        with _lock:
            _stats["requests"] += 1
            _stats["embeddings" if is_embedding else "chat"] += 1
            if is_embedding:
                inputs = body.get("input") or []
                _stats["embedding_items"] += len(inputs) if isinstance(inputs, list) else 1
            n = _stats["requests"]
            should_log = not is_embedding and _stats["logged"] < 3
            if should_log:
                _stats["logged"] += 1
        if should_log:
            msgs = _messages(body)
            print(f"\n--- 对话请求 {n} roles={[m.get('role') for m in msgs]}\n"
                  f"    tail={json.dumps(msgs[-3:], ensure_ascii=False)[:800]}\n---\n", flush=True)

        if is_embedding:
            time.sleep(EMBED_DELAY_MS / 1000.0)
            self._send_embeddings(body)
            return

        time.sleep(DELAY_MS / 1000.0)

        if TOOL_CALL and not _is_tool_result_present(body):
            self._send_tool_call(_user_text(body))
        else:
            self._send_stream()

    def do_GET(self):  # noqa: N802
        """GET /__stats 读计数，GET /__reset 清零 —— 压测脚本用它统计缓存命中率。

        每次缓存未命中才会调一次 Embedding，命中则完全不发请求，
        所以 embedding 调用数 / recommend_book 调用数 就是未命中率。
        """
        if self.path.startswith("/__reset"):
            with _lock:
                for key in _stats:
                    _stats[key] = 0
            payload = {"reset": True}
        elif self.path.startswith("/__stats"):
            with _lock:
                payload = dict(_stats)
        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        data = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _send_embeddings(self, body):
        inputs = body.get("input") or []
        if isinstance(inputs, str):
            inputs = [inputs]
        data = [{"object": "embedding", "index": i, "embedding": _vector(t)}
                for i, t in enumerate(inputs)]
        payload = json.dumps({
            "object": "list",
            "data": data,
            "model": body.get("model", "text-embedding-v4"),
            "usage": {"prompt_tokens": len(inputs), "total_tokens": len(inputs)},
        }).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _frame(self, seq, message, finish_reason):
        """复刻真实 DashScope 的 SSE 帧：id/event/HTTP_STATUS 前缀，data 后无空格，无 [DONE] 结束帧。

        格式取自 results/_real_sse.txt 的抓包，字段结构不一致时 AgentScope 会把
        工具参数解析成 null。
        """
        body = {
            "output": {"choices": [{"message": message, "index": 0,
                                    "finish_reason": finish_reason}]},
            "usage": {"total_tokens": 2, "output_tokens": 1, "input_tokens": 1},
            "request_id": "mock-stream",
        }
        return (f"id:{seq}\nevent:result\n:HTTP_STATUS/200\n"
                + "data:" + json.dumps(body, ensure_ascii=False) + "\n\n")

    def _send_tool_call(self, query):
        """第一轮固定推荐 recommend_book，把用户原话当检索词 —— 和真实 agent 行为一致。

        这样压测才会走到「查询向量化 + 余弦扫描 + 数据库回表」这条 RAG 路径，
        Caffeine 命中率才有测量意义。
        """
        with _lock:
            _stats["tool_call_responses"] += 1
            call_id = f"call_mock_{_stats['tool_call_responses']}"
        args = json.dumps({"query": query}, ensure_ascii=False)
        # 真实响应里参数是分片下发的：首帧给 id/name，后续帧只带 arguments 片段，最后才定 finish_reason。
        # id 必须每次唯一：AgentScope 的记忆里会残留上一轮的 tool_call，重复 id 会让它一直等不到结果。
        first = {
            "tool_calls": [{"index": 0, "id": call_id, "type": "function",
                            "function": {"name": "recommend_book", "arguments": ""}}],
            "role": "assistant", "content": "", "reasoning_content": "",
        }
        second = {
            "tool_calls": [{"index": 0, "id": "", "type": "function",
                            "function": {"arguments": args[:len(args) // 2]}}],
            "role": "assistant", "content": "", "reasoning_content": "",
        }
        third = {
            "tool_calls": [{"index": 0, "id": "", "type": "function",
                            "function": {"arguments": args[len(args) // 2:]}}],
            "role": "assistant", "content": "", "reasoning_content": "",
        }
        payload = (self._frame(1, first, "null")
                   + self._frame(2, second, "null")
                   + self._frame(3, third, "tool_calls")).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _send_stream(self):
        with _lock:
            _stats["text_responses"] += 1
        text = {"role": "assistant", "content": REPLY, "reasoning_content": ""}
        end = {"role": "assistant", "content": "", "reasoning_content": ""}
        payload = (self._frame(1, text, "null") + self._frame(2, end, "stop")).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        pass


def main():
    global DELAY_MS, EMBED_DELAY_MS, TOOL_CALL
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=18081)
    ap.add_argument("--delay-ms", type=int, default=50)
    ap.add_argument("--embed-delay-ms", type=int, default=180)
    ap.add_argument("--no-tool-call", action="store_true",
                    help="只返回纯文本，不触发工具调用（用于对比 RAG 路径的开销）")
    args = ap.parse_args()
    DELAY_MS = args.delay_ms
    EMBED_DELAY_MS = args.embed_delay_ms
    TOOL_CALL = not args.no_tool_call

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.daemon_threads = True
    # 默认 backlog 只有 5，200 并发会把连接直接拒掉（Connection refused），
    # 应用侧表现为 SSE Retries exhausted → HTTP 500。
    server.request_queue_size = 1024
    print(f"mock 监听 127.0.0.1:{args.port}，对话延迟 {DELAY_MS}ms，"
          f"Embedding 延迟 {EMBED_DELAY_MS}ms", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
