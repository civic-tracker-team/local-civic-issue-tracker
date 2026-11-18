package org.civictracker.analysis.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.civictracker.analysis.domain.Candidates;

/** Model findings and the host policy that turns them into canonical developments. */
final class Wire {
  static final String ISSUE_ID_PATTERN = "^i[1-9][0-9]*$";

  private Wire() {}

  record Reply(List<IssueDraft> issues, List<DevelopmentDraft> developments) {
    Reply {
      issues = List.copyOf(issues);
      developments = List.copyOf(developments);
    }
  }

  record IssueDraft(String id, MatterType matterType, String name, String priorIssueId) {
    IssueDraft {
      issueId(id);
      Objects.requireNonNull(matterType);
      Objects.requireNonNull(name);
    }
  }

  enum MatterType {
    PROJECT("a specific project"),
    PROPOSAL("a specific proposal"),
    POLICY("a specific policy"),
    PROPERTY("a particular property"),
    PROGRAM("a program the source names and describes specifically"),
    OTHER_MATTER("another specific civic matter");
    final String label;

    MatterType(String label) {
      this.label = label;
    }
  }

  record Citation(int firstLine, int lastLine) {}

  record DevelopmentDraft(
      List<Citation> evidence, Finding finding, String does, List<LinkDraft> links) {
    DevelopmentDraft {
      evidence = List.copyOf(evidence);
      Objects.requireNonNull(finding);
      Objects.requireNonNull(does);
      links = List.copyOf(links);
    }
  }

  record LinkDraft(String issueId, Candidates.Basis basis, List<Citation> evidence) {
    LinkDraft {
      Wire.issueId(issueId);
      Objects.requireNonNull(basis);
      evidence = List.copyOf(evidence);
    }
  }

  enum Kind {
    PROPOSAL(Proposal.class, "a proposal put forward for decision"),
    ACTION(Action.class, "an action recorded at this meeting"),
    EARLIER_ACTION(EarlierAction.class, "an earlier action reported in this source"),
    UNCLEAR(Unclear.class, "a development whose outcome is not established");
    final Class<? extends Finding> type;
    final String label;

    Kind(Class<? extends Finding> type, String label) {
      this.type = type;
      this.label = label;
    }
  }

  enum Proposer {
    STAFF("city staff"),
    CITY_MANAGER("the City Manager"),
    MAYOR("the Mayor"),
    COUNCIL_MEMBER("a Council member"),
    COMMISSION_OR_COMMITTEE("a commission or committee"),
    OUTSIDE_AGENCY("an outside agency"),
    PUBLIC("the public"),
    OTHER("another proposer"),
    NOT_STATED("proposer not stated");
    final String label;

    Proposer(String label) {
      this.label = label;
    }
  }

  enum Actor {
    CITY_COUNCIL("the City Council"),
    MAYOR("the Mayor"),
    COUNCIL_MEMBER("a Council member"),
    CITY_MANAGER("the City Manager"),
    STAFF("city staff"),
    COMMISSION_OR_COMMITTEE("a commission or committee"),
    OUTSIDE_AGENCY("an outside agency"),
    OTHER("another actor");
    final String label;

    Actor(String label) {
      this.label = label;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Proposal.class, name = "PROPOSAL"),
    @JsonSubTypes.Type(value = Action.class, name = "ACTION"),
    @JsonSubTypes.Type(value = EarlierAction.class, name = "EARLIER_ACTION"),
    @JsonSubTypes.Type(value = Unclear.class, name = "UNCLEAR")
  })
  sealed interface Finding permits Proposal, Action, EarlierAction, Unclear {
    Candidates.Status status();

    List<Candidates.DateClaim> dates(LocalDate meetingDate);
  }

  record Proposal(Proposer proposedBy, List<DateValue> planned) implements Finding {
    Proposal {
      Objects.requireNonNull(proposedBy);
      planned = List.copyOf(planned);
    }

    public Candidates.Status status() {
      return Candidates.Status.PROPOSED;
    }

    public List<Candidates.DateClaim> dates(LocalDate meetingDate) {
      return withPlanned(List.of(meeting(meetingDate)), planned);
    }
  }

  record Action(Actor actedBy, List<DateValue> planned) implements Finding {
    Action {
      Objects.requireNonNull(actedBy);
      planned = List.copyOf(planned);
    }

    public Candidates.Status status() {
      return Candidates.Status.RECORDED;
    }

    public List<Candidates.DateClaim> dates(LocalDate meetingDate) {
      return withPlanned(
          List.of(
              new Day(meetingDate.toString(), false).claim(Candidates.Role.OCCURRENCE),
              meeting(meetingDate)),
          planned);
    }
  }

  record EarlierAction(Actor actedBy, DateValue occurred, List<DateValue> planned)
      implements Finding {
    EarlierAction {
      Objects.requireNonNull(actedBy);
      Objects.requireNonNull(occurred);
      planned = List.copyOf(planned);
    }

    public Candidates.Status status() {
      return Candidates.Status.RECORDED;
    }

    public List<Candidates.DateClaim> dates(LocalDate meetingDate) {
      return withPlanned(
          List.of(occurred.claim(Candidates.Role.OCCURRENCE), meeting(meetingDate)), planned);
    }
  }

  record Unclear(List<DateValue> planned) implements Finding {
    Unclear {
      planned = List.copyOf(planned);
    }

    public Candidates.Status status() {
      return Candidates.Status.UNCERTAIN;
    }

    public List<Candidates.DateClaim> dates(LocalDate meetingDate) {
      return withPlanned(List.of(meeting(meetingDate)), planned);
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "precision")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Day.class, name = "DAY"),
        @JsonSubTypes.Type(value = Month.class, name = "MONTH"),
    @JsonSubTypes.Type(value = Year.class, name = "YEAR"),
        @JsonSubTypes.Type(value = DateRange.class, name = "RANGE"),
    @JsonSubTypes.Type(value = UnknownDate.class, name = "UNKNOWN")
  })
  sealed interface DateValue permits Day, Month, Year, DateRange, UnknownDate {
    Candidates.DateClaim claim(Candidates.Role role);
  }

  record Day(String value, boolean approximate) implements DateValue {
    Day {
      Objects.requireNonNull(value);
    }

    public Candidates.DateClaim claim(Candidates.Role role) {
      return new Candidates.DateClaim(role, Candidates.Precision.DAY, value, null, approximate);
    }
  }

  record Month(String value, boolean approximate) implements DateValue {
    Month {
      Objects.requireNonNull(value);
    }

    public Candidates.DateClaim claim(Candidates.Role role) {
      return new Candidates.DateClaim(role, Candidates.Precision.MONTH, value, null, approximate);
    }
  }

  record Year(String value, boolean approximate) implements DateValue {
    Year {
      Objects.requireNonNull(value);
    }

    public Candidates.DateClaim claim(Candidates.Role role) {
      return new Candidates.DateClaim(role, Candidates.Precision.YEAR, value, null, approximate);
    }
  }

  record DateRange(String start, String end, boolean approximate) implements DateValue {
    DateRange {
      Objects.requireNonNull(start);
      Objects.requireNonNull(end);
    }

    public Candidates.DateClaim claim(Candidates.Role role) {
      return new Candidates.DateClaim(role, Candidates.Precision.RANGE, start, end, approximate);
    }
  }

  record UnknownDate(boolean approximate) implements DateValue {
    public Candidates.DateClaim claim(Candidates.Role role) {
      return new Candidates.DateClaim(role, Candidates.Precision.UNKNOWN, null, null, approximate);
    }
  }

  static String render(Finding finding, String does) {
    String prefix =
        switch (finding) {
          case Proposal p ->
              p.proposedBy() == Proposer.NOT_STATED
                  ? "Proposed for decision: "
                  : "Proposed by " + p.proposedBy().label + " for decision: ";
          case Action a -> "Recorded action by " + a.actedBy().label + ": ";
          case EarlierAction a -> "Earlier action by " + a.actedBy().label + " reported: ";
          case Unclear u -> "Outcome not established: ";
        };
    return prefix + does;
  }

  private static Candidates.DateClaim meeting(LocalDate date) {
    return new Day(date.toString(), false).claim(Candidates.Role.MEETING);
  }

  private static List<Candidates.DateClaim> withPlanned(
      List<Candidates.DateClaim> known, List<DateValue> planned) {
    var result = new ArrayList<>(known);
    planned.forEach(date -> result.add(date.claim(Candidates.Role.PLANNED)));
    return List.copyOf(result);
  }

  private static void issueId(String id) {
    if (id == null || !id.matches(ISSUE_ID_PATTERN))
      throw new IllegalArgumentException("Invalid local issue ID");
  }
}
