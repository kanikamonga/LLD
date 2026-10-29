# Scenario 1 — Covered Questions & STAR Answers

Scenario summary

Paired with a teammate to debug an orchestration rule bug: when a suborder failed the rule still marked the overall customer order as "Completed", showing the wrong status on the customer portal. The bug was critical, customer-facing, and there was a tight deadline. I noticed a teammate stuck in noisy logs, paired with them, reproduced the issue, traced the event path in the rule engine, proposed and tested a fix, and we shipped before the deadline. Customer complaints decreased and the teammate gained confidence.

Full STAR answer (ready to speak)

S (Situation): During release, an orchestration rule caused customer orders to show "Completed" when a suborder had failed, creating incorrect portal status and customer complaints.

T (Task): My goal was to help diagnose and fix the bug before the deadline and upskill a teammate who was stuck in the logs.

A (Action): I paired with the teammate, filtered logs using the failing suborder ID to reduce noise, reproduced the issue, traced the event path through the rule engine, proposed a minimal safe fix, reviewed and tested the patch while they implemented it, and coordinated a quick verification and release.

R (Result): We shipped the fix before the deadline, customer complaints dropped, and the teammate learned focused debugging techniques and gained confidence.

---

Questions from skill.md that this scenario covers, with specific STAR-mapped answers / guidance

1) Give an example of when you saw a peer struggling and decided to step in and help.
- S: Briefly describe the orchestration bug and the teammate being stuck in logs.
- T: Explain your role (familiar with the codebase) and responsibility (get the fix shipped).
- A: Say you paired, taught log-filtering by suborder ID, broke work into reproduce/trace/fix/test, and reviewed their code.
- R: Delivered before deadline; complaints reduced; teammate gained confidence.

2) Tell me about a time when you helped one of your teammates / had to help a team member struggling with a task.
- Use the same STAR; emphasize mentoring actions (showing the exact log commands, checkpoints, and small test cases).

3) Have you ever gone out of your way to help a peer? (ownership)
- Emphasize voluntary pairing, taking extra time to reproduce and test, and coordinating release verification.

4) Tell me about a time when you were working on a project under a time constraint / handled a tight deadline.
- Focus STAR on timeline: how much time remained, how you prioritized reproduce->trace->fix, and how you de-scoped non-essential checks to meet the deadline while ensuring safety.

5) Tell me about a time when you needed help from somebody. (reverse framing — you helped instead)
- In the 'T' and 'A' points note the teammate needed help; show your facilitation and knowledge transfer.

6) Tell me about a time when you helped someone grow in their career and it benefited them.
- In 'A', describe the coaching moment: show a specific technique you taught (log filtering by suborder ID, hypothesis testing) and in 'R' note the confidence and ability to handle similar bugs later.

7) Tell me about a time when you faced an obstacle and how you overcame it. (debugging noisy logs)
- Describe the obstacle (noisy logs, hard to trace), and actions (filter logs, reproduce locally or in staging, trace event path) and the result.

8) Tell me about a time when you helped someone in their work.
- Same STAR; call out concrete artefacts you produced (debug checklist, filtered log queries, test case) that they used.

How to phrase answers quickly (bullet flashcard style)

- One-sentence opener (Situation): "During release, a rule marked orders Completed when suborders failed, causing customer complaints."
- One-sentence responsibility (Task): "I needed to help debug and ship a fix before deadline and support a teammate stuck in noisy logs."
- Two-sentence action (Action): "I paired with them, filtered logs by suborder ID, reproduced the issue, traced the rule engine path, proposed a minimal fix, and reviewed & tested their patch."
- One-sentence result (Result): "We shipped before the deadline, complaints dropped, and the teammate became more confident."

Interviewer follow-ups you should prepare (short answers)

- What exactly was the bug? — "A rule continued to mark the order Completed even when a suborder failed; missing conditional check in event handling."
- How much time was left? — give exact hours/days if possible.
- Which logs/commands did you use? — name the log system and the filters (e.g., suborder ID, correlation ID).
- What steps did you break the task into? — reproduce, trace event, identify faulty condition, implement minimal safe fix, test, deploy.
- What would you do differently? — suggest automated test, guardrails, or monitoring to catch similar issues earlier.

File note: keep this story crisp and practice it as a 45–60 second STAR answer. Use specific numbers where you can (time saved, reduction in complaints, time left before deadline) to strengthen impact.
