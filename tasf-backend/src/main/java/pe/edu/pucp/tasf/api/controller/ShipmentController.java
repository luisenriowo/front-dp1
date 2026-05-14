package pe.edu.pucp.tasf.api.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.tasf.api.dto.CreateShipmentRequest;
import pe.edu.pucp.tasf.api.dto.ShipmentResponse;
import pe.edu.pucp.tasf.service.ApiMapper;
import pe.edu.pucp.tasf.service.SimulationDataService;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final SimulationDataService simulationDataService;
    private final ApiMapper apiMapper;

    public ShipmentController(SimulationDataService simulationDataService, ApiMapper apiMapper) {
        this.simulationDataService = simulationDataService;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public List<ShipmentResponse> getShipments() {
        return simulationDataService.getShipments().stream()
            .map(apiMapper::toShipmentResponse)
            .toList();
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        var created = simulationDataService.createShipment(
            request.origin(),
            request.destination(),
            request.quantity(),
            request.deadline()
        );
        ShipmentResponse response = apiMapper.toShipmentResponse(created);
        return ResponseEntity.created(URI.create("/shipments/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ShipmentResponse getShipment(@PathVariable String id) {
        return apiMapper.toShipmentResponse(simulationDataService.getShipmentById(id));
    }
}
