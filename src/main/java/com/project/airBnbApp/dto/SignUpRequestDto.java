package com.project.airBnbApp.dto;

import lombok.Data;

@Data
public class SignUpRequestDto {
    // what is sent when signing up
    private String email;
    private String password;
    private String name;
}