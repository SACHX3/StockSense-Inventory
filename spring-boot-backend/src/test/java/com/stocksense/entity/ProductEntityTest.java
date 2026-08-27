package com.stocksense.entity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ProductEntityTest {

    @Test
    void isLowStock_whenQuantityBelowMin_returnsTrue() {
        Product p = new Product();
        p.setQuantity(5);
        p.setMinStockLevel(10);
        assertThat(p.isLowStock()).isTrue();
    }

    @Test
    void isLowStock_whenQuantityAboveMin_returnsFalse() {
        Product p = new Product();
        p.setQuantity(50);
        p.setMinStockLevel(10);
        assertThat(p.isLowStock()).isFalse();
    }

    @Test
    void isLowStock_whenQuantityEqualsMin_returnsTrue() {
        Product p = new Product();
        p.setQuantity(10);
        p.setMinStockLevel(10);
        assertThat(p.isLowStock()).isTrue();
    }

    @Test
    void product_defaultValues() {
        Product p = new Product();
        assertThat(p.getIsActive()).isTrue();
        assertThat(p.getQuantity()).isEqualTo(0);
        assertThat(p.getBuyingPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.getSellingPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.getUnit()).isEqualTo("pcs");
    }

    @Test
    void sale_paymentMethod_enumValues() {
        assertThat(Sale.PaymentMethod.values()).contains(
                Sale.PaymentMethod.CASH,
                Sale.PaymentMethod.CARD,
                Sale.PaymentMethod.BANK_TRANSFER,
                Sale.PaymentMethod.CREDIT
        );
    }

    @Test
    void inventoryLog_movementType_enumValues() {
        assertThat(InventoryLog.MovementType.values()).contains(
                InventoryLog.MovementType.STOCK_IN,
                InventoryLog.MovementType.STOCK_OUT,
                InventoryLog.MovementType.ADJUSTMENT,
                InventoryLog.MovementType.INVOICE_UPDATE
        );
    }
}
