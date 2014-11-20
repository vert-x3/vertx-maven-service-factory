package io.vertx.maven.modules;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class UrlCollectorFilter implements Filter {

  final HashSet<URL> requestedHosts = new HashSet<>();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) servletRequest;
    StringBuffer requestURL = req.getRequestURL();
    URL url = new URL(requestURL.toString());
    requestedHosts.add(new URL("http", url.getHost(), url.getPort(), "/"));
    filterChain.doFilter(servletRequest, servletResponse);
  }

  @Override
  public void destroy() {
  }
}
