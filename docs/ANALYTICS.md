# Analytics

Computed in `shared/.../domain/analytics` from an event's registrations and attendance sessions. Pure Kotlin,
unit-tested (`AnalyticsTest`), and used by the screens, the CSV exports and the PDF report alike.

## Definitions

| Metric | Definition |
|---|---|
| Valid registrations | status `REGISTERED`. Cancelled ones, and their sessions, are ignored. |
| Unique attendees | valid registrations with at least one session |
| Attendance rate | unique attendees ÷ valid registrations. No registrations → "No data yet", not 0%. |
| Completed session | has a check-out (`COMPLETED` or `CORRECTED`) |
| Incomplete session | still `ACTIVE`. No check-out time is invented; it is in no average or median. |
| Average / median per session | over completed sessions' `durationSeconds` |
| Average / median per participant | each participant's sum of completed sessions |

## How attendance ended (per participant)

`grace` = Settings → grace period (default 10 minutes).

| Outcome | Rule, first match wins |
|---|---|
| Absent | no session |
| Missing check-out | has an open session |
| Full attendance | total time ≥ scheduled duration − grace |
| Left early | last check-out < event end − grace |
| Partial | checked out normally but present for less than the event (arrived late, for example) |

So leaving a few minutes early is still full attendance, and a missing check-out is never counted as a departure.

## Charts (all from stored data)

Arrivals and departures per 15 minutes, session durations per 30 minutes, registrations per day, attendees by
faculty and department, the outcome split, and the participant table. An event with no check-ins shows an
empty state, not empty charts.

## Club-wide

Totals, attendance rate and average time come from counters on each event document
(`registrationCount, attendeeCount, completedSessionCount, activeSessionCount, completedDurationSeconds`), which the
attendance transactions maintain. The club median, and attendance over time, use the sessions of the last
180 days (capped at 3000). Drafts and archived events are excluded. Also: event comparison table, most attended,
longest average stay, participation by month.

## Exports (Reports)

Attendance CSV, participants CSV, sessions CSV, event analytics CSV, event comparison CSV, and a PDF report.
CSV files carry a UTF-8 byte-order mark for Excel and neutralise spreadsheet formulas in user-typed text.
The PDF uses the built-in Latin-1 fonts: names in other scripts print as "?". Use the CSV for those.

## Limits
One event's analytics read up to 5000 registrations and 5000 sessions. Beyond that, move the aggregation to a function.
