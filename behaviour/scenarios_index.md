# Scenarios Index — mapping scenarios to covered questions

This index maps the prepared scenarios (user-specific + scripted) to the exact behavioral questions from skill.md they cover. It also checks remaining questions that still need explicit scenarios or separate preparation.

Files referenced:
- scenario_scripts.md (10 scripted scenarios)
- scenario1_covered_questions.md (orchestration debug/help scenario)
- scenario2_covered_questions.md (poker game race condition / conflict & load test)

---

Overview: the 10 scripted scenarios + the 2 user-specific scenario files cover the bulk of questions in skill.md. Below each scenario lists the questions it directly supports (verbatim from skill.md). Where multiple questions are equivalent, a single scenario is listed once.

Scenario A — Orchestration debug & helping peer (file: scenario1_covered_questions.md)
Covers:
- Give an example of when you saw a peer struggling and decided to step in and help. What was the situation and what actions did you take? What was the outcome?
- Tell me about a time when you helped one of your teammates.
- Tell me about a time when you had to help a team member struggling with a task.
- Have you ever gone out of your way to help a peer? (ownership)
- Tell me about a time when you were working on a project under a time constraint.
- Tell me about a time when you handled a tight deadline.
- Tell me about a time when you needed help from somebody. (reverse framing: you helped instead)
- Tell me about a time when you helped someone in their work.
- Tell me about a time when you helped someone grow in their career and it benefited them. (teammate gained confidence)
- Tell me about a time when you faced an obstacle and how you overcame it. (debugging noisy logs)

Scenario B — Poker game: non-reproducible bug, logs & load tests (file: scenario2_covered_questions.md)
Covers:
- Tell me about a situation where you had a conflict with someone on your team. What was it about? What did you do? How did they react? What was the outcome?
- Tell me about a time when your teammate didn't agree with you? What did you do?
- Tell me about a time when you pushed back a decision from your management for better long-term benefits.
- Tell me about a time when you faced an obstacle and how you overcame it. (noisy logs / non-reproducible bug)
- Tell me about a time when you took an important decision without any data. (decided to run load tests / add logs to gather data)
- Tell me about a time when you helped someone in their work. (guided diagnosis and testing)
- Have you ever gone out of your way to help a peer? (ownership)

Scenario 3 — Conflict over technical approach (scenario_scripts.md: Scenario 1)
Covers:
- Tell me about a situation where you had a conflict with someone on your team.
- Tell me about a time when your teammate didn't agree with you.
- Tell me about a time when your team members were not supporting something but you pushed and went for a more optimal solution.
- Tell me about a time when you thought of an unpopular idea.
- Tell me about a time when you had conflicting ideas with your teammates and how you resolved them.

Scenario 4 — Helping a teammate debug (scripted; matched your orchestration story) (scenario_scripts.md: Scenario 2)
Covers (overlaps with Scenario A):
- Give an example of when you saw a peer struggling and decided to step in and help.
- Tell me about a time when you helped one of your teammates.
- Tell me about a time when you had to help a team member struggling with a task.
- Tell me about a time when you were working on a project under a time constraint.
- Tell me about a time when you handled a tight deadline.

Scenario 5 — Mistake and recovery (scenario_scripts.md: Scenario 3)
Covers:
- Tell me about a time you committed a mistake.
- Have you ever failed at something? What did you learn from it?
- Tell me about a time when you failed to meet your commitment.
- Tell me about a time when you failed to meet your deadlines for a project.

Scenario 6 — Wrong goal discovered late (scenario_scripts.md: Scenario 4)
Covers:
- Tell me about a time when you were 75% through a project and realized you had the wrong goal.
- Tell me about a time when you faced a setback initially but still achieved the goal.
- Tell me about a time when you had to cut corners to meet a deadline. (if you did)

Scenario 7 — Decision with limited data (scenario_scripts.md: Scenario 5)
Covers:
- Tell me about a time when you took an important decision without any data.
- Tell me about a time when you did not have enough data and had to use judgement to make a decision.
- Tell me about a time when you had to make a decision based on limited information and how it impacted the outcome.
- Have you ever been in a situation where you had to make a choice among a few options, but did not have a lot of time to explore each option?

Scenario 8 — Invented a small automation/tool (scenario_scripts.md: Scenario 6)
Covers:
- Tell me about a time when you invented something.
- Tell me about a time when you innovated and exceeded expectations.
- Tell me about a time when you went above and beyond your job responsibilities.

Scenario 9 — Received negative feedback and improved (scenario_scripts.md: Scenario 7)
Covers:
- Describe a time you received negative feedback from your manager and how you responded.
- Tell me about a time when you learned new technologies. (if feedback related to skill gap)
- Tell me about a time when you did not know something and how you tackled it.

Scenario 10 — Took a risky decision without manager approval (scenario_scripts.md: Scenario 8)
Covers:
- Have you ever had to make a tough decision without consulting anybody?
- Tell me about a time when you took a decision on your own without the manager's prior approval.
- Tell me about a time when you pushed back a decision from your management for better long-term benefits. (if applicable)

Scenario 11 — Mentoring someone who grew (scenario_scripts.md: Scenario 9)
Covers:
- Tell me about a time when you helped someone grow in their career and it benefited them.
- Tell me about a time when you helped someone grow but it did not benefit you.
- Tell me about a time when you helped someone in their work.

Scenario 12 — Pushed unpopular but better idea (scenario_scripts.md: Scenario 10)
Covers:
- Tell me about a time when you thought of an unpopular idea.
- Tell me about a time when you pushed back a decision from your management for better long-term benefits.
- Tell me about a time when you had conflicting ideas with your teammates and how you resolved them.

---

Coverage check vs full skill.md question list

- Fully covered by the scenarios above:
  - Conflict / disagreement variants (multiple scenarios)
  - Helping peers / mentoring / ownership (scenarios A,4,11)
  - Mistake / failure / learning (scenario 5)
  - Deadlines / tight time constraints / pivots (A,4,6)
  - Decision-making with limited data / fast choices (7,10)
  - Innovation / automation (8)
  - Feedback / receiving negative feedback (9)
  - Pushback / long-term benefits (2,10,12)
  - Wrong-goal / 75% changed (6)
  - Mentoring and growth (11)

- Questions that are NOT scenario-style and should be prepared separately (these remain uncovered by STAR stories and require short prepared answers):
  - Tell me about yourself. (prepare a 30–60s elevator pitch)
  - Tell me about a project you're working on. (project summary + role + impact)
  - Tell me about your past projects and the internship experience you are most proud of.

- Minor variants / duplicates: many repeated or rephrased questions are covered by the same story. Use the mapping above to reuse the nearest scenario and tailor the opening sentence for the exact ask.

Action items / recommendations

1. Create short elevator scripts for the three non-scenario items (Tell me about yourself / current project / proud internship).
2. If you want stricter 1:1 coverage, add 2–3 extra scenarios that specifically address:
   - A clear example of 'I missed a deadline' (if your mistake story avoided missed deadlines)
   - A case where you 'cut corners to meet a deadline' (if that happened and you can speak to trade-offs)
3. Export each scenario file into flashcard-ready formats (one file per scenario) for spaced practice.

Files created/used
- /Users/test/IdeaProjects/Walmart/scenario_scripts.md
- /Users/test/IdeaProjects/Walmart/scenario1_covered_questions.md
- /Users/test/IdeaProjects/Walmart/scenario2_covered_questions.md
- /Users/test/IdeaProjects/Walmart/scenarios_index.md (this file)

If you want, next steps:
- I can generate the three non-scenario elevator scripts now (recommended).
- Or convert each scenario into 30–45s punchy flashcards/audio prompts for practice.
- Or add the two extra scenarios suggested above to reach 12 total.
