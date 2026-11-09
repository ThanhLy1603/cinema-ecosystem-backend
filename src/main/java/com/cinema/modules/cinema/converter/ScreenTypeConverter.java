package com.cinema.modules.cinema.converter;

import com.cinema.modules.cinema.enums.ScreenType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ScreenTypeConverter implements AttributeConverter<ScreenType, String> {

    @Override
    public String convertToDatabaseColumn(ScreenType screenType) {
        return screenType == null ? null : screenType.getCode();
    }

    @Override
    public ScreenType convertToEntityAttribute(String code) {
        return code == null ? null : ScreenType.fromCode(code);
    }
}
