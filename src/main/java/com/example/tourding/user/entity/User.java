package com.example.tourding.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor

public class User {

    @GeneratedValue
    @Id
    private int id;
    private String name;
    private String password;
    private String email;
}
