package com.okaynow.common.geo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-region")
@RequiredArgsConstructor
public class ServiceRegionController {

    private final ServiceRegionService serviceRegionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> current() {
        List<String> states = serviceRegionService.allowedStates();
        return ResponseEntity.ok(Map.of(
                "allowedStates", states,
                "defaultState", serviceRegionService.defaultState(),
                "label", states.size() == 1 && "MA".equals(states.getFirst())
                        ? "Massachusetts"
                        : String.join(", ", states)));
    }
}
