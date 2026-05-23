package com.project.airBnbApp.entity;

import com.project.airBnbApp.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Entity
@Getter
@Setter
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private Integer age;

    // not req
//    @ManyToMany(mappedBy = "guests") // no join table else a new table of same will be created and causes exception. Rule is only owner must have the join table
//    private Set<Booking> bookings;






}
