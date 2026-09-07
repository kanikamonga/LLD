package LLD.GoogleCalendar;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GoogleCalendarDesign {
    public enum MeetingType { ONE_TIME, RECURRING }
    public enum InvitationStatus { PENDING, ACCEPTED, DECLINED, PROPOSED_CHANGE }
    public enum ProposalStatus { PENDING, ACCEPTED, DECLINED }
    public enum UpdateScope { THIS_OCCURRENCE, THIS_AND_FUTURE_OCCURRENCES, ALL_OCCURRENCES }
    public enum CancellationScope {
        THIS_OCCURRENCE, THIS_AND_FUTURE_OCCURRENCES, ENTIRE_SERIES
    }
    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    public static final class User {
        private final String id;
        private final String name;

        public User(String id, String name) {
            this.id = required(id, "user id");
            this.name = required(name, "user name");
        }
        public String id() { return id; }
        public String name() { return name; }
    }

    public static final class TimeRange {
        private final LocalDateTime start;
        private final LocalDateTime end;

        public TimeRange(LocalDateTime start, LocalDateTime end) {
            if (start == null || end == null || !start.isBefore(end)) {
                throw new IllegalArgumentException("Invalid time range");
            }
            this.start = start;
            this.end = end;
        }
        public LocalDateTime start() { return start; }
        public LocalDateTime end() { return end; }
    }

    public interface RecurrenceRule {
        List<TimeRange> occurrencesBetween(LocalDateTime baseStart,
                                            LocalDateTime baseEnd,
                                            LocalDateTime from,
                                            LocalDateTime to);
    }

    public static final class IntervalRecurrenceRule implements RecurrenceRule {
        private final Duration interval;
        private final LocalDateTime until;
        private final Integer occurrenceLimit;

        public IntervalRecurrenceRule(Duration interval, LocalDateTime until,
                                      Integer occurrenceLimit) {
            if (interval == null || interval.isZero() || interval.isNegative()
                    || (until == null && occurrenceLimit == null)
                    || (occurrenceLimit != null && occurrenceLimit < 1)) {
                throw new IllegalArgumentException("Invalid interval rule");
            }
            this.interval = interval;
            this.until = until;
            this.occurrenceLimit = occurrenceLimit;
        }

        @Override
        public List<TimeRange> occurrencesBetween(LocalDateTime baseStart,
                                                   LocalDateTime baseEnd,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
            List<TimeRange> result = new ArrayList<>();
            LocalDateTime start = baseStart;
            int occurrence = 0;
            while (!start.isAfter(to) && (until == null || !start.isAfter(until))
                    && (occurrenceLimit == null || occurrence < occurrenceLimit)) {
                if (overlaps(start, baseEnd, from, to)) {
                    result.add(new TimeRange(start, start.plus(Duration.between(baseStart, baseEnd))));
                }
                start = start.plus(interval);
                occurrence++;
            }
            return result;
        }
    }

    public static final class CalendarRecurrenceRule implements RecurrenceRule {
        private final Frequency frequency;
        private final int interval;
        private final Set<DayOfWeek> daysOfWeek;
        private final Integer dayOfMonth;
        private final LocalDateTime until;
        private final Integer occurrenceLimit;

        public CalendarRecurrenceRule(Frequency frequency, int interval,
                                      Set<DayOfWeek> daysOfWeek, Integer dayOfMonth,
                                      LocalDateTime until, Integer occurrenceLimit) {
            if (frequency == null || interval < 1
                    || (until == null && occurrenceLimit == null)
                    || (occurrenceLimit != null && occurrenceLimit < 1)) {
                throw new IllegalArgumentException("Invalid calendar rule");
            }
            this.frequency = frequency;
            this.interval = interval;
            this.daysOfWeek = daysOfWeek == null || daysOfWeek.isEmpty()
                    ? Collections.<DayOfWeek>emptySet()
                    : EnumSet.copyOf(daysOfWeek);
            this.dayOfMonth = dayOfMonth;
            this.until = until;
            this.occurrenceLimit = occurrenceLimit;
        }

        @Override
        public List<TimeRange> occurrencesBetween(LocalDateTime baseStart,
                                                   LocalDateTime baseEnd,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
            List<TimeRange> result = new ArrayList<>();
            Duration duration = Duration.between(baseStart, baseEnd);
            LocalDateTime candidate = baseStart;
            int count = 0;
            while (!candidate.isAfter(to) && (until == null || !candidate.isAfter(until))
                    && (occurrenceLimit == null || count < occurrenceLimit)) {
                if (matches(candidate, baseStart)) {
                    if (overlaps(candidate, candidate.plus(duration), from, to)) {
                        result.add(new TimeRange(candidate, candidate.plus(duration)));
                    }
                    count++;
                }
                candidate = candidate.plusDays(1);
            }
            return result;
        }

        private boolean matches(LocalDateTime candidate, LocalDateTime base) {
            switch (frequency) {
                case DAILY:
                    return daysBetween(base.toLocalDate(), candidate.toLocalDate()) % interval == 0;
                case WEEKLY:
                    long weeks = daysBetween(base.toLocalDate(), candidate.toLocalDate()) / 7;
                    return weeks % interval == 0
                            && (daysOfWeek.isEmpty() ? candidate.getDayOfWeek() == base.getDayOfWeek()
                            : daysOfWeek.contains(candidate.getDayOfWeek()));
                case MONTHLY:
                    return monthsBetween(base.toLocalDate(), candidate.toLocalDate()) % interval == 0
                            && candidate.getDayOfMonth() == (dayOfMonth == null
                            ? base.getDayOfMonth() : dayOfMonth);
                case YEARLY:
                    return candidate.getMonth() == base.getMonth()
                            && candidate.getDayOfMonth() == (dayOfMonth == null
                            ? base.getDayOfMonth() : dayOfMonth)
                            && (candidate.getYear() - base.getYear()) % interval == 0;
                default:
                    return false;
            }
        }
    }

    public static class Event {
        private final String id;
        private String title;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private final MeetingType type;
        private RecurrenceRule recurrenceRule;

        protected Event(String id, String title, String description,
                        LocalDateTime startTime, LocalDateTime endTime,
                        MeetingType type, RecurrenceRule recurrenceRule) {
            if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Invalid event time range");
            }
            if (type == MeetingType.RECURRING && recurrenceRule == null) {
                throw new IllegalArgumentException("Recurring event needs a rule");
            }
            this.id = required(id, "event id");
            this.title = required(title, "event title");
            this.description = description == null ? "" : description;
            this.startTime = startTime;
            this.endTime = endTime;
            this.type = type;
            this.recurrenceRule = recurrenceRule;
        }
        public String id() { return id; }
        public String title() { return title; }
        public String description() { return description; }
        public LocalDateTime startTime() { return startTime; }
        public LocalDateTime endTime() { return endTime; }
        public MeetingType type() { return type; }
        public RecurrenceRule recurrenceRule() { return recurrenceRule; }
        protected void updateDetails(String title, String description,
                                     LocalDateTime start, LocalDateTime end,
                                     RecurrenceRule rule) {
            if (start == null || end == null || !start.isBefore(end)) {
                throw new IllegalArgumentException("Invalid event time range");
            }
            this.title = required(title, "event title");
            this.description = description == null ? "" : description;
            this.startTime = start;
            this.endTime = end;
            this.recurrenceRule = rule;
        }
    }

    public static final class Meeting extends Event {
        private final User organizer;
        private final Map<String, MeetingInvitation> invitations = new HashMap<>();
        private final Map<LocalDateTime, TimeRange> occurrenceOverrides = new HashMap<>();
        private final Set<LocalDateTime> cancelledOccurrences = new HashSet<>();
        private LocalDateTime cancelledFrom;
        private FutureChange futureChange;
        private boolean seriesCancelled;

        public Meeting(String id, String title, String description,
                       LocalDateTime start, LocalDateTime end, User organizer,
                       MeetingType type, RecurrenceRule rule) {
            super(id, title, description, start, end, type, rule);
            this.organizer = organizer;
        }
        public User organizer() { return organizer; }
        public void invite(User user) {
            if (user == null || organizer.id().equals(user.id())) {
                throw new IllegalArgumentException("Invalid participant");
            }
            invitations.putIfAbsent(user.id(), new MeetingInvitation(this, user));
        }
        public MeetingInvitation invitationFor(String userId) {
            return Optional.ofNullable(invitations.get(userId))
                    .orElseThrow(() -> new IllegalArgumentException("User is not invited"));
        }
        public void update(User actor, UpdateScope scope, LocalDateTime occurrence,
                           String title, String description, LocalDateTime start,
                           LocalDateTime end, RecurrenceRule rule) {
            requireOrganizer(actor);
            if (scope == UpdateScope.THIS_OCCURRENCE) {
                occurrenceOverrides.put(requiredOccurrence(occurrence),
                        new TimeRange(start, end));
            } else if (scope == UpdateScope.ALL_OCCURRENCES) {
                updateDetails(title, description, start, end, rule);
                futureChange = null;
            } else {
                futureChange = new FutureChange(requiredOccurrence(occurrence), title,
                        description, start, end, rule);
            }
        }
        public void cancel(User actor, CancellationScope scope, LocalDateTime occurrence) {
            requireOrganizer(actor);
            if (scope == CancellationScope.ENTIRE_SERIES) {
                seriesCancelled = true;
            } else if (scope == CancellationScope.THIS_OCCURRENCE) {
                cancelledOccurrences.add(requiredOccurrence(occurrence));
            } else {
                cancelledFrom = requiredOccurrence(occurrence);
            }
        }
        private void requireOrganizer(User actor) {
            if (actor == null || !organizer.id().equals(actor.id())) {
                throw new IllegalArgumentException("Only organizer may modify a meeting");
            }
        }
        private LocalDateTime requiredOccurrence(LocalDateTime value) {
            if (value == null) throw new IllegalArgumentException("Occurrence is required");
            return value;
        }
        private List<TimeRange> visibleOccurrences(LocalDateTime from, LocalDateTime to) {
            if (seriesCancelled) return Collections.emptyList();
            List<TimeRange> occurrences = type() == MeetingType.ONE_TIME
                    ? Collections.singletonList(new TimeRange(startTime(), endTime()))
                    : recurrenceRule().occurrencesBetween(startTime(), endTime(), from, to);
            List<TimeRange> visible = new ArrayList<>();
            for (TimeRange occurrence : occurrences) {
                LocalDateTime originalStart = occurrence.start();
                if (futureChange != null && !originalStart.isBefore(futureChange.effectiveFrom)) {
                    continue;
                }
                if (cancelledOccurrences.contains(originalStart)
                        || (cancelledFrom != null && !originalStart.isBefore(cancelledFrom))) continue;
                TimeRange override = occurrenceOverrides.get(originalStart);
                visible.add(override == null ? occurrence : override);
            }
            if (futureChange != null) {
                List<TimeRange> futureOccurrences = futureChange.rule == null
                        ? Collections.singletonList(new TimeRange(
                        futureChange.start, futureChange.end))
                        : futureChange.rule.occurrencesBetween(
                        futureChange.start, futureChange.end, from, to);
                for (TimeRange occurrence : futureOccurrences) {
                    if (!occurrence.start().isBefore(futureChange.effectiveFrom)
                            && (cancelledFrom == null
                            || occurrence.start().isBefore(cancelledFrom))) {
                        visible.add(occurrence);
                    }
                }
            }
            return visible;
        }

        private static final class FutureChange {
            private final LocalDateTime effectiveFrom;
            private final String title;
            private final String description;
            private final LocalDateTime start;
            private final LocalDateTime end;
            private final RecurrenceRule rule;

            private FutureChange(LocalDateTime effectiveFrom, String title,
                                 String description, LocalDateTime start,
                                 LocalDateTime end, RecurrenceRule rule) {
                if (start == null || end == null || !start.isBefore(end)) {
                    throw new IllegalArgumentException("Invalid future event time range");
                }
                this.effectiveFrom = effectiveFrom;
                this.title = title;
                this.description = description;
                this.start = start;
                this.end = end;
                this.rule = rule;
            }
        }
    }

    public static final class MeetingInvitation {
        private final Meeting meeting;
        private final User participant;
        private InvitationStatus status = InvitationStatus.PENDING;

        private MeetingInvitation(Meeting meeting, User participant) {
            this.meeting = meeting;
            this.participant = participant;
        }
        public InvitationStatus status() { return status; }
        public void accept() { status = InvitationStatus.ACCEPTED; }
        public void decline() { status = InvitationStatus.DECLINED; }
        public TimeChangeProposal proposeTimeChange(LocalDateTime start,
                                                     LocalDateTime end, String reason) {
            if (status == InvitationStatus.DECLINED) {
                throw new IllegalStateException("Declined invitation cannot propose a change");
            }
            status = InvitationStatus.PROPOSED_CHANGE;
            return new TimeChangeProposal(meeting, participant, start, end, reason);
        }
    }

    public static final class TimeChangeProposal {
        private final Meeting meeting;
        private final User proposer;
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final String reason;
        private ProposalStatus status = ProposalStatus.PENDING;

        private TimeChangeProposal(Meeting meeting, User proposer,
                                   LocalDateTime start, LocalDateTime end, String reason) {
            if (start == null || end == null || !start.isBefore(end)) {
                throw new IllegalArgumentException("Invalid proposed time");
            }
            this.meeting = meeting;
            this.proposer = proposer;
            this.start = start;
            this.end = end;
            this.reason = required(reason, "proposal reason");
        }
        public ProposalStatus status() { return status; }
        public String reason() { return reason; }
        public void accept(User organizer, LocalDateTime occurrence) {
            meeting.update(organizer, UpdateScope.THIS_OCCURRENCE, occurrence,
                    meeting.title(), meeting.description(), start, end, meeting.recurrenceRule());
            status = ProposalStatus.ACCEPTED;
        }
        public void decline(User organizer) {
            if (organizer == null || !meeting.organizer().id().equals(organizer.id())) {
                throw new IllegalArgumentException("Only organizer may reject a proposal");
            }
            status = ProposalStatus.DECLINED;
        }
    }

    public interface MeetingRepository {
        Meeting save(Meeting meeting);
        Optional<Meeting> findById(String meetingId);
        List<Meeting> findByParticipantAndRange(String userId,
                                                LocalDateTime from, LocalDateTime to);
    }

    public static final class InMemoryMeetingRepository implements MeetingRepository {
        private final Map<String, Meeting> meetings = new HashMap<>();
        public synchronized Meeting save(Meeting meeting) {
            meetings.put(meeting.id(), meeting);
            return meeting;
        }
        public synchronized Optional<Meeting> findById(String id) {
            return Optional.ofNullable(meetings.get(id));
        }
        public synchronized List<Meeting> findByParticipantAndRange(
                String userId, LocalDateTime from, LocalDateTime to) {
            List<Meeting> result = new ArrayList<>();
            for (Meeting meeting : meetings.values()) {
                boolean participant = meeting.organizer().id().equals(userId);
                if (!participant) {
                    try {
                        meeting.invitationFor(userId);
                        participant = true;
                    } catch (IllegalArgumentException ignored) {
                        // User is not an attendee.
                    }
                }
                if (participant && hasOccurrence(meeting, from, to)) result.add(meeting);
            }
            return result;
        }
        private boolean hasOccurrence(Meeting meeting, LocalDateTime from, LocalDateTime to) {
            return !meeting.type().equals(MeetingType.RECURRING)
                    ? overlaps(meeting.startTime(), meeting.endTime(), from, to)
                    : !meeting.recurrenceRule().occurrencesBetween(
                    meeting.startTime(), meeting.endTime(), from, to).isEmpty();
        }
    }

    public static final class CalendarService {
        private final MeetingRepository repository;
        public CalendarService(MeetingRepository repository) {
            this.repository = repository;
        }
        public List<TimeRange> getEvents(String userId, LocalDateTime from, LocalDateTime to) {
            List<TimeRange> result = new ArrayList<>();
            for (Meeting meeting : repository.findByParticipantAndRange(userId, from, to)) {
                result.addAll(meeting.visibleOccurrences(from, to));
            }
            result.sort(Comparator.comparing(TimeRange::start));
            return result;
        }
    }

    public static final class MeetingService {
        private final MeetingRepository repository;
        public MeetingService(MeetingRepository repository) { this.repository = repository; }
        public Meeting create(User organizer, String title, String description,
                              LocalDateTime start, LocalDateTime end,
                              MeetingType type, RecurrenceRule rule, List<User> participants) {
            Meeting meeting = new Meeting(UUID.randomUUID().toString(), title, description,
                    start, end, organizer, type, rule);
            if (participants != null) for (User participant : participants) meeting.invite(participant);
            return repository.save(meeting);
        }
        public Meeting get(String id) {
            return repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Meeting not found"));
        }
    }

    private static boolean overlaps(LocalDateTime start, LocalDateTime end,
                                    LocalDateTime from, LocalDateTime to) {
        return start.isBefore(to) && end.isAfter(from);
    }
    private static long daysBetween(LocalDate a, LocalDate b) {
        return java.time.temporal.ChronoUnit.DAYS.between(a, b);
    }
    private static long monthsBetween(LocalDate a, LocalDate b) {
        return java.time.temporal.ChronoUnit.MONTHS.between(
                YearMonth.from(a), YearMonth.from(b));
    }
    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
    private GoogleCalendarDesign() { }
}
