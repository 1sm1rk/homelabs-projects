package de.homelabs.projects.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.homelabs.projects.domain.Label;
import de.homelabs.projects.domain.LabelService;

/**
 * The known labels, for discovery of the {@code label} filters. Labels themselves are
 * created on demand at the first assignment — there is no create endpoint.
 */
@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private final LabelService labels;

    public LabelController(LabelService labels) {
        this.labels = labels;
    }

    @GetMapping
    public List<Label> list() {
        return labels.all();
    }
}
