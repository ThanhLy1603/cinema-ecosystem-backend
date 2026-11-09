package com.cinema.modules.cinema.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ScreenType {
    SCREEN_2D("2D"),
    SCREEN_3D("3D"),
    IMAX("IMAX"),
    SCREEN_4DX("4DX");

    private final String code;

    ScreenType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ScreenType fromCode(String code) {
        return Arrays.stream(values())
                .filter(screenType -> screenType.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại màn hình không hợp lệ: " + code));
    }
}
