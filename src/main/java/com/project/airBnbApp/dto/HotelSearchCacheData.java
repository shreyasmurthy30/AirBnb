package com.project.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotelSearchCacheData {
    private List<HotelPriceDto> content;
    private long totalElements;
    private int pageNumber;
    private int pageSize;

    public HotelSearchCacheData(Page<HotelPriceDto> page) {
        this.content = new ArrayList<>(page.getContent());
        this.totalElements = page.getTotalElements();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
    }
}
