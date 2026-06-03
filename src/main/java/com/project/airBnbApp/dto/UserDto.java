package com.project.airBnbApp.dto;

import lombok.Data;

@Data
public class UserDto {
    // returned after signup/login
    private Long id;
    private String email;
    private String name;
}
