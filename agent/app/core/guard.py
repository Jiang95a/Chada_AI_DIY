"""AI 安全护栏：注入防护、输出消毒、限流、日志脱敏。

为什么单独一个模块：评审和答辩都会问「模型被带偏了怎么办」。
答案不能是"我们的模型很聪明"，只能是"我们在外面套了一层能说清楚的规则"。

四件事，每一件都独立可测：
1. sanitize_system_extra —— 客户端传来的附加要求是**可控输入**，必须先过一遍
2. screen_input          —— 检测提示词攻击和 PII，PII 只脱敏、不拦截（用户填手机号是正常需求）
3. screen_output         —— 剥掉 HTML/脚本痕迹，限制长度
4. RateLimiter           —— 单 IP 令牌桶，防刷

所有函数都返回「清洗后的结果 + 命中的规则名」，因为可解释比干净更重要：
出问题时要能说清是哪条规则拦的。
"""

from __future__ import annotations

import re
import threading
import time
from dataclasses import dataclass, field

from app.core.config import get_settings

# ---------------------------------------------------------------------------
# 规则表：全部小写匹配。加规则只改这里，别散到业务代码里。
# ---------------------------------------------------------------------------

# 提示词注入：想改写人设、想套出系统提示词、想切开发者模式
_INJECTION_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("ignore_rules", re.compile(r"(忽略|无视|忘记|绕过|别管)[^\n]{0,12}(之前|以上|上面|前面|所有|一切|原)[^\n]{0,6}(规则|要求|指令|设定|限制|提示)")),
    ("ignore_rules_en", re.compile(r"ignore\s+(all\s+)?(the\s+)?(previous|above|prior|earlier)\s+(instructions?|rules?|prompts?)")),
    ("you_are_now", re.compile(r"你(现在|从现在起|接下来)?(就)?是(一个|一名|个)?[^\n]{0,20}(管理员|开发者|root|DAN|越狱|无限制)")),
    ("reveal_prompt", re.compile(r"(输出|打印|显示|重复|告诉我|复述)[^\n]{0,10}(你的|系统)[^\n]{0,6}(提示词|prompt|设定|规则|指令)")),
    ("reveal_prompt_en", re.compile(r"(print|show|repeat|reveal|tell me)[^\n]{0,20}(your\s+)?(system\s+prompt|instructions)")),
    ("role_prefix", re.compile(r"^\s*(system|assistant|developer|tool)\s*[:：]", re.IGNORECASE | re.MULTILINE)),
    ("jailbreak", re.compile(r"(开发者模式|jailbreak|DAN\s*模式|越狱模式|无任何限制|不受任何限制)")),
    ("api_key_probe", re.compile(r"(api[\s_-]?key|密钥|秘钥|token)[^\n]{0,8}(是|多少|给我|返回|输出)")),
]

# 输出侧：绝不允许模型往页面里塞可执行内容
_HTML_DANGER: list[tuple[str, re.Pattern[str]]] = [
    ("script_tag", re.compile(r"<\s*/?\s*script[^>]*>", re.IGNORECASE)),
    ("js_url", re.compile(r"javascript\s*:", re.IGNORECASE)),
    ("event_attr", re.compile(r"\son[a-z]+\s*=\s*[\"']", re.IGNORECASE)),
    ("iframe_tag", re.compile(r"<\s*/?\s*iframe[^>]*>", re.IGNORECASE)),
    ("style_url", re.compile(r"expression\s*\(", re.IGNORECASE)),
]

# PII：只用于日志脱敏。不拦截——报名表本来就要收手机号。
_PII_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("id_card", re.compile(r"\b\d{17}[\dXx]\b")),
    ("phone_cn", re.compile(r"\b1[3-9]\d{9}\b")),
    ("bank_card", re.compile(r"\b\d{16,19}\b")),
]

_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")

# 系统提示词里出现这些串，说明模型把自己的规则抄出来了
_LEAK_MARKERS = ("# 你的职责", "# 规则", "调用工具前，先用一句话说明")


@dataclass
class GuardResult:
    """护栏的输出：清洗后的文本 + 命中了哪些规则。"""

    text: str
    flags: list[str] = field(default_factory=list)

    @property
    def blocked(self) -> bool:
        return bool(self.flags)

    @property
    def flag_line(self) -> str:
        return ",".join(self.flags)


# ---------------------------------------------------------------------------
# 1. system_extra
# ---------------------------------------------------------------------------
def sanitize_system_extra(raw: str | None) -> GuardResult:
    """客户端可控的「本次附加要求」必须过这一关。

    命中注入规则时**整段丢弃**，不做局部消毒——半截被改写的指令比完全没有更危险，
    而且 system_extra 本来就只是锦上添花，丢了不影响主流程。
    """
    settings = get_settings()
    if not raw:
        return GuardResult(text="")

    cleanned = _CONTROL_CHARS.sub("", raw).replace("\r\n", "\n").strip()
    flags: list[str] = []

    limit = max(50, settings.system_extra_max_chars)
    if len(cleanned) > limit:
        cleanned = cleanned[:limit]
        flags.append("system_extra_truncated")

    for name, pattern in _INJECTION_PATTERNS:
        if pattern.search(cleanned):
            return GuardResult(text="", flags=["system_extra_blocked", name])

    return GuardResult(text=cleanned, flags=flags)


# ---------------------------------------------------------------------------
# 2. 用户输入
# ---------------------------------------------------------------------------
def screen_input(text: str) -> GuardResult:
    """用户正文：只标记、不改写（改写了用户会觉得「我打的字没了」）。"""
    flags = [name for name, pattern in _INJECTION_PATTERNS if pattern.search(text)]
    if len(text) > 8000:
        flags.append("input_too_long")
    return GuardResult(text=text, flags=flags)


def mask_pii(text: str) -> str:
    """日志脱敏：手机号留后四位，身份证只留前二后二。"""
    out = text
    out = _PII_PATTERNS[0][1].sub(lambda m: f"{m.group()[:2]}****{m.group()[-2:]}", out)
    out = _PII_PATTERNS[1][1].sub(lambda m: f"{m.group()[:3]}****{m.group()[-4:]}", out)
    out = _PII_PATTERNS[2][1].sub(lambda m: f"****{m.group()[-4:]}", out)
    return out


# ---------------------------------------------------------------------------
# 2.5 危机关怀：优先级**高于**其余所有规则
# ---------------------------------------------------------------------------
# 先说清楚这里不做什么：不判断"他是不是抑郁"，不评估严重程度，不追问细节。
# 那是专业人员的活，我们没有资格，也不该有资格。
#
# 这里只做一件很窄的事：**这句话里有没有风险的形状**。
# 有，就立刻停下生成，把人交给真人。
#
# 命中率设计上刻意偏向"宁可多报"：多给一次资源，代价只是多说一句用不上的话；
# 漏掉的代价，不是这个项目能承担的。
_CRISIS_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    # 明确的自我伤害意图
    (
        "crisis_self_harm",
        re.compile(
            r"(不想活|活不下去|活够了|活着[^\n]{0,8}(没|没有)[^\n]{0,6}(意思|意义)|"
            r"想死|想去死|不如死了|死了算了|结束(自己的)?生命|了结自己)"
        ),
    ),
    # 具体方式：出现即按高风险处理，不去分辨「只是随口说说」
    (
        "crisis_method",
        re.compile(r"(割腕|割(自己|胳膊|手)|划(自己|胳膊)|跳楼|跳下去|上吊|吞药|烧炭|自残|自伤)"),
    ),
    # 绝望 / 消失愿望
    (
        "crisis_hopeless",
        re.compile(r"(撑不下去|熬不下去|挺不下去|没人(会)?在乎|想消失|消失就好了|没有出口)"),
    ),
]

# 这些说法里命中的词是别的意思，先剔掉再匹配——少一次误报就少一次吓人
_CRISIS_EXCLUSIONS: tuple[re.Pattern[str], ...] = (
    re.compile(r"想死你"),
    re.compile(r"跳楼机"),
    re.compile(r"(笑|累|热|饿|困|气|烦|忙|爽|美|甜|香)死(我)?了"),
)


def screen_crisis(text: str) -> list[str]:
    """返回命中的风险类别；空列表 = 没看出风险。

    只做识别，不看开关——开关在 ``is_crisis``，测试要能单独验证规则本身。
    """
    if not text or not text.strip():
        return []
    probe = text
    for pattern in _CRISIS_EXCLUSIONS:
        probe = pattern.sub("", probe)
    return [name for name, pattern in _CRISIS_PATTERNS if pattern.search(probe)]


def is_crisis(text: str) -> bool:
    """业务侧开关：``CRISIS_GUARD_ENABLED=false`` 时可以整体关掉（不建议）。"""
    if not get_settings().crisis_guard_enabled:
        return False
    return bool(screen_crisis(text))


def crisis_reply() -> str:
    """危机时的**固定文案**，不交给模型写。

    为什么不让模型写：这句话每次都必须一样、必须都对。
    模型大概率会补一句「会好起来的」——而我们保证不了，那正是最不该说的话。

    文案里的号码来自配置，各校上线前自己换（见 .env.example 的 CRISIS_*）。
    """
    settings = get_settings()
    return (
        "先停一下。\n\n"
        "你刚才说的这些，比做一个活动页面重要得多。这个页面我先不往下做了。\n\n"
        "如果你现在很难受，可以先做一件具体的事：\n"
        f"· 联系{settings.crisis_center_hint}\n"
        f"· 或者拨打{settings.crisis_hotline_name} {settings.crisis_hotline}\n"
        f"· 如果已经危及到人身安全，请立刻拨 {settings.crisis_emergency}\n\n"
        "也可以把刚才这段话，再说给你信任的一个人听——老师、家人、朋友，谁都行。\n\n"
        "页面可以以后再建。你先照顾好自己。"
    )


# ---------------------------------------------------------------------------
# 3. 模型输出
# ---------------------------------------------------------------------------
def screen_output(text: str, max_chars: int = 8000) -> GuardResult:
    """输出消毒：剥可执行标签 + 截断 + 检查有没有把系统提示词抄出来。"""
    if not text:
        return GuardResult(text="")

    flags: list[str] = []
    cleaned = text

    for name, pattern in _HTML_DANGER:
        if pattern.search(cleaned):
            cleaned = pattern.sub("", cleaned)
            flags.append(f"output_{name}")

    if any(marker in cleaned for marker in _LEAK_MARKERS):
        flags.append("output_prompt_leak")

    if len(cleaned) > max_chars:
        cleaned = cleaned[:max_chars] + "\n\n（回复过长已截断）"
        flags.append("output_truncated")

    return GuardResult(text=cleaned.strip(), flags=flags)


# ---------------------------------------------------------------------------
# 4. 限流
# ---------------------------------------------------------------------------
class RateLimiter:
    """进程内令牌桶。单实例够用；多实例部署要换成 Redis，注释里写清楚别装。

    为什么不用中间件：只有真正花钱的接口需要限流（/chat、/poster/extract），
    /health 这种要让它随便探活。
    """

    def __init__(self, per_minute: int | None = None) -> None:
        self._per_minute = per_minute
        self._hits: dict[str, list[float]] = {}
        self._lock = threading.Lock()

    @property
    def limit(self) -> int:
        if self._per_minute is not None:
            return self._per_minute
        return max(1, get_settings().rate_limit_per_minute)

    def check(self, key: str) -> tuple[bool, int]:
        """返回 (是否放行, 建议等待秒数)。key 用客户端 IP。"""
        limit = self.limit
        now = time.monotonic()
        window = 60.0

        with self._lock:
            bucket = self._hits.setdefault(key, [])
            # 丢掉窗口外的记录
            bucket[:] = [ts for ts in bucket if now - ts < window]
            if len(bucket) >= limit:
                wait = int(window - (now - bucket[0])) + 1
                return False, max(1, wait)
            bucket.append(now)

            # 顺手清理长期不活跃的 key，防止内存慢慢涨
            if len(self._hits) > 2000:
                for stale in [k for k, v in self._hits.items() if not v][:500]:
                    self._hits.pop(stale, None)
            return True, 0

    def reset(self) -> None:
        with self._lock:
            self._hits.clear()


_global_limiter = RateLimiter()


def get_rate_limiter() -> RateLimiter:
    return _global_limiter
