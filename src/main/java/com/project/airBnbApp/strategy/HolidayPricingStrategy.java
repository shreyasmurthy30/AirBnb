package com.project.airBnbApp.strategy;

import com.project.airBnbApp.entity.Inventory;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;

@RequiredArgsConstructor
public class HolidayPricingStrategy implements PricingStrategy{

    private final PricingStrategy wrapped;

    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        BigDecimal price = wrapped.calculatePrice(inventory);
        boolean isHoliday = isHolidayDate(inventory.getDate());
        if (isHoliday) {
            price = price.multiply(BigDecimal.valueOf(1.25));
        }
        return price;
    }

    private boolean isHolidayDate(java.time.LocalDate date) {
        return false;
    }
}
