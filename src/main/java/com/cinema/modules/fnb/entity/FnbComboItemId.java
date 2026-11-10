package com.cinema.modules.fnb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class FnbComboItemId implements Serializable {

    @Column(name = "combo_id")
    private Long comboId;

    @Column(name = "item_id")
    private Long itemId;
}
