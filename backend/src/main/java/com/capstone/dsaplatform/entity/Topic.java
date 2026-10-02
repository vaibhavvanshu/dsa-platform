package com.capstone.dsaplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "topics")
public class Topic {

    // Not generated: 1..10 is the fixed topic order and the HLR one-hot index (id - 1).
    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    // Mapped as a plain join table (no entity) because an edge has no data of its own
    // and the graph is seed data the app only reads.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "topic_prerequisites",
            joinColumns = @JoinColumn(name = "topic_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_id"))
    private Set<Topic> prerequisites = new HashSet<>();

    protected Topic() {
        // Required by JPA.
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Set<Topic> getPrerequisites() { return prerequisites; }
}
