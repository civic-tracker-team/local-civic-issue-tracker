// Sample content transcribed from the seven signed-off a boards. Client-only fixture module.
import type { Coverage, Issue, Meeting, CivicRecord, HomeData } from "./types";

export const coverage: Coverage = {
  "authority": "San Bernardino City Council",
  "range": "Jan 2023 – Oct 2026",
  "recordTypes": [
    "agendas",
    "minutes",
    "staff reports",
    "attachments",
    "official transcripts",
    "cancellation notices"
  ],
  "scheduled": 92,
  "held": 90,
  "cancelled": 2,
  "upcoming": 1,
  "months": [
    {
      "year": 2023,
      "month": 1,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 2,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 3,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 4,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 5,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 6,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 7,
      "held": 1,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 8,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 9,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 10,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 11,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2023,
      "month": 12,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 1,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 2,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 3,
      "held": 3,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 4,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 5,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 6,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 7,
      "held": 1,
      "cancelled": 1,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 8,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 9,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 10,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 11,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2024,
      "month": 12,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 1,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 2,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 3,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 4,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 5,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 6,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 7,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 8,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 9,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 10,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 11,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2025,
      "month": 12,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 1,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 2,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 3,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 4,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 5,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 6,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 7,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 8,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 9,
      "held": 1,
      "cancelled": 1,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 10,
      "held": 2,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 11,
      "held": 0,
      "cancelled": 0,
      "upcoming": 1,
      "notYet": false
    },
    {
      "year": 2026,
      "month": 12,
      "held": 0,
      "cancelled": 0,
      "upcoming": 0,
      "notYet": true
    }
  ]
};

export const issues: Issue[] = [
  {
    "id": "downtown-parking-structure",
    "name": "Downtown parking structure",
    "previewSummary": "A proposed public garage near the civic center, now in design after an Oct 15, 2026 contract approval.",
    "summary": "The city is planning a public parking garage near the civic center. After a 2024 parking study and community meetings, Council chose a site in 2025, set aside funding, and on Oct 15, 2026 approved a contract to design it. Construction has not been approved.",
    "latest": "Design contract approved, 5–2.",
    "meetingCount": 9,
    "recordCount": 31,
    "span": "Mar 2024 – Oct 2026",
    "group": "Active in October 2026",
    "basis": null,
    "status": "documented",
    "tag": "",
    "developments": [
      {
        "id": "parking-1",
        "issueId": "downtown-parking-structure",
        "text": "Staff presented a parking study and recommended a public garage near the civic center.",
        "date": {
          "label": "Mar 6, 2024",
          "precision": "day",
          "role": "occurrence",
          "note": "Study session"
        },
        "sourceMeetingDate": {
          "label": "Mar 6, 2024",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "proposed",
        "basis": "stated",
        "tag": "Proposed · stated in staff report",
        "evidence": [
          {
            "id": "parking-evidence-1",
            "recordId": "parking-study",
            "versionId": null,
            "source": "Staff report, Mar 6, 2024, p. 3",
            "passage": "“Staff recommends that Council consider a public parking structure of 500 to 650 spaces within the civic center area.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      },
      {
        "id": "parking-2",
        "issueId": "downtown-parking-structure",
        "text": "Staff held three community meetings about the garage.",
        "date": {
          "label": "Spring 2024",
          "precision": "approximate",
          "role": "occurrence",
          "note": "Approximate; from a later report"
        },
        "sourceMeetingDate": {
          "label": "Jun 19, 2024",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "documented",
        "basis": "stated",
        "tag": "Stated",
        "evidence": [
          {
            "id": "parking-evidence-2",
            "recordId": "community-meetings-report",
            "versionId": null,
            "source": "Staff report, Jun 19, 2024, p. 2",
            "passage": "“During spring 2024, staff hosted three community meetings on the proposed structure.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      },
      {
        "id": "parking-3",
        "issueId": "downtown-parking-structure",
        "text": "Council directed staff to evaluate possible sites.",
        "date": {
          "label": "Jun 19, 2024",
          "precision": "day",
          "role": "occurrence",
          "note": "Regular meeting"
        },
        "sourceMeetingDate": {
          "label": "Jun 19, 2024",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "documented",
        "basis": "stated",
        "tag": "Stated in minutes",
        "evidence": [
          {
            "id": "parking-evidence-3",
            "recordId": "minutes-2024-06-19",
            "versionId": null,
            "source": "Minutes, Jun 19, 2024, item 14",
            "passage": "“Council directed staff to return with an evaluation of candidate sites.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      },
      {
        "id": "parking-4",
        "issueId": "downtown-parking-structure",
        "text": "Staff evaluated four candidate sites. A traffic study ran at about the same time; the records don’t say which finished first.",
        "date": {
          "label": "Nov 2024 – Jan 2025",
          "precision": "range",
          "role": "occurrence",
          "note": "Date range; order with the traffic study unknown"
        },
        "sourceMeetingDate": {
          "label": "Feb 5, 2025",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "documented",
        "basis": "stated",
        "tag": "Stated",
        "evidence": [
          {
            "id": "parking-evidence-4",
            "recordId": "site-evaluation",
            "versionId": null,
            "source": "Staff report, Feb 5, 2025, p. 4",
            "passage": "“Between November 2024 and January 2025, staff evaluated four candidate sites.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      },
      {
        "id": "parking-5",
        "issueId": "downtown-parking-structure",
        "text": "The city-owned lot east of City Hall was named the preferred site.",
        "date": {
          "label": "Feb 5, 2025",
          "precision": "day",
          "role": "reported",
          "note": "Reported date; when it happened isn’t stated"
        },
        "sourceMeetingDate": {
          "label": "Feb 5, 2025",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "documented",
        "basis": "stated",
        "tag": "Stated in minutes",
        "evidence": [
          {
            "id": "parking-evidence-5",
            "recordId": "minutes-2025-02-05",
            "versionId": null,
            "source": "Minutes, Feb 5, 2025, item 9",
            "passage": "“Site A was identified as the preferred location.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      },
      {
        "id": "parking-6",
        "issueId": "downtown-parking-structure",
        "text": "Council set aside money to design and build the garage.",
        "date": {
          "label": "Aug 2025",
          "precision": "month",
          "role": "occurrence",
          "note": "Budget adoption"
        },
        "sourceMeetingDate": null,
        "publicationDate": null,
        "status": "conflict",
        "basis": "stated",
        "tag": "Sources disagree",
        "evidence": [
          {
            "id": "parking-evidence-6",
            "recordId": "adopted-budget",
            "versionId": null,
            "source": "Adopted budget, attachment B",
            "passage": "“Downtown parking structure: $26.1 million.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [
          {
            "evidence": {
              "id": "adopted-budget",
              "recordId": "adopted-budget",
              "versionId": null,
              "source": "Adopted budget, attachment B (Aug 27, 2025)",
              "passage": "Downtown parking structure: $26.1 million.",
              "basis": "stated",
              "explanation": null,
              "videoTime": null
            },
            "publicationDate": {
              "label": "Aug 27, 2025",
              "precision": "day",
              "role": "publication",
              "note": ""
            },
            "account": "Downtown parking structure: $26.1 million.",
            "latest": true
          },
          {
            "evidence": {
              "id": "budget-staff-report",
              "recordId": "budget-staff-report",
              "versionId": null,
              "source": "Staff report (Aug 20, 2025)",
              "passage": "$24.5 million.",
              "basis": "stated",
              "explanation": null,
              "videoTime": null
            },
            "publicationDate": {
              "label": "Aug 20, 2025",
              "precision": "day",
              "role": "publication",
              "note": ""
            },
            "account": "$24.5 million.",
            "latest": false
          }
        ],
        "initiallyOpen": false
      },
      {
        "id": "parking-7",
        "issueId": "downtown-parking-structure",
        "text": "The city asked firms to propose designs for a civic center project.",
        "date": {
          "label": "Mar 18, 2026",
          "precision": "day",
          "role": "occurrence",
          "note": "Regular meeting"
        },
        "sourceMeetingDate": {
          "label": "Mar 18, 2026",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "documented",
        "basis": "inferred",
        "tag": "Inferred link · may be incorrect",
        "evidence": [
          {
            "id": "parking-evidence-7",
            "recordId": "agenda-2026-03-18",
            "versionId": null,
            "source": "Agenda, Mar 18, 2026, consent item 6",
            "passage": "“Authorize release of RFP No. 26-014 for design services, civic center area.”",
            "basis": "inferred",
            "explanation": "same site and scope as the Feb 5, 2025 selection; the record doesn’t name the garage.",
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": true
      },
      {
        "id": "parking-8",
        "issueId": "downtown-parking-structure",
        "text": "Council approved the design contract, 5–2.",
        "date": {
          "label": "Oct 15, 2026",
          "precision": "day",
          "role": "occurrence",
          "note": "Regular meeting · video 1:42:10"
        },
        "sourceMeetingDate": {
          "label": "Oct 15, 2026",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "documented",
        "basis": "stated",
        "tag": "Stated in minutes",
        "evidence": [
          {
            "id": "parking-evidence-8",
            "recordId": "minutes-2026-10-15",
            "versionId": "2",
            "source": "Minutes, Oct 15, 2026, item 12",
            "passage": "“Motion to approve the professional services agreement for design of the downtown parking structure carried 5–2.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": "1:42:10"
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      },
      {
        "id": "parking-9",
        "issueId": "downtown-parking-structure",
        "text": "Design is expected to be finished.",
        "date": {
          "label": "Late 2027",
          "precision": "approximate",
          "role": "expected",
          "note": "Expected"
        },
        "sourceMeetingDate": {
          "label": "Oct 15, 2026",
          "precision": "day",
          "role": "reported",
          "note": ""
        },
        "publicationDate": null,
        "status": "planned",
        "basis": "stated",
        "tag": "Expected · not confirmed",
        "evidence": [
          {
            "id": "parking-evidence-9",
            "recordId": "design-report",
            "versionId": null,
            "source": "Staff report, Oct 15, 2026, p. 6",
            "passage": "“Design is anticipated to be complete in late 2027.”",
            "basis": "stated",
            "explanation": null,
            "videoTime": null
          }
        ],
        "conflicts": [],
        "initiallyOpen": false
      }
    ]
  },
  {
    "id": "short-term-rental-ordinance",
    "name": "Short-term rental ordinance",
    "summary": "",
    "latest": "First reading held; second reading Nov 5.",
    "meetingCount": 5,
    "recordCount": null,
    "span": "Jun 2025 – Oct 2026",
    "group": "Active in October 2026",
    "basis": null,
    "status": "planned",
    "tag": "Planned",
    "developments": []
  },
  {
    "id": "water-rate-adjustment",
    "name": "Water rate adjustment",
    "summary": "",
    "latest": "Rate study presented; no action.",
    "meetingCount": 4,
    "recordCount": null,
    "span": "Mar 2025 – Oct 2026",
    "group": "Active in October 2026",
    "basis": "inferred",
    "status": "documented",
    "tag": "Inferred link · may be incorrect",
    "developments": []
  },
  {
    "id": "street-resurfacing-program",
    "name": "Street resurfacing program",
    "summary": "",
    "latest": "14 more segments proposed.",
    "meetingCount": 14,
    "recordCount": null,
    "span": "Feb 2023 – Oct 2026",
    "group": "Active in October 2026",
    "basis": null,
    "status": "proposed",
    "tag": "Proposed",
    "developments": []
  },
  {
    "id": "northside-fire-station-replacement",
    "name": "Northside fire station replacement",
    "summary": "",
    "latest": "Bid award postponed.",
    "meetingCount": 7,
    "recordCount": null,
    "span": "Aug 2023 – Oct 2026",
    "group": "Active in October 2026",
    "basis": "inferred",
    "status": "documented",
    "tag": "Inferred link · may be incorrect",
    "developments": []
  },
  {
    "id": "park-restroom-renovations",
    "name": "Park restroom renovations",
    "summary": "",
    "latest": "On the agenda; outcome not recorded.",
    "meetingCount": 3,
    "recordCount": null,
    "span": "Jan 2025 – Sep 2026",
    "group": "Active in September 2026",
    "basis": null,
    "status": "unknown",
    "tag": "Outcome unavailable",
    "developments": []
  },
  {
    "id": "sidewalk-vending-permits",
    "name": "Sidewalk vending permits",
    "summary": "",
    "latest": "Fee schedule adopted (Jul 2).",
    "meetingCount": 6,
    "recordCount": null,
    "span": "Apr 2024 – Jul 2026",
    "group": "Earlier in 2026",
    "basis": null,
    "status": "documented",
    "tag": "",
    "developments": []
  },
  {
    "id": "civic-center-parking-permits",
    "name": "Civic center parking permits",
    "summary": "Changes to employee and visitor permit zones around City Hall.",
    "latest": "Permit zones revised (Jun 17).",
    "meetingCount": 4,
    "recordCount": null,
    "span": "Jan 2025 – Jun 2026",
    "group": "Earlier in 2026",
    "basis": null,
    "status": "documented",
    "tag": "",
    "developments": []
  },
  {
    "id": "library-branch-hours",
    "name": "Library branch hours",
    "summary": "",
    "latest": "Saturday hours restored (Feb 4).",
    "meetingCount": 3,
    "recordCount": null,
    "span": "Sep 2023 – Feb 2026",
    "group": "Earlier in 2026",
    "basis": null,
    "status": "documented",
    "tag": "",
    "developments": []
  },
  {
    "id": "animal-shelter-expansion",
    "name": "Animal shelter expansion",
    "summary": "",
    "latest": "Construction contract awarded (Jan 21).",
    "meetingCount": 4,
    "recordCount": null,
    "span": "Jul 2023 – Jan 2026",
    "group": "Earlier in 2026",
    "basis": null,
    "status": "documented",
    "tag": "",
    "developments": []
  }
];

export const meetings: Meeting[] = [
  {
    "id": "2026-11-05",
    "matchingAgendaItems": null,
    "date": "2026-11-05",
    "dateLabel": "Thu, Nov 5, 2026",
    "title": "Regular meeting",
    "status": "upcoming",
    "summary": "Agenda lists 18 items, including the second reading of rental rules.",
    "recordsLabel": "Agenda",
    "resources": [],
    "items": [],
    "previousId": null,
    "nextId": null,
    "time": null,
    "cancellationEvidence": null,
    "videoUrl": null
  },
  {
    "id": "2026-10-15",
    "matchingAgendaItems": 2,
    "listingSummary": "Garage design contract approved; rental rules first reading; water rate study presented.",
    "date": "2026-10-15",
    "dateLabel": "Thu, Oct 15, 2026",
    "title": "Regular meeting",
    "status": "held",
    "summary": "Council approved the parking garage design contract and held the first reading of short-term rental rules. A water rate study was presented with no action. For one appointment, the available records don’t say what was decided.",
    "recordsLabel": "Agenda · minutes · transcript · video",
    "resources": [
      {
        "label": "Agenda",
        "note": "Oct 9",
        "recordId": "agenda-2026-10-15",
        "url": null
      },
      {
        "label": "Minutes",
        "note": "Oct 22",
        "recordId": "minutes-2026-10-15",
        "url": null
      },
      {
        "label": "Transcript",
        "note": "official",
        "recordId": "transcript-2026-10-15",
        "url": null
      },
      {
        "label": "Meeting video",
        "note": "city site",
        "recordId": null,
        "url": null
      }
    ],
    "items": [
      {
        "id": "1-8",
        "label": "1–8",
        "topic": "Consent calendar: eight routine items",
        "outcome": "Approved together, 7–0.",
        "outcomeStatus": "recorded",
        "basis": "stated",
        "tag": "Stated in minutes",
        "source": "Minutes, consent",
        "recordId": "minutes-2026-10-15",
        "issueId": null,
        "issueName": null,
        "issueBasis": null,
        "videoTime": null
      },
      {
        "id": "item-9",
        "label": "Item 9",
        "topic": "First reading of an ordinance creating short-term rental permits",
        "outcome": "First reading held. Second reading set for Nov 5, 2026.",
        "outcomeStatus": "recorded",
        "basis": "stated",
        "tag": "Stated in minutes",
        "source": "Minutes, item 9",
        "recordId": "minutes-2026-10-15",
        "issueId": "short-term-rental-ordinance",
        "issueName": "Short-term rental ordinance",
        "issueBasis": "stated",
        "videoTime": "0:58:30"
      },
      {
        "id": "item-12",
        "label": "Item 12",
        "topic": "Professional services agreement to design the downtown parking structure",
        "outcome": "Approved, 5–2.",
        "outcomeStatus": "recorded",
        "basis": "stated",
        "tag": "Stated in minutes",
        "source": "Minutes, item 12",
        "recordId": "minutes-2026-10-15",
        "issueId": "downtown-parking-structure",
        "issueName": "Downtown parking structure",
        "issueBasis": "stated",
        "videoTime": "1:42:10"
      },
      {
        "id": "item-14",
        "label": "Item 14",
        "topic": "Presentation of the water rate study",
        "outcome": "Received and filed. No action taken.",
        "outcomeStatus": "recorded",
        "basis": "stated",
        "tag": "Stated in minutes",
        "source": "Minutes, item 14",
        "recordId": "minutes-2026-10-15",
        "issueId": "water-rate-adjustment",
        "issueName": "Water rate adjustment",
        "issueBasis": "inferred",
        "videoTime": null
      },
      {
        "id": "item-17",
        "label": "Item 17",
        "topic": "Appointment to the Parks and Recreation Commission",
        "outcome": "The available records don’t say what was decided.",
        "outcomeStatus": "unavailable",
        "basis": null,
        "tag": "Outcome not in available records",
        "source": "Agenda, item 17",
        "recordId": "agenda-2026-10-15",
        "issueId": null,
        "issueName": null,
        "issueBasis": null,
        "videoTime": null
      },
      {
        "id": "item-20",
        "label": "Item 20",
        "topic": "Closed session: labor negotiations",
        "outcome": "No reportable action.",
        "outcomeStatus": "recorded",
        "basis": "stated",
        "tag": "Stated in minutes",
        "source": "Minutes, closed session",
        "recordId": "minutes-2026-10-15",
        "issueId": null,
        "issueName": null,
        "issueBasis": null,
        "videoTime": null
      }
    ],
    "previousId": "2026-10-01",
    "nextId": "2026-11-05",
    "time": "5:00 pm",
    "cancellationEvidence": null,
    "videoUrl": null
  },
  {
    "id": "2026-10-01",
    "matchingAgendaItems": null,
    "date": "2026-10-01",
    "dateLabel": "Thu, Oct 1, 2026",
    "title": "Regular meeting",
    "status": "held",
    "summary": "Fire station bid award postponed; street resurfacing additions proposed.",
    "recordsLabel": "Agenda · minutes · video",
    "resources": [],
    "items": [],
    "previousId": null,
    "nextId": null,
    "time": null,
    "cancellationEvidence": null,
    "videoUrl": null
  },
  {
    "id": "2026-09-17",
    "matchingAgendaItems": null,
    "date": "2026-09-17",
    "dateLabel": "Thu, Sep 17, 2026",
    "title": "Regular meeting",
    "status": "held",
    "summary": "Park restroom renovations on the agenda. Minutes not yet published.",
    "recordsLabel": "Agenda · video",
    "resources": [],
    "items": [],
    "previousId": null,
    "nextId": null,
    "time": null,
    "cancellationEvidence": null,
    "videoUrl": null
  },
  {
    "id": "2026-09-03",
    "matchingAgendaItems": null,
    "date": "2026-09-03",
    "dateLabel": "Thu, Sep 3, 2026",
    "title": "Regular meeting",
    "status": "cancelled",
    "summary": "The city posted a cancellation notice. Cancelling a meeting doesn’t cancel the issues on its agenda.",
    "recordsLabel": "Cancellation notice",
    "resources": [],
    "items": [],
    "previousId": null,
    "nextId": null,
    "time": null,
    "cancellationEvidence": "The city posted a cancellation notice. Cancelling a meeting doesn’t cancel the issues on its agenda.",
    "videoUrl": null
  },
  {
    "id": "2025-07-02",
    "matchingAgendaItems": 1,
    "date": "2025-07-02",
    "dateLabel": "Wed, Jul 2, 2025",
    "title": "Regular meeting",
    "status": "held",
    "summary": "",
    "recordsLabel": "",
    "resources": [],
    "items": [],
    "previousId": null,
    "nextId": null,
    "time": null,
    "cancellationEvidence": null,
    "videoUrl": null
  },
  {
    "id": "2024-03-06",
    "matchingAgendaItems": 3,
    "date": "2024-03-06",
    "dateLabel": "Wed, Mar 6, 2024",
    "title": "Study session",
    "status": "held",
    "summary": "",
    "recordsLabel": "",
    "resources": [],
    "items": [],
    "previousId": null,
    "nextId": null,
    "time": null,
    "cancellationEvidence": null,
    "videoUrl": null
  }
];

export const records: CivicRecord[] = [
  {
    "id": "minutes-2026-10-15",
    "title": "Minutes · Regular meeting, Oct 15, 2026",
    "matchTitle": "Regular meeting minutes, item 12",
    "kind": "Minutes",
    "meetingIds": [
      "2026-10-15"
    ],
    "sourceMeetingDate": "Oct 15, 2026",
    "currentVersionId": "2",
    "pageCount": 14,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": "Oct 22, 2026",
        "correctedDate": null,
        "retrievedDate": "Oct 23, 2026",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "9. Ordinance establishing short-term rental permits (first reading)",
            "before": "Staff summarized the proposed permit, inspection and fee requirements. Following discussion, ",
            "highlight": "first reading was held; the ordinance will return for second reading on November 5, 2026.",
            "after": "",
            "marker": "A",
            "issueId": "short-term-rental-ordinance",
            "issueName": "Short-term rental ordinance",
            "supports": "first reading held, second reading planned",
            "basis": "stated",
            "page": 6
          },
          {
            "heading": "12. Professional services agreement: design of the downtown parking structure",
            "before": "Staff reviewed the scope in Exhibit A. ",
            "highlight": "Motion to approve the professional services agreement for design of the downtown parking structure carried 5–2",
            "after": ", Wards 2 and 6 voting no.",
            "marker": "B",
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "design contract approved, 5–2",
            "basis": "stated",
            "page": 6
          },
          {
            "heading": "14. Water rate study presentation",
            "before": "Staff and the consultant presented findings of the water rate study. ",
            "highlight": "Received and filed.",
            "after": " No action was taken.",
            "marker": "C",
            "issueId": "water-rate-adjustment",
            "issueName": "Water rate adjustment",
            "supports": "study presented, no action",
            "basis": "inferred",
            "page": 6
          },
          {
            "heading": "17. Appointment to the Parks and Recreation Commission",
            "before": "Item heard. No further entry for this item appears in these minutes.",
            "highlight": "",
            "after": "",
            "marker": null,
            "issueId": null,
            "issueName": null,
            "supports": null,
            "basis": null,
            "page": 6
          }
        ]
      },
      {
        "id": "2",
        "number": 2,
        "publicationDate": "Oct 22, 2026",
        "correctedDate": "Oct 28, 2026",
        "retrievedDate": "Oct 29, 2026",
        "correction": "The city corrected item 4 (attendance) on Oct 28.",
        "originalUrl": null,
        "passages": [
          {
            "heading": "9. Ordinance establishing short-term rental permits (first reading)",
            "before": "Staff summarized the proposed permit, inspection and fee requirements. Following discussion, ",
            "highlight": "first reading was held; the ordinance will return for second reading on November 5, 2026.",
            "after": "",
            "marker": "A",
            "issueId": "short-term-rental-ordinance",
            "issueName": "Short-term rental ordinance",
            "supports": "first reading held, second reading planned",
            "basis": "stated",
            "page": 6
          },
          {
            "heading": "12. Professional services agreement: design of the downtown parking structure",
            "before": "Staff reviewed the scope in Exhibit A. ",
            "highlight": "Motion to approve the professional services agreement for design of the downtown parking structure carried 5–2",
            "after": ", Wards 2 and 6 voting no.",
            "marker": "B",
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "design contract approved, 5–2",
            "basis": "stated",
            "page": 6
          },
          {
            "heading": "14. Water rate study presentation",
            "before": "Staff and the consultant presented findings of the water rate study. ",
            "highlight": "Received and filed.",
            "after": " No action was taken.",
            "marker": "C",
            "issueId": "water-rate-adjustment",
            "issueName": "Water rate adjustment",
            "supports": "study presented, no action",
            "basis": "inferred",
            "page": 6
          },
          {
            "heading": "17. Appointment to the Parks and Recreation Commission",
            "before": "Item heard. No further entry for this item appears in these minutes.",
            "highlight": "",
            "after": "",
            "marker": null,
            "issueId": null,
            "issueName": null,
            "supports": null,
            "basis": null,
            "page": 6
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "agreement for design of the ",
      "highlight": "downtown parking",
      "after": " structure. Motion carried 5–2"
    }
  },
  {
    "id": "parking-study",
    "title": "Parking demand study, downtown core",
    "kind": "Staff report",
    "meetingIds": [
      "2024-03-06"
    ],
    "sourceMeetingDate": "Mar 6, 2024",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "peak occupancy in the ",
      "highlight": "downtown parking",
      "after": " district exceeded 90 percent"
    }
  },
  {
    "id": "parking-citation-fees",
    "title": "Parking citation fee schedule update",
    "kind": "Staff report",
    "meetingIds": [
      "2025-07-02"
    ],
    "sourceMeetingDate": "Jul 2, 2025",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [],
    "issueId": null,
    "basis": null,
    "excerpt": {
      "before": "citation fees for ",
      "highlight": "parking",
      "after": " violations in the downtown zone"
    }
  },
  {
    "id": "design-services-scope",
    "title": "Exhibit A: design services scope",
    "kind": "Attachment",
    "meetingIds": [
      "2026-10-15"
    ],
    "sourceMeetingDate": "Oct 15, 2026",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "a multi-level ",
      "highlight": "parking",
      "after": " structure of approximately 600 spaces"
    }
  },
  {
    "id": "community-meetings-report",
    "title": "Staff report, Jun 19, 2024, p. 2",
    "kind": "Staff report",
    "meetingIds": [],
    "sourceMeetingDate": "Jun 19, 2024",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Staff report, Jun 19, 2024, p. 2",
            "before": "",
            "highlight": "“During spring 2024, staff hosted three community meetings on the proposed structure.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "Staff held three community meetings about the garage.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "“During spring 2024, staff hosted three community meetings on the proposed structure.”",
      "after": ""
    }
  },
  {
    "id": "minutes-2024-06-19",
    "title": "Minutes, Jun 19, 2024, item 14",
    "kind": "Minutes",
    "meetingIds": [],
    "sourceMeetingDate": "Jun 19, 2024",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Minutes, Jun 19, 2024, item 14",
            "before": "",
            "highlight": "“Council directed staff to return with an evaluation of candidate sites.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "Council directed staff to evaluate possible sites.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "“Council directed staff to return with an evaluation of candidate sites.”",
      "after": ""
    }
  },
  {
    "id": "site-evaluation",
    "title": "Staff report, Feb 5, 2025, p. 4",
    "kind": "Staff report",
    "meetingIds": [],
    "sourceMeetingDate": "Feb 5, 2025",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Staff report, Feb 5, 2025, p. 4",
            "before": "",
            "highlight": "“Between November 2024 and January 2025, staff evaluated four candidate sites.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "Staff evaluated four candidate sites. A traffic study ran at about the same time; the records don’t say which finished first.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "“Between November 2024 and January 2025, staff evaluated four candidate sites.”",
      "after": ""
    }
  },
  {
    "id": "minutes-2025-02-05",
    "title": "Minutes, Feb 5, 2025, item 9",
    "kind": "Minutes",
    "meetingIds": [],
    "sourceMeetingDate": "Feb 5, 2025",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Minutes, Feb 5, 2025, item 9",
            "before": "",
            "highlight": "“Site A was identified as the preferred location.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "The city-owned lot east of City Hall was named the preferred site.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "“Site A was identified as the preferred location.”",
      "after": ""
    }
  },
  {
    "id": "adopted-budget",
    "title": "Adopted budget, attachment B",
    "kind": "Adopted budget",
    "meetingIds": [],
    "sourceMeetingDate": "",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Adopted budget, attachment B",
            "before": "",
            "highlight": "“Downtown parking structure: $26.1 million.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "Council set aside money to design and build the garage.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "“Downtown parking structure: $26.1 million.”",
      "after": ""
    }
  },
  {
    "id": "budget-staff-report",
    "title": "Staff report (Aug 20, 2025)",
    "kind": "Staff report (Aug 20",
    "meetingIds": [],
    "sourceMeetingDate": "",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Staff report (Aug 20, 2025)",
            "before": "",
            "highlight": "$24.5 million.",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "Council set aside money to design and build the garage.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "$24.5 million.",
      "after": ""
    }
  },
  {
    "id": "agenda-2026-03-18",
    "title": "Agenda, Mar 18, 2026, consent item 6",
    "kind": "Agenda",
    "meetingIds": [],
    "sourceMeetingDate": "Mar 18, 2026",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Agenda, Mar 18, 2026, consent item 6",
            "before": "",
            "highlight": "“Authorize release of RFP No. 26-014 for design services, civic center area.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "The city asked firms to propose designs for a civic center project.",
            "basis": "inferred",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "inferred",
    "excerpt": {
      "before": "",
      "highlight": "“Authorize release of RFP No. 26-014 for design services, civic center area.”",
      "after": ""
    }
  },
  {
    "id": "design-report",
    "title": "Staff report, Oct 15, 2026, p. 6",
    "kind": "Staff report",
    "meetingIds": [],
    "sourceMeetingDate": "Oct 15, 2026",
    "currentVersionId": "1",
    "pageCount": null,
    "versions": [
      {
        "id": "1",
        "number": 1,
        "publicationDate": null,
        "correctedDate": null,
        "retrievedDate": "",
        "correction": null,
        "originalUrl": null,
        "passages": [
          {
            "heading": "Staff report, Oct 15, 2026, p. 6",
            "before": "",
            "highlight": "“Design is anticipated to be complete in late 2027.”",
            "after": "",
            "marker": null,
            "issueId": "downtown-parking-structure",
            "issueName": "Downtown parking structure",
            "supports": "Design is expected to be finished.",
            "basis": "stated",
            "page": 1
          }
        ]
      }
    ],
    "issueId": "downtown-parking-structure",
    "basis": "stated",
    "excerpt": {
      "before": "",
      "highlight": "“Design is anticipated to be complete in late 2027.”",
      "after": ""
    }
  },
  // The meeting board lists these resources, but supplies no extracted versions.
  // Retain their metadata so local source links have a destination without inventing passages.
  ...[
    { id: 'agenda-2026-10-15', title: 'Agenda · Regular meeting, Oct 15, 2026', kind: 'Agenda' },
    { id: 'transcript-2026-10-15', title: 'Official transcript · Regular meeting, Oct 15, 2026', kind: 'Transcript' },
  ].map(resource => ({ ...resource, meetingIds: ['2026-10-15'], sourceMeetingDate: 'Oct 15, 2026',
    currentVersionId: '', pageCount: null, versions: [], issueId: null, basis: null,
    excerpt: { before: '', highlight: '', after: '' } })),

];

export const home: HomeData = {
  "tries": [
    "parking garage",
    "short-term rentals",
    "my street repaving",
    "water rates"
  ],
  "days": [
    {
      "date": "Oct 15, 2026",
      "meetingId": "2026-10-15",
      "meeting": "Regular meeting",
      "items": [
        {
          "issueId": "downtown-parking-structure",
          "issue": "Downtown parking structure",
          "text": "Council approved the design contract, 5–2.",
          "tag": "Stated in minutes",
          "basis": "stated",
          "status": "documented"
        },
        {
          "issueId": "short-term-rental-ordinance",
          "issue": "Short-term rental ordinance",
          "text": "First reading held. Second reading is planned for Nov 5, 2026.",
          "tag": "Planned · not yet happened",
          "basis": null,
          "status": "planned"
        }
      ]
    },
    {
      "date": "Oct 1, 2026",
      "meetingId": "2026-10-01",
      "meeting": "Regular meeting",
      "items": [
        {
          "issueId": "street-resurfacing-program",
          "issue": "Street resurfacing program",
          "text": "A staff report proposes adding 14 street segments to next year’s list.",
          "tag": "Proposed · no action yet",
          "basis": null,
          "status": "proposed"
        },
        {
          "issueId": "northside-fire-station-replacement",
          "issue": "Northside fire station replacement",
          "text": "The construction bid award was postponed to a later meeting.",
          "tag": "Inferred link · may be incorrect",
          "basis": "inferred",
          "status": "documented"
        }
      ]
    },
    {
      "date": "Sep 17, 2026",
      "meetingId": "2026-09-17",
      "meeting": "Regular meeting",
      "items": [
        {
          "issueId": "park-restroom-renovations",
          "issue": "Park restroom renovations",
          "text": "Listed on the agenda; the available records don’t say what was decided.",
          "tag": "Outcome not in available records",
          "basis": null,
          "status": "unknown"
        }
      ]
    }
  ]
};
