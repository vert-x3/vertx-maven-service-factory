package io.vertx.maven.modules;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AuthFilter implements Filter {

  public static AuthFilter proxyAuthenticator(String username, String password) {
    return new AuthFilter(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED, "Proxy-Authenticate", "Proxy-Authorization", username, password);
  }

  public static AuthFilter serverAuthenticator(String username, String password) {
    return new AuthFilter(HttpServletResponse.SC_UNAUTHORIZED, "WWW-Authenticate", "Authorization", username, password);
  }

  final int authenticationRequiredStatus;
  final String authenticateHeader;
  final String authorizationHeader;
  final String username;
  final String password;
  final AtomicBoolean authenticated;

  private AuthFilter(int authenticationRequiredStatus, String authenticateHeader, String authorizationHeader, String username, String password) {
    this.authenticationRequiredStatus = authenticationRequiredStatus;
    this.authenticateHeader = authenticateHeader;
    this.authorizationHeader = authorizationHeader;
    this.username = username;
    this.password = password;
    authenticated = new AtomicBoolean();
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }
  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) servletRequest;
    String authz = req.getHeader(authorizationHeader);
    if (authz != null && authz.startsWith("Basic ")) {
      String secret = authz.substring(6);
      String up = new String(java.util.Base64.getDecoder().decode(secret));
      int index = up.indexOf(':');
      String username = up.substring(0, index);
      String password = up.substring(index + 1);
      if (username.equals(this.username) && password.equals(this.password)) {
        authenticated.set(true);
        filterChain.doFilter(servletRequest, servletResponse);
        return;
      }
    }
    HttpServletResponse resp = (HttpServletResponse) servletResponse;
    resp.addHeader(authenticateHeader, "Basic realm=\"Jetty Authorization\"");
    resp.setStatus(authenticationRequiredStatus);
  }
  @Override
  public void destroy() {
  }
}
