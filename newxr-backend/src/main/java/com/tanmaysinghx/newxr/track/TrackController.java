package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.track.TrackDtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/track")
public class TrackController {

    private final TrackService track;
    private final CurrentUser current;

    public TrackController(TrackService track, CurrentUser current) {
        this.track = track;
        this.current = current;
    }

    @GetMapping("/projects")
    public List<ProjectView> projects() { return track.listProjects(); }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView createProject(@RequestBody CreateProject body) { return track.createProject(body, current.require()); }

    @GetMapping("/projects/{key}")
    public ProjectView project(@PathVariable String key) { return track.getProject(key); }

    @PutMapping("/projects/{key}")
    public ProjectView updateProject(@PathVariable String key, @RequestBody CreateProject body) { return track.updateProject(key, body); }

    @GetMapping("/projects/{key}/board")
    public BoardView board(@PathVariable String key, @RequestParam(required = false) String q, @RequestParam(required = false) Long assignee,
                           @RequestParam(required = false) String type, @RequestParam(required = false) String label,
                           @RequestParam(required = false) String epic) {
        return track.board(key, q, assignee, type, label, epic);
    }

    @GetMapping("/projects/{key}/backlog")
    public BacklogView backlog(@PathVariable String key, @RequestParam(required = false) String q, @RequestParam(required = false) Long assignee,
                               @RequestParam(required = false) String type, @RequestParam(required = false) String epic) {
        return track.backlog(key, q, assignee, type, epic);
    }

    @GetMapping("/projects/{key}/issues")
    public List<IssueRow> issues(@PathVariable String key, @RequestParam(required = false) String q, @RequestParam(required = false) Long assignee,
                                 @RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String label, @RequestParam(required = false) String epic,
                                 @RequestParam(required = false) String sort) {
        return track.list(key, q, assignee, type, status, label, epic, sort);
    }

    @GetMapping("/projects/{key}/timeline")
    public List<EpicView> timeline(@PathVariable String key) { return track.timeline(key); }

    @GetMapping("/projects/{key}/report")
    public Report report(@PathVariable String key, @RequestParam(required = false) Long sprint) { return track.report(key, sprint); }

    @GetMapping("/projects/{key}/sprints")
    public List<SprintView> sprints(@PathVariable String key) { return track.listSprints(key); }

    @PostMapping("/projects/{key}/sprints")
    public SprintView createSprint(@PathVariable String key, @RequestBody CreateSprint body) { return track.createSprint(key, body); }

    @PostMapping("/sprints/{id}/start")
    public SprintView start(@PathVariable Long id, @RequestBody(required = false) StartSprint body) { return track.startSprint(id, body, current.require()); }

    @PostMapping("/sprints/{id}/complete")
    public SprintView complete(@PathVariable Long id, @RequestBody(required = false) CompleteSprint body) { return track.completeSprint(id, body, current.require()); }

    @DeleteMapping("/sprints/{id}")
    public void deleteSprint(@PathVariable Long id) { track.deleteSprint(id); }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueDetail create(@RequestBody CreateIssue body) { return track.createIssue(body, current.require()); }

    @GetMapping("/issues/{key}")
    public IssueDetail issue(@PathVariable String key) { return track.getIssue(key); }

    @PatchMapping("/issues/{key}")
    public IssueDetail update(@PathVariable String key, @RequestBody UpdateIssue body) { return track.updateIssue(key, body, current.require()); }

    @PostMapping("/issues/{key}/move")
    public IssueRow move(@PathVariable String key, @RequestBody MoveIssue body) { return track.move(key, body, current.require()); }

    @DeleteMapping("/issues/{key}")
    public void delete(@PathVariable String key) { track.deleteIssue(key, current.require()); }

    @PostMapping("/issues/{key}/comments")
    public CommentView comment(@PathVariable String key, @RequestBody CommentRequest body) { return track.addComment(key, body.body(), current.require()); }

    @PutMapping("/comments/{id}")
    public CommentView editComment(@PathVariable Long id, @RequestBody CommentRequest body) { return track.editComment(id, body.body(), current.require()); }

    @DeleteMapping("/comments/{id}")
    public void deleteComment(@PathVariable Long id) { track.deleteComment(id, current.require()); }
}
