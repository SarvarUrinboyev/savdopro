package uz.barakat.market.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

/**
 * Serves the React SPA's index.html for every client-side route so that a
 * hard refresh or direct URL navigation still loads the app.
 *
 * WHY bytes instead of "forward:/index.html":
 *   Spring Security 6 re-applies the entire security filter chain to
 *   FORWARD dispatches. This creates deeply-nested HttpServletRequestWrapper
 *   stacks that overflow when SessionManagementFilter calls getSession().
 *   Reading the resource bytes and writing them directly avoids the forward
 *   dispatch entirely — no re-entry into the security chain, no recursion.
 *
 * Excluded from matching (handled by their own machinery):
 *   /api/**         → REST controllers
 *   /error          → Spring Boot BasicErrorController
 *   /assets/**      → Vite static bundle
 *   /favicon.ico, /icon.svg, /index.html → static resources
 */
@Controller
public class SpaController {

    @Value("classpath:static/index.html")
    private Resource indexHtml;

    @GetMapping(
            value = {
                // Single-segment SPA routes: /login /dashboard /reports …
                // `ws` is excluded so /ws (and /ws-sockjs) reach the WebSocket
                // handler instead of being shadowed by index.html.
                "/{path:^(?!api|error|assets|ws|favicon\\.ico|icon\\.svg|index\\.html).*$}",
                // Multi-segment SPA routes: /warehouse/details/123 …
                "/{path:^(?!api|error|assets|ws).*$}/**"
            },
            produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> spa() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(indexHtml.getContentAsByteArray());
    }
}
