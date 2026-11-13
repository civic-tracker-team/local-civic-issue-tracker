package org.civictracker.records.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import org.civictracker.records.web.ReadProblems;
import org.springframework.web.filter.OncePerRequestFilter;

/** Allows local HTTP development and requires HTTPS for remote callers. */
public final class InternalTransportFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    boolean local;
    try {
      String peer = request.getRemoteAddr();
      local = peer != null && !peer.isBlank()
          && InetAddress.getByName(peer).isLoopbackAddress();
    } catch (IOException error) {
      local = false;
    }
    if (!request.isSecure() && !local) {
      ReadProblems.write(response, 403, "HTTPS_REQUIRED");
      return;
    }
    chain.doFilter(request, response);
  }
}
