package com.example.channelbot.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity(name="game")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String Game_name;
    private String Genre;
    private String Description;
    private String link;
    @Column(name = "bytes", columnDefinition = "longblob")
    private byte[] photo;
}
