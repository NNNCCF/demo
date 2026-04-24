package com.ncf.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public UploadResourceConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path baseDir = Paths.get(appProperties.getUpload().getBaseDir()).toAbsolutePath().normalize();
        String publicPath = appProperties.getUpload().getPublicPath();
        String normalizedPublicPath = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
        String pattern = normalizedPublicPath.endsWith("/**")
                ? normalizedPublicPath
                : normalizedPublicPath + "/**";
        String location = baseDir.toUri().toString();

        registry.addResourceHandler(pattern)
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
