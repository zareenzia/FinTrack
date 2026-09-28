package org.example.finzin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get("user-uploads").toAbsolutePath();
        registry.addResourceHandler("/user-uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");

        // CSS/JS/images are referenced from every HTML page with a build-time content-hash
        // query string (e.g. "/css/style.css?v=3f1a9c2b0e") injected by the `versionStaticAssets`
        // Gradle task (see build.gradle), which only changes when the underlying file's bytes
        // change. That makes it safe to tell browsers/CDNs to cache these responses "forever" —
        // a new deploy that edits a file gets a new query string, which is a brand-new URL as far
        // as HTTP caching is concerned, so it's fetched fresh automatically.
        registry.addResourceHandler("/css/**", "/js/**", "/images/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/", "classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // The HTML documents themselves must always be revalidated with the server (rather than
        // reused from a stale local cache) so that browsers keep picking up the freshly-versioned
        // asset URLs above — and any other markup changes — right after a deploy, without users
        // needing to hard-refresh. "no-cache" still allows the browser to store a copy, it just
        // forces a conditional GET (If-Modified-Since/ETag) before reusing it.
        registry.addResourceHandler("/*.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());
    }
}
