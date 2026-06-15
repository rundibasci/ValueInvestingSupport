package it.mazzoni.vis.admin;

import it.mazzoni.vis.marketdata.CacheEvictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/cache")
public class CacheAdminController {

    private final CacheEvictionService cacheEvictionService;

    public CacheAdminController(CacheEvictionService cacheEvictionService) {
        this.cacheEvictionService = cacheEvictionService;
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> evictSymbol(@PathVariable String symbol) {
        cacheEvictionService.evictSymbol(symbol);
        return ResponseEntity.noContent().build();
    }
}
