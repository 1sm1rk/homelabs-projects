package de.homelabs.projects.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.homelabs.projects.domain.Comment;
import de.homelabs.projects.domain.CommentService;

/**
 * Comments: plain text on any of the three levels. {@code parent} names the entity —
 * {@code task:HP-12}, {@code project:HP} or a bare reference/code, see the API
 * documentation of the identifier forms.
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Comment create(@Valid @RequestBody CreateCommentRequest request,
            @RequestHeader(value = ApiSupport.SOURCE_HEADER, required = false) String source,
            @AuthenticationPrincipal Jwt jwt) {
        return comments.create(ApiSupport.caller(jwt, source), request.parent(), request.text());
    }

    /** Newest first. */
    @GetMapping
    public List<Comment> list(@RequestParam String parent, @RequestParam(required = false) Integer limit) {
        return comments.list(parent, ApiSupport.clamp(limit, ApiSupport.DEFAULT_ACTIVITY_LIMIT,
                ApiSupport.MAX_ACTIVITY_LIMIT));
    }

    /** Body of {@code POST /api/comments}: {@code parent} plus the comment text. */
    public record CreateCommentRequest(
            @NotBlank String parent,
            @NotBlank @Size(max = 4000) String text) {
    }
}
