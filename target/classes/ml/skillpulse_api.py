import json
import math
import sys
from datetime import date


def avg(values):
    return sum(values) / len(values) if values else 0.0


def trend(values):
    if len(values) < 2:
        return 0.0
    n = len(values)
    sx = sum(range(n))
    sy = sum(values)
    sxy = sum(i * values[i] for i in range(n))
    sxx = sum(i * i for i in range(n))
    denom = max(1.0, n * sxx - sx * sx)
    return (n * sxy - sx * sy) / denom


def days_since_last(timestamps):
    if not timestamps:
        return 0
    try:
        return max(0, (date.today() - date.fromisoformat(timestamps[-1])).days)
    except Exception:
        return 0


def clamp(value, lo, hi):
    return max(lo, min(hi, value))


def analyze(payload):
    accuracies = payload.get("accuracies") or [0.90, 0.82, 0.75, 0.63, 0.55]
    difficulties = payload.get("difficulties") or [2.0]
    recent = avg(accuracies[-5:])
    overall = avg(accuracies)
    t = trend(accuracies)
    gap = days_since_last(payload.get("timestamps") or [])

    half_life = clamp(math.exp(0.4 * recent + 0.3 * max(0, t) - 0.15 * max(1.0, avg(difficulties))) * 12.0, 0.5, 90.0)
    recall = clamp(2 ** (-gap / half_life), 0.0, 1.0)

    if recall < 0.50 or (recent < 0.60 and t < -0.03):
        status = "DECAYING"
        confidence = clamp(0.70 + abs(t) + (0.60 - recent), 0.70, 0.97)
        priority = "HIGH"
        actions = ["Schedule immediate refresher practice."]
        if gap > 14:
            actions.append("Reduce the long practice gap with daily micro-sessions.")
        if recent < 0.60:
            actions.append("Restart with easier tasks before increasing difficulty.")
    elif t > 0.04 and recent > 0.75:
        status = "IMPROVING"
        confidence = clamp(0.68 + t + recent / 10.0, 0.65, 0.95)
        priority = "LOW"
        actions = ["Continue the current practice pattern.", "Increase difficulty slightly to lock in mastery."]
    else:
        status = "STABLE"
        confidence = clamp(0.62 + overall / 5.0, 0.60, 0.90)
        priority = "MEDIUM" if gap > 7 or recent < 0.70 else "LOW"
        actions = ["Maintain light review practice this week."]

    return {
        "skillName": payload.get("skillName") or "Skill",
        "status": status,
        "confidence": round(confidence, 3),
        "recallProbability": round(recall, 3),
        "priority": priority,
        "actions": actions,
        "explanation": f"Recent accuracy is {round(recent * 100)}%, overall accuracy is {round(overall * 100)}%, trend is {round(t, 3)}, and last practice was {gap} day(s) ago.",
        "engine": "python"
    }


if __name__ == "__main__":
    request = json.load(sys.stdin)
    print(json.dumps(analyze(request)))
