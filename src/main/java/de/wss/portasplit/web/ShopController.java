package de.wss.portasplit.web;

import de.wss.portasplit.service.ShopService;
import de.wss.portasplit.web.dto.ShopDto;
import de.wss.portasplit.web.dto.ShopRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shops")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public List<ShopDto> list() {
        return shopService.findAll().stream().map(ShopDto::from).toList();
    }

    @GetMapping("/{id}")
    public ShopDto get(@PathVariable Long id) {
        return ShopDto.from(shopService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopDto create(@Valid @RequestBody ShopRequest request) {
        return ShopDto.from(shopService.create(request));
    }

    @PutMapping("/{id}")
    public ShopDto update(@PathVariable Long id, @Valid @RequestBody ShopRequest request) {
        return ShopDto.from(shopService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shopService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
