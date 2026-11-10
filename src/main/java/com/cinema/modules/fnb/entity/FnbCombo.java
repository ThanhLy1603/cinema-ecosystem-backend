package com.cinema.modules.fnb.entity;

import com.cinema.common.entity.SoftDeleteEntity;
import com.cinema.modules.fnb.enums.FnbStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fnb_combos")
public class FnbCombo extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private FnbStatus status = FnbStatus.AVAILABLE;

    @OneToMany(mappedBy = "fnbCombo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FnbComboItem> fnbComboItems = new ArrayList<>();

    public void replaceItems(Map<Long, FnbItem> fnbItemsById, Map<Long, Integer> quantitiesByItemId) {
        fnbComboItems.removeIf(fnbComboItem -> !quantitiesByItemId.containsKey(fnbComboItem.getFnbItem().getId()));
        for (FnbComboItem fnbComboItem : fnbComboItems) {
            fnbComboItem.setQuantity(quantitiesByItemId.get(fnbComboItem.getFnbItem().getId()));
        }
        for (Map.Entry<Long, Integer> quantityEntry : quantitiesByItemId.entrySet()) {
            boolean alreadyPresent = fnbComboItems.stream()
                    .anyMatch(fnbComboItem -> fnbComboItem.getFnbItem().getId().equals(quantityEntry.getKey()));
            if (!alreadyPresent) {
                fnbComboItems.add(new FnbComboItem(this, fnbItemsById.get(quantityEntry.getKey()), quantityEntry.getValue()));
            }
        }
    }
}
