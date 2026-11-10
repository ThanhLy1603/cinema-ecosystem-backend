package com.cinema.modules.fnb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fnb_combo_items")
public class FnbComboItem {

    @EmbeddedId
    private FnbComboItemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("comboId")
    @JoinColumn(name = "combo_id")
    private FnbCombo fnbCombo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("itemId")
    @JoinColumn(name = "item_id")
    private FnbItem fnbItem;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public FnbComboItem(FnbCombo fnbCombo, FnbItem fnbItem, int quantity) {
        this.id = new FnbComboItemId(fnbCombo.getId(), fnbItem.getId());
        this.fnbCombo = fnbCombo;
        this.fnbItem = fnbItem;
        this.quantity = quantity;
    }
}
