# Conflict with a Senior Regarding a Work Task

## Interview question

Tell me about a time when you had a conflict with a senior regarding a work task.

Include:

- What was the situation?
- What was the conflict about?
- How was the situation resolved?
- Who ultimately prevailed?
- What did you learn from the experience?

## STAR answer

### Situation

While working as an SDE3 on a poker platform, we encountered a production issue where players who joined a table during late registration could not leave after losing all their chips. The issue occurred frequently under heavy production traffic, but we could not reproduce it locally.

### Task and conflict

I suspected a concurrency issue in a particular section of the code because multiple threads were executing it simultaneously. However, the available logs were insufficient to prove my hypothesis.

A senior team member preferred implementing a workaround immediately, while I believed we also needed to identify and fix the root cause to prevent the issue from recurring.

### Action and resolution

Instead of continuing the disagreement based on assumptions, I proposed a balanced approach:

1. Implement a safe temporary workaround to reduce the immediate customer impact.
2. Add targeted logging around the suspected code path.
3. create a load-test environment with production-like configuration and traffic.

After several load-test runs, we reproduced the issue. The additional logs confirmed that concurrent execution in the suspected code path was causing the problem. We then implemented a permanent fix and validated it through repeated load testing before deployment.

### Result and who prevailed

My root-cause hypothesis was ultimately validated, but I would not describe the outcome as one person winning.

The senior team member's workaround helped manage the immediate customer impact, while my logging and load-testing approach allowed us to identify and permanently fix the problem. Players could leave the table correctly, and the number of related support tickets decreased.

### Learning

I learned that technical disagreements should be resolved with evidence rather than seniority or opinion. I also learned to recognize the value in the other person's proposal and combine short-term risk mitigation with long-term root-cause analysis.

This approach allowed us to preserve a respectful working relationship while achieving the best technical and customer outcome.

## 60-second version

While working as an SDE3 on a poker platform, we had a production issue where late-registration players could not leave a table after losing their chips. It occurred under heavy traffic but was not reproducible locally. I suspected a concurrency issue, while a senior team member preferred implementing a workaround because the logs did not prove my hypothesis.

I proposed that we use the workaround to reduce immediate customer impact while also adding targeted logs and running production-like load tests. After several test runs, we reproduced the issue, and the logs confirmed the concurrency problem. We implemented and validated a permanent fix, and related support tickets decreased.

My hypothesis was validated, but both approaches contributed to the outcome. I learned to resolve technical disagreements with evidence and balance immediate mitigation with long-term corrective action.
