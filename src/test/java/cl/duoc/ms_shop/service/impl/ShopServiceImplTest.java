package cl.duoc.ms_shop.service.impl;

import cl.duoc.ms_shop.client.CurrencyClient;
import cl.duoc.ms_shop.client.InventoryClient;
import cl.duoc.ms_shop.client.ItemClient;
import cl.duoc.ms_shop.dto.ItemFeignDto;
import cl.duoc.ms_shop.dto.PurchaseRequestDto;
import cl.duoc.ms_shop.enums.CurrencyType;
import cl.duoc.ms_shop.model.ShopItem;
import cl.duoc.ms_shop.repository.ShopItemRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    @Mock private ShopItemRepository repository;
    @Mock private CurrencyClient currencyClient;
    @Mock private InventoryClient inventoryClient;
    @Mock private ItemClient itemClient;
    @InjectMocks private ShopServiceImpl service;

    private ShopItem listing() {
        ShopItem s = new ShopItem();
        s.setItemId(10L);
        s.setPrice(50);
        s.setCurrencyType(CurrencyType.GOLD);
        return s;
    }

    private PurchaseRequestDto purchase(int qty) {
        PurchaseRequestDto d = new PurchaseRequestDto();
        d.setUserId(1L);
        d.setShopItemId(5L);
        d.setQuantity(qty);
        return d;
    }

    private ItemFeignDto item() {
        ItemFeignDto i = new ItemFeignDto();
        i.setName("Espada");
        return i;
    }

    @Test
    void compraExitosa_descuentaMonedaYEntregaItem() {
        when(repository.findById(5L)).thenReturn(Optional.of(listing()));
        when(itemClient.getItemById(10L)).thenReturn(item());
        when(currencyClient.deductCurrency(eq(1L), any())).thenReturn("descontado");

        String res = service.purchaseItem(purchase(2));

        verify(currencyClient).deductCurrency(eq(1L), any());
        verify(inventoryClient).addItem(any());
        verify(currencyClient, never()).addCurrency(anyLong(), any());
        assertThat(res).contains("Espada");
    }

    @Test
    void compra_cantidadInvalida_lanzaIllegalArgument() {
        assertThatThrownBy(() -> service.purchaseItem(purchase(0)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(currencyClient, inventoryClient, repository, itemClient);
    }

    @Test
    void compra_fallaEntrega_reembolsaLaMoneda() {
        when(repository.findById(5L)).thenReturn(Optional.of(listing()));
        when(itemClient.getItemById(10L)).thenReturn(item());
        when(currencyClient.deductCurrency(eq(1L), any())).thenReturn("descontado");
        doThrow(mock(FeignException.class)).when(inventoryClient).addItem(any());

        assertThatThrownBy(() -> service.purchaseItem(purchase(1)))
                .isInstanceOf(RuntimeException.class);

        verify(currencyClient).addCurrency(eq(1L), any());
    }

    @Test
    void compra_listingNoExiste_lanza404() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseItem(purchase(1)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
