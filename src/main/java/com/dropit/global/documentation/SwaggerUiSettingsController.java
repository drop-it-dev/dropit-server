package com.dropit.global.documentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerUiSettingsController {

    private final boolean readOnly;

    public SwaggerUiSettingsController(@Value("${docs.swagger-ui.read-only:false}") boolean readOnly) {
        this.readOnly = readOnly;
    }

    @GetMapping(value = "/swagger-ui/settings.js", produces = "application/javascript")
    public String settings() {
        return "window.swaggerUiConfig = " + (readOnly ? "{ supportedSubmitMethods: [] }" : "{}") + ";";
    }
}
