package cl.duoc.ms_shop.service.impl;

import cl.duoc.ms_shop.client.CurrencyClient;
import cl.duoc.ms_shop.client.InventoryClient;
import cl.duoc.ms_shop.client.ItemClient;
import cl.duoc.ms_shop.dto.*;
import cl.duoc.ms_shop.model.ShopItem;
import cl.duoc.ms_shop.repository.ShopItemRepository;
import cl.duoc.ms_shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopItemRepository repository;
    private final CurrencyClient currencyClient;
    private final InventoryClient inventoryClient;
    private final ItemClient itemClient;

    @Override
    public String createShopListing(ShopItemRequestDto dto) {
        ShopItem listing = new ShopItem();
        listing.setItemId(dto.getItemId());
        listing.setPrice(dto.getPrice());
        listing.setCurrencyType(dto.getCurrencyType());

        repository.save(listing);
        return "Item añadido a tienda";
    }

    @Override
    public String purchaseItem(PurchaseRequestDto dto) {

        ShopItem listing = repository.findById(dto.getShopItemId())
                .orElseThrow(() -> new RuntimeException("El item no existe en la tienda."));

        String itemName;
        try {
            ItemFeignDto item = itemClient.getItemById(listing.getItemId());
            itemName = item.getName();
        } catch (Exception e) {
            throw new RuntimeException("Error: No se pudo verificar el nombre del item");
        }

        int totalCost = listing.getPrice() * dto.getQuantity();
        String currencyMessage;
        try {
            currencyMessage = currencyClient.deductCurrency(
                    dto.getUserId(),
                    new CurrencyFeignDto(listing.getCurrencyType(), totalCost)
            );
        } catch (Exception e) {
            System.err.println("🛑 ERROR FEIGN MS-CURRENCY: " + e.getMessage());
            throw new RuntimeException("Transacción fallida: Fondos insuficientes");
        }

        try {
            inventoryClient.addItem(new InventoryFeignDto(
                    dto.getUserId(),
                    listing.getItemId(),
                    dto.getQuantity()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Error: No se pudo entregar el item al inventario.");
        }

        return String.format("Has obtenido %dx %s. %s",
                dto.getQuantity(),
                itemName,
                currencyMessage);
    }

    @Override
    public List<ShopCatalogResponseDto> getCatalog() {

        List<ShopItem> allListings = repository.findAll();

        return allListings.stream().map(listing -> {
            ShopCatalogResponseDto dto = new ShopCatalogResponseDto();

            dto.setShopItemId(listing.getId());
            dto.setItemId(listing.getItemId());
            dto.setPrice(listing.getPrice());
            dto.setCurrencyType(listing.getCurrencyType().name());

            return dto;
        }).toList();
    }
}