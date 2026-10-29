# Scenario 2 — Conflict & Root-Cause Load Test (Poker game)

Scenario summary

As SDE3 on a poker game, players who joined during late registration couldn’t leave the table under heavy production traffic. The issue did not reproduce locally; a teammate and the architect disagreed about root cause vs. workaround. You proposed adding targeted logs and running prod-like load tests. The tests reproduced the issue, logs revealed a race condition in a multi-threaded section, you implemented a synchronized check, validated under load, and shipped a permanent fix. Support tickets decreased.

Full STAR answer

S (Situation): In production, late-registrant players couldn’t leave the table; it happened frequently under heavy load but never reproduced locally. A teammate disagreed with my diagnosis and the architect pushed for a quick workaround.

T (Task): Prove the root cause, enable a correct fix, minimize player impact and reduce support load.

A (Action): Suggested and implemented targeted logging in the suspected section, set up multiple prod‑like load tests, and kept a short safe workaround in place. The load tests reproduced the failure; logs pointed to a race condition caused by concurrent threads. I implemented a synchronized check, validated it in repeated load runs, and coordinated rollout.

R (Result): The permanent fix restored correct player behavior (they could leave the table), we reduced support tickets, and the team accepted the data-driven approach.

Questions from skill.md this scenario covers

- Tell me about a situation where you had a conflict with someone on your team. What was it about? What did you do? How did they react? What was the outcome?
- Tell me about a time when your teammate didn't agree with you? What did you do?
- Tell me about a time when you pushed back a decision from your management for better long-term benefits.
- Tell me about a time when you faced an obstacle and how you overcame it. (noisy logs / non-reproducible bug)
- Tell me about a time when you took an important decision without much data. (decided to run load tests / add logs to gather data)
- Tell me about a time when you helped someone in their work. (guided diagnosis and testing)
- Have you ever gone out of your way to help a peer? (ownership: set up tests, added logs, coordinated)

45‑second elevator version (memorize)

“In production, late-registrant players couldn’t leave the table under heavy traffic and it didn’t reproduce locally. The architect favored a quick workaround while a teammate disagreed on the root cause. I added targeted logs and ran prod-like load tests; we reproduced the issue and the logs pointed to a race condition from concurrent threads. I implemented a synchronized check, validated it under load, and rolled it out. Player behavior was restored and support tickets dropped. The team learned to use data (logs + load tests) to resolve technical disagreements.”

Follow-up prompts to rehearse (short answers)

- What specific log fields/IDs did you add? — e.g., suborder/player session ID, correlation IDs, thread IDs, timestamps.
- How long did the load testing take and what tooling did you use? — mention tools and number of iterations if known.
- How did you coordinate with the architect and your teammate? — show communication and compromise (safe workaround while proving root cause).
- What would you do differently next time? — suggest automated load tests, better logging in staging, or runtime assertions.

File: /Users/test/IdeaProjects/Walmart/scenario2_covered_questions.md
