# Behavioral Scenario Scripts (10 ready-to-speak STAR answers)

Each entry below is a short STAR script followed by a 45-second elevator version you can memorize.

---

Scenario 1 — Conflict over technical approach

S: On a sprint we disagreed with a teammate about building a quick workaround vs. a scalable implementation.
T: My task was to keep delivery on schedule while minimizing future technical debt.
A: I listened, documented trade-offs, proposed a phased plan (MVP now, refactor later), and negotiated milestones. I built the core interfaces to make later refactor easy.
R: We delivered the sprint goals and later refactored with minimal rework; the team adopted the phased approach.

45s: During a sprint I disagreed with a teammate on implementing a quick hack vs. scalable design. I proposed a phased plan: deliver an MVP now but design interfaces to allow a later refactor. I negotiated milestones and implemented the minimal reusable core. We met the deadline and later completed a clean refactor with little rework.

---

Scenario 2 — Helping a teammate debug (Your orchestration bug)

S: An orchestration rule marked customer orders "Completed" when a suborder failed; the portal showed wrong status and customers complained before release.
T: As someone familiar with the codebase, I needed to help diagnose and ensure a fix shipped before the deadline.
A: I paired with the teammate, filtered logs by suborder ID to reduce noise, reproduced the issue, traced the event path through the rule engine, proposed a minimal safe fix, reviewed and tested while they implemented it, and coordinated verification and release.
R: The fix shipped before the deadline, customer complaints dropped, and the teammate gained debugging confidence.

45s: Before release, an orchestration rule incorrectly marked orders Completed when suborders failed. I paired with a teammate stuck in noisy logs, filtered by suborder ID, reproduced and traced the event path, and proposed a minimal fix. They implemented while I reviewed and tested; we shipped on time and reduced customer complaints.

---

Scenario 3 — Mistake and recovery

S: I once deployed a change without a full regression run and a customer-facing bug escaped to production.
T: I had to restore service, find the root cause, and prevent recurrence.
A: I took ownership, rolled a hotfix, did a root-cause analysis, added missing tests and CI checks, and communicated transparently to stakeholders.
R: The issue was resolved quickly, we restored customer trust, and the new tests prevented regressions going forward.

45s: I accidentally deployed without complete regression tests and caused a production bug. I owned it, rolled a hotfix, performed root-cause analysis, added tests and CI gates, and informed stakeholders. The bug was resolved and similar regressions were prevented by the new checks.

---

Scenario 4 — Wrong goal discovered late (75% through project)

S: At 75% into a project we discovered the original requirement did not meet the customer need.
T: I needed to re-evaluate scope, minimize wasted effort, and still deliver value.
A: I paused development, validated the new goal with stakeholders, reprioritized the backlog to deliver the correct core feature, and scheduled non-critical work for later.
R: We delivered the correct feature (slightly later), stakeholders were satisfied, and wasted effort was minimized.

45s: When 75% through a project we discovered the goal was incorrect. I validated the new requirement with stakeholders, reprioritized to deliver the core correct feature first, and moved non-essential tasks to a later release. We delivered the right outcome and minimized rework.

---

Scenario 5 — Decision with limited data

S: A production degradation required immediate action but full metrics were not yet available.
T: I had to choose an action that minimized user impact without complete data.
A: I gathered quick signals (error rates, recent deploys), consulted the on-call and product owner, picked a low-risk rollback/patch, and monitored closely.
R: The action stabilized the service and prevented a larger outage; we later completed a full post-mortem.

45s: Faced with a production degradation and limited data, I quickly gathered error-rate signals and consulted stakeholders. I chose a conservative rollback/patch that minimized user impact, monitored results, and later ran a full post-mortem. The service stabilized and outage risk was avoided.

---

Scenario 6 — Invented a small automation/tool

S: Repetitive manual process was causing delays and human errors in our release checks.
T: Reduce toil and improve reliability for release validation.
A: I built a small CLI tool that ran core checks, summarized discrepancies, and integrated it into our release checklist.
R: Release time dropped, human errors decreased, and the tool was adopted across teams.

45s: I automated repetitive release checks by building a small CLI that ran validations and summarized issues. It cut release time and reduced manual errors; the team adopted it as part of the standard checklist.

---

Scenario 7 — Received negative feedback and improved

S: My manager told me my updates to stakeholders were too infrequent and lacked clarity.
T: Improve communication so stakeholders were informed without overhead.
A: I asked for examples, implemented a short weekly status template, and scheduled brief syncs for major blockers.
R: Stakeholders reported better visibility and my manager noted improved communication on the next project.

45s: After receiving feedback about unclear stakeholder updates, I requested specifics, created a concise weekly status template and short syncs for blockers. Stakeholder visibility improved and my manager acknowledged the change.

---

Scenario 8 — Took a risky decision without prior approval (small-scale)

S: A time-sensitive issue threatened a customer workflow and waiting for formal approval would cause major customer impact.
T: Make a quick judgment call that minimized customer risk while keeping leadership informed.
A: I implemented a small, reversible change with strong tests, documented the decision and the risk, and informed my manager immediately after.
R: The action prevented impact; leadership accepted the approach and we later formalized the guardrail.

45s: Faced with imminent customer impact, I made a small reversible change with tests, documented the decision and risks, and informed my manager after the fact. The change prevented the issue and leadership accepted the decision with follow-up guardrails.

---

Scenario 9 — Mentoring someone who grew

S: A junior engineer struggled with code quality and PR review feedback.
T: Help them lift their skills and become more independent.
A: I scheduled weekly pairing sessions, reviewed PRs with teachable comments, and gave small focused exercises.
R: Within months they improved code quality, reduced review cycles, and took on larger features.

45s: I mentored a junior engineer by pairing weekly, giving focused PR feedback, and assigning small exercises. Over months their code quality and confidence improved, and they started delivering larger features independently.

---

Scenario 10 — Pushed an unpopular but better idea

S: The team favored a quick fix to meet a deadline, but I believed the architectural change would save major effort later.
T: Convince stakeholders to accept a slightly longer timeline for a higher-quality solution.
A: I gathered cost estimates, showed trade-offs with examples, proposed a phased rollout that delivered immediate value while enabling the architecture change, and addressed concerns.
R: The team agreed to the phased approach; long-term maintenance costs dropped and the system scaled better.

45s: When the team preferred a quick fix I advocated for a phased architectural change by presenting trade-offs and a plan to deliver value now while enabling the long-term design. We adopted the phased plan, which reduced future maintenance and improved scalability.

---

If you want, I can now:
- convert each 45s script into a 30–45s punchy elevator version,
- create audio scripts for practice,
- export individual scenario files for spaced repetition flashcards.
