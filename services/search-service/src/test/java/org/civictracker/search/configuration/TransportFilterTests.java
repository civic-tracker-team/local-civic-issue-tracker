package org.civictracker.search.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TransportFilterTests {
  @Test
  void remotePlainHttpIsRefusedEvenWithForwardingHeaders() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/search");
    request.setRemoteAddr("203.0.113.7");
    request.addHeader("X-Forwarded-Proto", "https");
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new TransportFilter().doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    assertEquals(null, chain.getRequest());
  }

  @Test
  void loopbackHttpAndRemoteHttpsPass() throws Exception {
    for (var secure : new boolean[] {false, true}) {
      var request = new MockHttpServletRequest("GET", "/api/v1/search");
      request.setRemoteAddr(secure ? "203.0.113.7" : "127.0.0.1");
      request.setSecure(secure);
      var chain = new MockFilterChain();
      new TransportFilter().doFilter(request, new MockHttpServletResponse(), chain);
      assertEquals(request, chain.getRequest());
    }
  }
}
