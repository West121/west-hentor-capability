package com.sgs.capability.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Original GraphQL Playground entry point. */
@RestController
public class GraphqlPlaygroundController {
    @GetMapping(value = "/ui/playground", produces = MediaType.TEXT_HTML_VALUE)
    public String playground() {
        return """
                <!doctype html>
                <html>
                <head>
                    <title>GraphQL Playground</title>
                    <meta charset="utf-8">
                </head>
                <body>
                    <main id="root">GraphQL Playground</main>
                    <script>
                        window.graphQLEndpoint = "/graphql";
                    </script>
                </body>
                </html>
                """;
    }
}
