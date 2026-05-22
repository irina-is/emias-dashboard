package com.emias.dashboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settings")
public class Settings {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "setting_value")
    private String value;

    public Settings() {}

    public Settings(String key, String value) {
        this.key   = key;
        this.value = value;
    }

    public String getKey()   { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
