package org.civictracker.records.portal.primegov;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.AcquisitionData.Profile;
import org.civictracker.records.storage.OriginalFileStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fetches bounded original bytes without internal service credentials. */
@Component
public class PortalHttp implements DisposableBean {
  public record Fetched(
      URI source,
      URI resolved,
      Instant observedAt,
      String contentType,
      OriginalFileStore.Staged staged)
      implements AutoCloseable {
    @Override
    public void close() throws IOException {
      staged.close();
    }
  }

  private final OriginalFileStore files;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Map<Profile, HttpClient> clients = new ConcurrentHashMap<>();
  private final ScheduledExecutorService deadlines =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("portal-deadlines").factory());

  public PortalHttp(
      OriginalFileStore files, JdbcTemplate jdbc, PlatformTransactionManager manager) {
    this.files = files;
    this.jdbc = jdbc;
    transactions = new TransactionTemplate(manager);
  }

  public Fetched fetch(Profile profile, URI source) throws IOException, InterruptedException {
    URI target = source;
    HttpClient client =
        clients.computeIfAbsent(
            profile,
            p ->
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(p.connectTimeoutMillis()))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build());
    for (int redirects = 0; redirects <= profile.maxRedirects(); redirects++) {
      validate(profile, target);
      pace(profile.baseUrl().getHost(), profile.requestIntervalMillis());
      Instant observed = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      long start = System.nanoTime();
      HttpRequest request =
          HttpRequest.newBuilder(target)
              .timeout(Duration.ofMillis(profile.transferTimeoutMillis()))
              .header("User-Agent", "LocalCivicIssueTracker/0.1")
              .header("Accept-Encoding", "identity")
              .GET()
              .build();
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        int status = response.statusCode();
        if (status == 429) {
          postpone(
              profile.baseUrl().getHost(), response.headers().firstValue("Retry-After").orElse(""));
          throw new OperationProblem(503, "SOURCE_RATE_LIMITED");
        }
        if (status >= 300 && status < 400) {
          String location =
              response
                  .headers()
                  .firstValue("Location")
                  .orElseThrow(() -> new OperationProblem(503, "INVALID_SOURCE_REDIRECT"));
          target = target.resolve(location);
          continue;
        }
        if (status != 200) {
          throw new OperationProblem(503, "SOURCE_HTTP_FAILURE");
        }
        String encoding = response.headers().firstValue("Content-Encoding").orElse("identity");
        if (!encoding.equalsIgnoreCase("identity")) {
          throw new OperationProblem(503, "UNSUPPORTED_TRANSFER_ENCODING");
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (length > profile.maxBytes()) {
          throw new OperationProblem(503, "SOURCE_TOO_LARGE");
        }
        var expired = new AtomicBoolean();
        long remaining =
            profile.transferTimeoutMillis()
                - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        var deadline =
            deadlines.schedule(
                () -> {
                  expired.set(true);
                  try {
                    body.close();
                  } catch (IOException ignored) {
                    // The reader reports the failed transfer.
                  }
                },
                Math.max(0, remaining),
                TimeUnit.MILLISECONDS);
        try {
          var staged =
              files.stage(
                  new FilterInputStream(body) {
                    private long count;

                    @Override
                    public int read(byte[] buffer, int offset, int size) throws IOException {
                      int read = super.read(buffer, offset, size);
                      if (expired.get()) {
                        throw new IOException("Portal body deadline expired");
                      }
                      if (read > 0) {
                        count += read;
                        if (count > profile.maxBytes()) {
                          throw new IOException("Portal body exceeds its configured bound");
                        }
                      }
                      if (read == -1 && length >= 0 && count != length) {
                        throw new IOException("Incomplete portal body");
                      }
                      return read;
                    }
                  });
          if (expired.get() || staged.byteLength() == 0) {
            staged.close();
            throw new IOException("Empty or expired portal body");
          }
          return new Fetched(
              source,
              target,
              observed,
              response.headers().firstValue("Content-Type").orElse(""),
              staged);
        } finally {
          deadline.cancel(false);
        }
      }
    }
    throw new OperationProblem(503, "TOO_MANY_SOURCE_REDIRECTS");
  }

  public void validate(Profile profile, URI uri) throws IOException {
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || !profile.allowedHosts().contains(uri.getHost())
        || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))) {
      throw new OperationProblem(503, "UNAPPROVED_SOURCE_DESTINATION");
    }
    for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
      boolean loopback = address.isLoopbackAddress();
      boolean uniqueLocal =
          address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc;
      if (address.isMulticastAddress() || uniqueLocal) {
        throw new OperationProblem(503, "UNAPPROVED_SOURCE_DESTINATION");
      }
      if (!(loopback && profile.allowLoopback())
          && (address.isAnyLocalAddress()
              || address.isLinkLocalAddress()
              || address.isSiteLocalAddress()
              || loopback)) {
        throw new OperationProblem(503, "UNAPPROVED_SOURCE_DESTINATION");
      }
      if (uri.getPort() != -1
          && uri.getPort() != 443
          && !(loopback
              && profile.allowLoopback()
              && uri.getPort() == profile.baseUrl().getPort())) {
        throw new OperationProblem(503, "UNAPPROVED_SOURCE_DESTINATION");
      }
      if (!uri.getScheme().equals("https") && !(loopback && profile.allowLoopback())) {
        throw new OperationProblem(503, "SOURCE_HTTPS_REQUIRED");
      }
    }
  }

  private void pace(String host, long interval) throws InterruptedException {
    Instant allowed =
        transactions.execute(status -> {
          jdbc.update(
              "insert into records.portal_request_gate(host,next_allowed_at) values (?,now()) "
                  + "on conflict do nothing",
              host);
          Instant previous =
              jdbc.queryForObject(
                  "select next_allowed_at from records.portal_request_gate where host=? for update",
                  (row, n) -> row.getTimestamp(1).toInstant(),
                  host);
          Instant next = previous.isAfter(Instant.now()) ? previous : Instant.now();
          jdbc.update(
              "update records.portal_request_gate set next_allowed_at=? where host=?",
              java.sql.Timestamp.from(next.plusMillis(interval)),
              host);
          return next;
        });
    long delay = Duration.between(Instant.now(), allowed).toMillis();
    if (delay > 0) {
      Thread.sleep(delay);
    }
  }

  private void postpone(String host, String header) {
    try {
      Instant next;
      try {
        next = Instant.now().plusSeconds(Long.parseLong(header));
      } catch (NumberFormatException ignored) {
        next = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
      }
      jdbc.update(
          "update records.portal_request_gate "
              + "set next_allowed_at=greatest(next_allowed_at,?) where host=?",
          java.sql.Timestamp.from(next),
          host);
    } catch (RuntimeException ignored) {
      // Invalid Retry-After does not turn the failed request into success.
    }
  }

  @Override
  public void destroy() {
    deadlines.shutdownNow();
    clients.values().forEach(HttpClient::shutdownNow);
  }
}
