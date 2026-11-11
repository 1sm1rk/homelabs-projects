package de.homelabs.projects.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The service's micro frontend, described for the app that mounts it: which custom
 * element, which module (relative to {@code /mfe/}) and how its menu entry reads. The
 * descriptor carries no roles — who may open it is the app's decision.
 */
@RestController
public class MfeDescriptorController {

    private final String version;

    public MfeDescriptorController(ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        this.version = properties == null ? "dev" : properties.getVersion();
    }

    @GetMapping("/mfe/descriptor")
    ResponseEntity<Descriptor> descriptor() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new Descriptor("projects", version,
                "hl-projects", "projects.js", new Nav(new Label("Projekte", "Projects"), "folder", 10)));
    }

    record Descriptor(
            @JsonProperty("id") String id,
            @JsonProperty("version") String version,
            @JsonProperty("element") String element,
            @JsonProperty("entry") String entry,
            @JsonProperty("nav") Nav nav) {
    }

    record Nav(@JsonProperty("label") Label label, @JsonProperty("icon") String icon, @JsonProperty("order") int order) {
    }

    record Label(@JsonProperty("de") String de, @JsonProperty("en") String en) {
    }
}
