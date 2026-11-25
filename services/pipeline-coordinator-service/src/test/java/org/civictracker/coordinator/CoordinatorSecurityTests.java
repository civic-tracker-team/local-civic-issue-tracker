package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.civictracker.coordinator.config.CoordinatorSecurity;
import org.civictracker.coordinator.config.InternalTransportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CoordinatorSecurityTests {
  @Test
  void serviceIdentitiesNeedBcryptAndDistinctAuthorityGroups() {
    String hash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("synthetic");
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoordinatorSecurity.Credentials(Map.of("maintainer", "plaintext"), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoordinatorSecurity.Credentials(Map.of("same", hash), Map.of("same", hash)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CoordinatorSecurity.Credentials(Map.of("bad:name", hash), Map.of()));
    assertTrue(new CoordinatorSecurity.Credentials(null, null).maintainers().isEmpty());
  }

  @Test
  void cleartextIsAllowedOnlyForLoopbackPeersRegardlessOfForwardedHeaders() throws Exception {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.1");
    request.addHeader("X-Forwarded-For", "127.0.0.1");
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new InternalTransportFilter().doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    assertNull(chain.getRequest());
    assertTrue(response.getContentAsString().contains("HTTPS_REQUIRED"));
    for (String peer : java.util.List.of("127.0.0.1", "::1")) {
      var local = new MockHttpServletRequest();
      local.setRemoteAddr(peer);
      var localChain = new MockFilterChain();
      new InternalTransportFilter().doFilter(local, new MockHttpServletResponse(), localChain);
      assertNotNull(localChain.getRequest());
    }
  }

  @Test
  void remoteHttpsPassesTheTransportCheckAndUnknownPeersFailClosed() throws Exception {
    var secure = new MockHttpServletRequest();
    secure.setRemoteAddr("198.51.100.1");
    secure.setSecure(true);
    var chain = new MockFilterChain();
    new InternalTransportFilter().doFilter(secure, new MockHttpServletResponse(), chain);
    assertNotNull(chain.getRequest());
    var unknown = new MockHttpServletRequest();
    unknown.setRemoteAddr("");
    var response = new MockHttpServletResponse();
    new InternalTransportFilter().doFilter(unknown, response, new MockFilterChain());
    assertEquals(403, response.getStatus());
  }
}
