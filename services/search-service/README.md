# Search service

Java package: `org.civictracker.search`.

Provides search over published issues, individual records and meetings. Obtains source and derived data through records and analysis APIs. Michael leads detailed search design; matching, ranking and any search-specific persistence remain open.

The Spring Boot entry point starts the HTTP baseline, with a real-server startup test. Search behavior, indexing and owner API clients are not implemented. No search engine or endpoint contract is selected here.
