package com.emias.dashboard.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "mo_tasks")
public class MoTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority; // URGENT, HIGH, NORMAL

    @Column(name = "status", nullable = false, length = 20)
    private String status;   // IN_PROGRESS, DONE, OVERDUE

    @Column(name = "responsible", length = 200)
    private String responsible;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "tag", length = 100)
    private String tag;

    public MoTask() {}

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponsible() { return responsible; }
    public void setResponsible(String responsible) { this.responsible = responsible; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    // Удобные геттеры для шаблона
    public String getPriorityLabel() {
        return switch (priority) {
            case "URGENT" -> "🔴 Срочно";
            case "HIGH"   -> "🟡 Высокий";
            default       -> "🟢 Обычный";
        };
    }
    public String getPriorityColor() {
        return switch (priority) {
            case "URGENT" -> "#f87171";
            case "HIGH"   -> "#fcd34d";
            default       -> "#4ade80";
        };
    }
    public String getPriorityBg() {
        return switch (priority) {
            case "URGENT" -> "rgba(239,68,68,.15)";
            case "HIGH"   -> "rgba(252,211,77,.12)";
            default       -> "rgba(74,222,128,.1)";
        };
    }
    public String getPriorityBorder() {
        return switch (priority) {
            case "URGENT" -> "rgba(239,68,68,.3)";
            case "HIGH"   -> "rgba(252,211,77,.25)";
            default       -> "rgba(74,222,128,.2)";
        };
    }
    public String getStatusLabel() {
        return switch (status) {
            case "DONE"    -> "Выполнено";
            case "OVERDUE" -> "Просрочено";
            default        -> "В работе";
        };
    }
    public String getStatusColor() {
        return switch (status) {
            case "DONE"    -> "#4ade80";
            case "OVERDUE" -> "#f87171";
            default        -> "#fcd34d";
        };
    }
    public String getStatusBg() {
        return switch (status) {
            case "DONE"    -> "rgba(74,222,128,.1)";
            case "OVERDUE" -> "rgba(239,68,68,.1)";
            default        -> "rgba(252,211,77,.1)";
        };
    }
    public String getStatusBorder() {
        return switch (status) {
            case "DONE"    -> "rgba(74,222,128,.25)";
            case "OVERDUE" -> "rgba(239,68,68,.25)";
            default        -> "rgba(252,211,77,.25)";
        };
    }
}
