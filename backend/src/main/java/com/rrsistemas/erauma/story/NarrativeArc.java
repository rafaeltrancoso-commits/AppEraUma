package com.rrsistemas.erauma.story;

public record NarrativeArc(String setup, String centralSituation, String protagonistAction, String resolution, String closingScene) {
    public NarrativeArc(String setup, String centralSituation, String resolution) {
        this(setup, centralSituation, "O protagonista participa ativamente da solucao.", resolution, "A historia termina com uma cena concreta e acolhedora.");
    }
}
