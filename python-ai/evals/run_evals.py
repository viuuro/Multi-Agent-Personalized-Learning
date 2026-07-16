"""无需 API Key 的智能路由与局部计划保护回归测试。"""

from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from intelligence import (  # noqa: E402
    deterministic_quality_check,
    merge_plan_revision,
    rule_based_turn_analysis,
)


def run() -> None:
    cases = json.loads((Path(__file__).parent / "cases.json").read_text(encoding="utf-8"))
    failures: list[str] = []
    for index, case in enumerate(cases, start=1):
        result = rule_based_turn_analysis(case["message"], case.get("profile", {}))
        checks = {
            "intent": result["intent"],
            "action": result["plan_action"],
            "clarify": result["needs_clarification"],
        }
        for key, actual in checks.items():
            if actual != case[key]:
                failures.append(f"case {index} {key}: expected={case[key]!r}, actual={actual!r}")
        if "weeks" in case and result["revision_scope"].get("weeks") != case["weeks"]:
            failures.append(
                f"case {index} weeks: expected={case['weeks']!r}, actual={result['revision_scope'].get('weeks')!r}"
            )

    existing = {"weeks": [
        {"weekNumber": 1, "topic": "old-1", "tasks": ["a"]},
        {"weekNumber": 2, "topic": "old-2", "tasks": ["b"]},
        {"weekNumber": 3, "topic": "old-3", "tasks": ["c"]},
        {"weekNumber": 4, "topic": "old-4", "tasks": ["d"]},
    ]}
    proposed = [{"weekNumber": n, "topic": f"new-{n}", "tasks": ["x"]} for n in range(1, 5)]
    merged = merge_plan_revision(existing, proposed, "modify_week", {"weeks": [3]})
    topics = [week["topic"] for week in merged]
    if topics != ["old-1", "old-2", "new-3", "old-4"]:
        failures.append(f"partial plan merge changed protected weeks: {topics!r}")

    pending = rule_based_turn_analysis(
        "调整一下计划难度", {"interestAreas": ["Java"]})
    resumed = rule_based_turn_analysis(
        "提高一些", {"interestAreas": ["Java"]}, pending["temporary_state"])
    if resumed["plan_action"] != "adjust_difficulty" or resumed["needs_clarification"]:
        failures.append(f"pending clarification action was not resumed: {resumed!r}")

    quality = deterministic_quality_check(
        "你好！我已经分析了你的学习情况。根据我们的对话，我了解到你的学习目标。",
        ["你好！我已经分析了你的学习情况。根据我们的对话，我了解到你的学习目标。"],
    )
    if not quality["needs_revision"]:
        failures.append("quality checker did not reject repetitive template response")

    if failures:
        raise AssertionError("\n".join(failures))
    print(f"PASS: {len(cases)} routing cases + partial-plan + quality checks")


if __name__ == "__main__":
    run()
