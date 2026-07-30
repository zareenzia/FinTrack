package org.example.finzin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "todo_items")
public class TodoItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** References TodoListEntity.id. Set on sub-items too (copied from the parent at creation, never
     *  changes) so "all items in list X, top-level or not" is always a single findByListId query. */
    @Column(nullable = false)
    private Long listId;

    /** References TodoItemEntity.id — no JPA FK, per app convention. Null = top-level item.
     *  Non-null = sub-item ("step"); enforced one-level-only in TodoService, not schema. */
    @Column(nullable = true)
    private Long parentItemId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Boolean completed = false;

    /** The star/favorite toggle — direct replacement for the old flat TodoEntity's "pinned" flag. */
    @Column(nullable = false)
    private Boolean important = false;

    @Column(nullable = true)
    private LocalDate dueDate;

    /** Manual drag-and-drop order within this item's bucket: among its list's top-level items if
     *  parentItemId is null, or among its parent's sub-items otherwise. Null = never manually reordered. */
    @Column(nullable = true)
    private Integer boardPosition;

    @Column(nullable = true)
    private LocalDateTime completedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (completed == null) completed = false;
        if (important == null) important = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public Long getParentItemId() { return parentItemId; }
    public void setParentItemId(Long parentItemId) { this.parentItemId = parentItemId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public Boolean getImportant() { return important; }
    public void setImportant(Boolean important) { this.important = important; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Integer getBoardPosition() { return boardPosition; }
    public void setBoardPosition(Integer boardPosition) { this.boardPosition = boardPosition; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
