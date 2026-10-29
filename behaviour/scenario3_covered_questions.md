# Scenario 3 — Mistake & Recovery

Scenario summary

While debugging an order issue, I misread a field ID and followed the wrong logs. I investigated another issue but couldn't find the root cause of the actual problem. Later I realized I had been looking at the wrong logs. I immediately informed my manager. The bug was not severe and did not impact customers.

Full STAR answer

S (Situation): While debugging an order-processing issue in production, I misread a field ID and traced the wrong log stream, which led me to a different problem rather than the root cause.

T (Task): My responsibility was to find the real root cause quickly and ensure we didn’t miss a customer-impacting issue.

A (Action): After confirming I’d been following the wrong logs, I immediately informed my manager, corrected my log filters to the proper field ID, retraced the request path, and identified the real root cause. I documented the mistake and added a quick checklist to avoid similar log-misreads in future debugging sessions.

R (Result): We identified the actual issue and fixed it; there was no customer impact. My transparency avoided wasted effort, and the checklist reduced confusion for the team during future incidents.

45‑second elevator version

“While debugging an order issue, I mistakenly followed the wrong log field because I misread a field ID, which led me to investigate an unrelated problem. I told my manager right away, fixed my filters, retraced the request path, and found the real root cause. The bug wasn’t customer-facing, but I documented the error and created a small debugging checklist to prevent similar mistakes. The team appreciated the transparency and we reduced investigation time in later incidents.”

Questions from skill.md this scenario covers

- Tell me about a time you committed a mistake.
- Have you ever failed at something? What did you learn from it? (learning focus)
- Tell me about a time when you failed to meet your commitment. (if you didn’t meet a deadline because of this, add details)
- Describe a time you received negative feedback from your manager and how you responded. (if manager gave feedback on process)
- Tell me about a time when you did not know something and how you tackled it. (you corrected log filters and retraced the path)

Follow-up prompts to rehearse (short answers)

- What exactly did you misread (field name/ID)?
- How long did it take to discover the mistake?
- What did you tell your manager and how did they react?
- What is in the debugging checklist you created?
- What would you do differently next time?

File: /Users/test/IdeaProjects/Walmart/scenario3_covered_questions.md
