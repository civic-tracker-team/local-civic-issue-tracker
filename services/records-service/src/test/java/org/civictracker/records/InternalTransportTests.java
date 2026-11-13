package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.civictracker.records.configuration.InternalTransportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalTransportTests {
  @Test
  void remotePlainHttpIsRejectedEvenWithSpoofedForwardingHeaders() throws Exception {
    var request = new MockHttpServletRequest("GET", "/internal/v1/documents/id");
    request.setRemoteAddr("192.0.2.1");
    request.addHeader("X-Forwarded-Proto", "https");
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new InternalTransportFilter().doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("HTTPS_REQUIRED"));
    assertEquals(null, chain.getRequest());
  }

  @Test
  void remoteHttpsCanProceedToAuthentication() throws Exception {
    var request = new MockHttpServletRequest("GET", "/internal/v1/documents/id");
    request.setRemoteAddr("192.0.2.1");
    request.setSecure(true);
    var chain = new MockFilterChain();
    new InternalTransportFilter().doFilter(request, new MockHttpServletResponse(), chain);
    assertEquals(request, chain.getRequest());
  }

  @Test
  void missingPeerInformationDoesNotGrantTheLoopbackException() throws Exception {
    for (String peer : new String[] {null, ""}) {
      var request = new MockHttpServletRequest("GET", "/internal/v1/documents/id");
      request.setRemoteAddr(peer);
      var response = new MockHttpServletResponse();
      new InternalTransportFilter().doFilter(request, response, new MockFilterChain());
      assertEquals(403, response.getStatus());
    }
  }

  @Test
  void loopbackIpv4AndIpv6AllowLocalHttp() throws Exception {
    for (String address : new String[] {"127.0.0.1", "::1"}) {
      var request = new MockHttpServletRequest("GET", "/internal/v1/documents/id");
      request.setRemoteAddr(address);
      var chain = new MockFilterChain();
      new InternalTransportFilter().doFilter(request, new MockHttpServletResponse(), chain);
      assertEquals(request, chain.getRequest());
    }
  }
}
