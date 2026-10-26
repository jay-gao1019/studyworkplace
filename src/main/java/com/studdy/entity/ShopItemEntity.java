package com.studdy.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "shop_items")
public class ShopItemEntity {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 24)
    private String icon;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 'unlimited' 或数字串 */
    @Column(length = 12)
    private String stock = "unlimited";

    protected ShopItemEntity() {}

    public ShopItemEntity(String id, String name, String icon, int price, boolean enabled, String stock) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.price = price;
        this.enabled = enabled;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getIcon() { return icon; }
    public void setIcon(String v) { icon = v; }
    public int getPrice() { return price; }
    public void setPrice(int v) { price = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getStock() { return stock; }
    public void setStock(String v) { stock = v; }
}
