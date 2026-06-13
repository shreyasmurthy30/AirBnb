package com.project.airBnbApp.dto;

import com.project.airBnbApp.entity.HotelContactInfo;
import com.project.airBnbApp.entity.Room;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class HotelDto {
    // Part of HotelInfoDto which is sent back when a hotel is requested by id
    private Long id;
    private String name;
    private String city;
    private String[] photos;
    private String[] amenities;
    private HotelContactInfo contactInfo;
    private Boolean active;
}
