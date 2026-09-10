package com.rrsistemas.erauma.story;

import com.rrsistemas.erauma.child.ChildProfile;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_character")
public class StoryCharacter {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "child_profile_id") private ChildProfile character;
    @Column(name = "selection_order") private int selectionOrder;
    @Enumerated(EnumType.STRING) private StoryCharacterRole role;
    @Column(name = "visual_description") private String visualDescription;
    @Column(name = "created_at") private Instant createdAt;

    protected StoryCharacter() {}

    public StoryCharacter(Story story, ChildProfile character, int selectionOrder, String visualDescription) {
        this.id = UUID.randomUUID();
        this.story = story;
        this.character = character;
        this.selectionOrder = selectionOrder;
        this.role = selectionOrder == 1 ? StoryCharacterRole.PROTAGONIST : StoryCharacterRole.SECONDARY;
        this.visualDescription = visualDescription;
        this.createdAt = Instant.now();
    }

    public ChildProfile getCharacter() { return character; }
    public int getSelectionOrder() { return selectionOrder; }
    public StoryCharacterRole getRole() { return role; }
    public String getVisualDescription() { return visualDescription; }
}
